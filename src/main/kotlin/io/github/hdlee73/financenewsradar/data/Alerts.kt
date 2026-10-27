package io.github.hdlee73.financenewsradar.data

import android.content.Context
import io.github.hdlee73.financenewsradar.model.AgencyGroup
import io.github.hdlee73.financenewsradar.model.AgencyId
import io.github.hdlee73.financenewsradar.model.TimeRange
import io.github.hdlee73.financenewsradar.platform.Notifications
import io.github.hdlee73.financenewsradar.platform.Scheduler
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 앱이 보내는 알림의 공통 표시 부분(트레이 풍선 알림). */
internal object AlertNotifier {
    private const val MAX_LINES = 5

    fun post(context: Context, channelId: String, channelName: String, id: Int, title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        val shown = lines.take(MAX_LINES).toMutableList()
        if (lines.size > MAX_LINES) shown += "외 ${lines.size - MAX_LINES}건"
        Notifications.notifyLines(title, shown)
    }
}

/** 보도자료·연구자료가 올라오면 알리는 기능. 기관별로 켜고 끈다. */
object NewMaterialAlerts {
    /** 알림을 고를 수 있는 기관(직접 추가한 연구소는 [AgencyId.CUSTOM] 하나로 묶는다). */
    val sources: List<AgencyId> = AgencyId.entries

    fun anyEnabled(store: SettingsStore): Boolean = sources.any(store::materialAlertEnabled)

    /** 켜진 기관이 하나라도 있으면 1시간마다 확인한다(키워드 알림의 1시간 작업이 함께 처리). */
    internal suspend fun check(context: Context) {
        val store = SettingsStore(context)
        if (UpdateChecker.needsNotificationPermission(context)) return
        val enabled = sources.filter(store::materialAlertEnabled)
        if (enabled.isEmpty()) return
        val agencies = AgencyRepository(context)
        for (group in AgencyGroup.entries) {
            val fresh = mutableListOf<AlertHit>()
            for (agency in enabled.filter { it.group == group }) {
                if (agency == AgencyId.CUSTOM || agency == AgencyId.CUSTOM_PRESS) {
                    for (institute in store.loadInstitutes().filter { it.group == group }) {
                        val hits = runCatching { agencies.latestCustom(institute, 20) }.getOrNull()
                            ?.map { AlertHit(it.title, it.link, institute.name) }
                        fresh += diffAndRecord(store, "material_custom_${institute.url}", hits)
                    }
                } else {
                    val hits = runCatching { agencies.latest(agency, 20) }.getOrNull()
                        ?.map { AlertHit(it.title, it.link, agency.shortLabel) }
                    fresh += diffAndRecord(store, "material_${agency.name}", hits)
                }
            }
            if (fresh.isNotEmpty()) {
                val noun = if (group == AgencyGroup.PRESS) "보도자료" else "금융리포트"
                AlertNotifier.post(
                    context, "material_alerts", "새 자료 알림", 3001 + group.ordinal,
                    "새 $noun ${fresh.size}건", fresh.map { "[${it.source}] ${it.title}" }
                )
            }
        }
    }

    /** 처음 보는 기관이면 지금 목록을 '이미 본 것'으로만 기록(알리지 않음). */
    private fun diffAndRecord(store: SettingsStore, key: String, hits: List<AlertHit>?): List<AlertHit> {
        val (fresh, seen) = KeywordAlertLogic.diff(hits, store.seenLinks(key))
        if (seen != null) store.saveSeenLinks(key, seen)
        return fresh
    }
}

/** 참고자료(구글 드라이브 자료실)에 새 파일이 올라오면 알리는 기능. 1시간 작업에 함께 실려 확인한다. */
object LibraryAlerts {
    private const val SEEN_KEY = "library_files"
    private const val MAX_FOLDERS = 30

    internal suspend fun check(context: Context) {
        val store = SettingsStore(context)
        if (!store.libraryAlertEnabled() || UpdateChecker.needsNotificationPermission(context)) return
        val api = LibraryApi(context)
        if (!api.isConfigured) return
        val hits = runCatching { allFiles(api) }.getOrNull()
        val (fresh, seen) = KeywordAlertLogic.diff(hits, store.seenLinks(SEEN_KEY))
        if (seen != null) store.saveSeenLinks(SEEN_KEY, seen, 2000)
        if (fresh.isNotEmpty()) {
            AlertNotifier.post(
                context, "library_alerts", "참고 자료실 알림", 3003,
                "새 참고 자료실 자료 ${fresh.size}건", fresh.map { if (it.source.isBlank()) it.title else "[${it.source}] ${it.title}" }
            )
        }
    }

    /** 자료실 최상위부터 하위 폴더까지 둘러보며 파일을 모은다(폴더 수 제한). 링크 자리에는 파일 ID를 쓴다. */
    private suspend fun allFiles(api: LibraryApi): List<AlertHit> {
        val queue = ArrayDeque<Pair<String?, String>>().apply { add(null to "") }
        val files = mutableListOf<AlertHit>()
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_FOLDERS) {
            val (id, name) = queue.removeFirst()
            visited++
            for (entry in api.list(id).items) {
                if (entry.isFolder) queue.add(entry.id to entry.name) else files += AlertHit(entry.name, entry.id, name)
            }
        }
        return files
    }
}

/** 매일 정한 시각에 오늘의 브리핑(시장·최근 기사·최근 보도자료)을 요약해 알린다. */
object BriefingAlert {
    private const val WORK_NAME = "briefing_alert"
    private val SEOUL = ZoneId.of("Asia/Seoul")
    private val dateFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

    /** 지금부터 다음 [hour]:[minute](서울 시각)까지 기다릴 시간. 이미 지났으면 내일. */
    internal fun initialDelay(now: ZonedDateTime, hour: Int, minute: Int): Duration {
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }

    fun formatTime(minutesOfDay: Int): String = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

    /** 켜져 있으면 다음 알림 시각까지 기다렸다 하루 한 번 도는 작업을 등록하고, 꺼져 있으면 취소한다. */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val store = SettingsStore(app)
        if (!store.briefingAlertEnabled()) {
            Scheduler.cancel(WORK_NAME)
            return
        }
        val minutes = store.briefingAlertMinutes()
        val delay = initialDelay(ZonedDateTime.now(SEOUL), minutes / 60, minutes % 60)
        // 시각을 바꾼 경우에도 새 시각부터 다시 세도록 기존 예약을 지우고 다시 등록한다.
        Scheduler.scheduleDaily(WORK_NAME, delay.toMillis()) { check(app) }
    }

    /** 앱을 열 때: 예약이 살아 있게만 한다(이미 있으면 그대로 두어 시각이 밀리지 않게). */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val store = SettingsStore(app)
        if (!store.briefingAlertEnabled()) return
        if (!Scheduler.isScheduled(WORK_NAME)) reschedule(app)
    }

    internal suspend fun check(context: Context) {
        val store = SettingsStore(context)
        if (!store.briefingAlertEnabled() || UpdateChecker.needsNotificationPermission(context)) return
        val today = LocalDate.now(SEOUL)
        val lines = mutableListOf<String>()

        runCatching {
            val market = MarketClient(context)
            val parts = listOf("^KS11" to "코스피", "^KQ11" to "코스닥").mapNotNull { (symbol, name) ->
                runCatching { market.quote(symbol) }.getOrNull()?.let { q ->
                    val pct = q.changePercent
                    if (pct == null) "$name ${"%,.2f".format(q.price)}"
                    else "$name ${"%,.2f".format(q.price)} (${if (pct >= 0) "▲" else "▼"}${"%.2f".format(kotlin.math.abs(pct))}%)"
                }
            }
            if (parts.isNotEmpty()) lines += parts.joinToString(" · ")
            // 투자자별 순매수(억원): "코스피 개인 +1,794 외국인 -7,035 기관 +4,832"
            val flows = listOf("^KS11" to "코스피", "^KQ11" to "코스닥").mapNotNull { (symbol, name) ->
                runCatching { market.investors(symbol) }.getOrNull()?.let { f ->
                    fun s(v: Double) = (if (v > 0) "+" else "") + "%,.0f".format(v)
                    "$name 개인 ${s(f.personal)} 외국인 ${s(f.foreign)} 기관 ${s(f.institutional)}"
                }
            }
            if (flows.isNotEmpty()) lines += "순매수(억원) " + flows.joinToString(" · ")
        }

        runCatching {
            val settings = store.loadSettings().copy(timeRange = TimeRange.DAY)
            val articles = NewsRepository().home(settings, store.loadCredentials()).articles
                .filter { PublisherCatalog.isMajor(it.source, it.link) }
                .sortedByDescending { it.publishedAt }
                .take(3)
            articles.forEach { lines += "[기사] ${it.title}" }
        }

        runCatching {
            val repo = AgencyRepository(context)
            val recent = listOf(AgencyId.FSS, AgencyId.FSC).flatMap { agency ->
                runCatching { repo.latest(agency, 10) }.getOrDefault(emptyList())
            }.filter { item -> item.date?.let { !it.isBefore(today.minusDays(1)) } == true }
                .sortedByDescending { it.date }
                .take(2)
            recent.forEach { lines += "[${it.agency.shortLabel}] ${it.title}" }
        }

        if (lines.isEmpty()) return
        AlertNotifier.post(context, "briefing_alert", "오늘의 브리핑", 3000, "오늘의 브리핑 · ${today.format(dateFormat)}", lines)
    }
}
