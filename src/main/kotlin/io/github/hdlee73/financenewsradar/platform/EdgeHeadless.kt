package io.github.hdlee73.financenewsradar.platform

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 안드로이드 WebView(크롬 엔진)의 데스크톱 대역.
 * 설치된 Microsoft Edge(또는 Chrome)를 헤드리스로 실행해 렌더링된 HTML을 얻거나 PDF로 저장한다.
 */
object EdgeHeadless {

    /** 찾은 브라우저 실행 파일(없으면 null). */
    val executable: File? by lazy { findBrowser() }

    val isAvailable: Boolean get() = executable != null

    private fun findBrowser(): File? {
        val candidates = listOf(
            System.getenv("LOCALAPPDATA")?.let { "$it\\Microsoft\\Edge\\Application\\msedge.exe" },
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
            System.getenv("LOCALAPPDATA")?.let { "$it\\Google\\Chrome\\Application\\chrome.exe" },
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
        )
        candidates.filterNotNull().forEach { path ->
            val file = File(path)
            if (file.isFile) return file
        }
        for (name in listOf("msedge.exe", "chrome.exe")) {
            runCatching {
                val process = ProcessBuilder("where", name).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText().lineSequence().firstOrNull { it.isNotBlank() }
                process.waitFor()
                if (!output.isNullOrBlank() && File(output.trim()).isFile) return File(output.trim())
            }
        }
        return null
    }

    /** 자바스크립트까지 실행해 그려진 DOM(HTML)을 가져온다. 브라우저가 없으면 예외. */
    suspend fun dumpDom(url: String, timeoutMs: Long = 40_000, virtualTimeMs: Long = 6_000): String {
        val browser = executable ?: error("Microsoft Edge 또는 Chrome을 찾지 못했습니다.")
        return withContext(Dispatchers.IO) {
            val profile = tempProfile("dom")
            try {
                val command = listOf(
                    browser.absolutePath,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-extensions",
                    "--hide-scrollbars",
                    "--mute-audio",
                    "--user-data-dir=$profile",
                    "--virtual-time-budget=$virtualTimeMs",
                    "--dump-dom",
                    url
                )
                val result = runProcess(command, timeoutMs)
                if (result.first != 0 && result.second.isBlank()) error("브라우저가 페이지를 열지 못했습니다. (종료코드 ${result.first})")
                result.second
            } finally {
                deleteRecursively(profile)
            }
        }
    }

    /** 페이지를 PDF로 저장한다(A4, 머리글·바닥글 없음). 브라우저가 없으면 예외. */
    suspend fun printToPdf(url: String, destination: File, timeoutMs: Long = 60_000, virtualTimeMs: Long = 6_000): File {
        val browser = executable ?: error("Microsoft Edge 또는 Chrome을 찾지 못했습니다.")
        return withContext(Dispatchers.IO) {
            destination.parentFile?.mkdirs()
            if (destination.exists()) destination.delete()
            val profile = tempProfile("pdf")
            try {
                val command = listOf(
                    browser.absolutePath,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-extensions",
                    "--mute-audio",
                    "--no-pdf-header-footer",
                    "--user-data-dir=$profile",
                    "--virtual-time-budget=$virtualTimeMs",
                    "--print-to-pdf=${destination.absolutePath}",
                    url
                )
                val result = runProcess(command, timeoutMs)
                if (!destination.isFile || destination.length() == 0L) {
                    error("PDF를 만들지 못했습니다. (종료코드 ${result.first})")
                }
                destination
            } finally {
                deleteRecursively(profile)
            }
        }
    }

    /** 브라우저가 없을 때를 위한 일반 HTTP 내려받기(자바스크립트 없이). */
    suspend fun plainFetch(url: String, timeoutMs: Int = 20_000): String = withContext(Dispatchers.IO) {
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        )
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        try {
            val charset = runCatching {
                if (connection.contentType.orEmpty().lowercase().contains("euc-kr")) java.nio.charset.Charset.forName("EUC-KR") else Charsets.UTF_8
            }.getOrDefault(Charsets.UTF_8)
            connection.inputStream.use { it.readBytes().toString(charset) }
        } finally {
            connection.disconnect()
        }
    }

    private fun tempProfile(tag: String): File =
        File(System.getProperty("java.io.tmpdir"), "fss-insights-$tag-${System.nanoTime()}").apply { mkdirs() }

    private fun runProcess(command: List<String>, timeoutMs: Long): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                }
            }
        }
        reader.isDaemon = true
        reader.start()
        val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1 to output.toString()
        }
        reader.join(2_000)
        return process.exitValue() to output.toString()
    }

    private fun deleteRecursively(file: File) {
        runCatching {
            if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
            file.delete()
        }
    }

    /** 시간 제한 안에서 [block]을 실행하고 실패하면 null. */
    suspend fun <T> tryWith(timeoutMs: Long, block: suspend () -> T): T? =
        withTimeoutOrNull(timeoutMs) { runCatching { block() }.getOrNull() }
}
