package io.github.hdlee73.financenewsradar.data

import android.content.Context
import android.content.Desktop
import io.github.hdlee73.financenewsradar.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 새 버전 설치 파일(.msi)을 GitHub 릴리스에서 내려받아 설치 화면을 띄운다. 최종 설치 확인은 Windows가 직접 묻는다. */
object UpdateInstaller {
    private const val DIR = "app_updates"

    /** Windows에서는 설치 제한이 없다. */
    fun canInstall(context: Context): Boolean = true

    /** 데스크톱에서는 따로 허용할 설정이 없다. */
    fun openInstallPermissionSettings(context: Context) = Unit

    /** 설치 파일을 내려받아 캐시에 저장한다. [onProgress]는 0..1(크기를 모르면 -1). 실패하면 예외. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: error("릴리스에 설치 파일이 없습니다.")
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val extension = if (url.endsWith(".exe", ignoreCase = true)) "exe" else "msi"
        val out = File(dir, "fss-insights-windows-v${info.version}.$extension")
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "FSSInsights/${BuildConfig.VERSION_NAME}")
        try {
            if (c.responseCode != 200) error("내려받기 실패 (HTTP ${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
            }
            if (total > 0 && done != total) error("내려받은 파일 크기가 맞지 않습니다.")
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            c.disconnect()
        }
        out
    }

    /** 내려받은 설치 파일의 설치 화면(Windows Installer)을 연다. */
    fun install(context: Context, file: File) {
        val command = when {
            file.extension.equals("msi", ignoreCase = true) -> listOf("msiexec.exe", "/i", file.absolutePath)
            file.extension.equals("exe", ignoreCase = true) -> listOf(file.absolutePath)
            else -> listOf("cmd", "/c", "start", "", file.absolutePath)
        }
        runCatching { ProcessBuilder(command).start() }
            .onFailure { Desktop.openFile(file) }
    }
}
