package io.github.hdlee73.financenewsradar.data

import android.content.Context
import io.github.hdlee73.financenewsradar.BuildConfig
import io.github.hdlee73.financenewsradar.platform.Notifications
import io.github.hdlee73.financenewsradar.platform.Scheduler
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** GitHub 최신 릴리스 정보. */
data class UpdateInfo(
    val version: String,
    val url: String,
    /** 릴리스 본문(이번 버전 변경 내용). */
    val notes: String = "",
    /** 릴리스에 첨부된 설치 파일(.msi) 내려받기 주소(없으면 null). */
    val apkUrl: String? = null,
    val apkSize: Long = 0L
)

/** 업데이트 확인 진행 상태. */
enum class CheckState { IDLE, CHECKING, LATEST, AVAILABLE, FAILED }

/** 마지막 확인 결과. 앱 정보 화면이 구독한다. */
object UpdateStatus {
    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available
    private val _state = MutableStateFlow(CheckState.IDLE)
    val state: StateFlow<CheckState> = _state
    internal fun set(info: UpdateInfo?) { _available.value = info }
    internal fun setState(s: CheckState) { _state.value = s }
}

object UpdateChecker {
    const val RELEASES_URL = "https://github.com/hdlee73/FSS_Insights_Win/releases"
    private const val LATEST_API = "https://api.github.com/repos/hdlee73/FSS_Insights_Win/releases/latest"
    private const val WORK_NAME = "update_check"
    private const val PREFS = "update_checker"
    private const val KEY_NOTIFIED = "notified_version"

    /** Windows 설치 파일 확장자(내려받아 설치할 파일). */
    private val INSTALL_EXTENSIONS = listOf(".msi", ".exe")

    /** ARM 기기(Windows on ARM)이면 arm64용 설치 파일을, 아니면 x64용을 고른다. */
    private val isArm: Boolean = System.getProperty("os.arch").orEmpty().lowercase().let { "aarch64" in it || "arm" in it }

    /** "v0.12.1" / "0.12.1-debug" 같은 문자열을 숫자 목록으로. 해석 불가면 null. */
    internal fun parseVersion(raw: String): List<Int>? {
        val core = raw.trim().removePrefix("v").removePrefix("V").takeWhile { it.isDigit() || it == '.' }
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }

    /** latest가 current보다 높으면 true. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 릴리스 본문에서 "## vX.Y.Z 변경 내용" 구역만 뽑는다. 없으면 본문 전체. */
    internal fun extractChanges(body: String): String {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.startsWith("## ") && it.contains("변경 내용") }
        if (start < 0) return body.trim()
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return lines.subList(start + 1, end).joinToString("\n").trim()
    }

    private suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URI(LATEST_API).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "FSSInsights/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode != 200) return@withContext null
            val json = JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            val tag = json.optString("tag_name")
            if (tag.isBlank()) return@withContext null
            val assets = json.optJSONArray("assets")
            var assetUrl: String? = null
            var assetSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    if (INSTALL_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } && name.contains("arm64", ignoreCase = true) == isArm) {
                        assetUrl = a.optString("browser_download_url").ifBlank { null }
                        assetSize = a.optLong("size")
                        break
                    }
                }
            }
            UpdateInfo(
                tag.removePrefix("v"),
                json.optString("html_url").ifBlank { RELEASES_URL },
                extractChanges(json.optString("body")),
                assetUrl,
                assetSize
            )
        } finally {
            c.disconnect()
        }
    }

    /** 앱 시작 시 한 번 호출. 실패(오프라인 등)는 조용히 무시한다. */
    suspend fun check(context: Context) {
        UpdateStatus.setState(CheckState.CHECKING)
        val latest = runCatching { fetchLatest() }.getOrNull()
        if (latest == null) {
            UpdateStatus.setState(CheckState.FAILED)
            return
        }
        if (!isNewer(latest.version, BuildConfig.VERSION_NAME)) {
            UpdateStatus.set(null)
            UpdateStatus.setState(CheckState.LATEST)
            return
        }
        UpdateStatus.set(latest)
        UpdateStatus.setState(CheckState.AVAILABLE)
        notifyOnce(context.applicationContext, latest)
    }

    /** 앱을 열지 않아도 새 버전이 나오면 알림이 울리도록 12시간마다 확인한다. 이미 예약돼 있으면 그대로 둔다. */
    fun scheduleBackgroundCheck(context: Context) {
        val app = context.applicationContext
        if (Scheduler.isScheduled(WORK_NAME)) return
        Scheduler.schedulePeriodic(WORK_NAME, periodMs = 12L * 60 * 60 * 1000) { checkQuietly(app) }
    }

    /** 백그라운드 확인: 새 버전이면 알림만 보낸다(화면 상태는 건드리지 않는다). */
    internal suspend fun checkQuietly(context: Context) {
        val latest = runCatching { fetchLatest() }.getOrNull() ?: return
        if (isNewer(latest.version, BuildConfig.VERSION_NAME)) notifyOnce(context.applicationContext, latest)
    }

    /** 데스크톱에서는 알림 권한을 따로 받지 않는다. */
    fun needsNotificationPermission(context: Context): Boolean = false

    /** 같은 버전에는 한 번만 알린다(트레이 풍선 알림). */
    private fun notifyOnce(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED, null) == info.version) return
        Notifications.notify("FSS Insights 새 버전 ${info.version}", "앱 정보에서 업데이트를 내려받아 설치할 수 있습니다.")
        prefs.edit().putString(KEY_NOTIFIED, info.version).apply()
    }
}
