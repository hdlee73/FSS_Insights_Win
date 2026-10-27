package io.github.hdlee73.financenewsradar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** 요약 작업의 결과: 성공하면 [text], 실패하면 사용자에게 보일 짧은 [reason]. */
internal class Outcome(val text: String? = null, val reason: String? = null)

/**
 * 본문에서 핵심 문장을 골라 요약하는 내장 추출 요약기(윈도우에는 Gemini Nano가 없어 AI 대신 쓴다).
 * 제목과 겹치는 낱말, 문서 앞쪽 위치, 결정·회신·시행 같은 핵심 표현으로 문장 점수를 매겨 높은 두 문장을 원래 순서대로 잇는다.
 * 모델이나 내려받기가 없어 가볍고, 어느 PC에서나 바로 동작한다.
 */
internal object SummaryEngine {
    private const val MIN_BODY_CHARS = 150
    private const val MAX_SUMMARY_CHARS = 230

    private val sentenceSplit = Regex("(?<=[.!?。])\\s+|\\n+|(?<=다)\\s{2,}")
    private val noise = Regex("담당부서|담당자|연락처|전화|문의|배포|배포일|보도시점|엠바고|사진|붙임|첨부|이메일|@|개인정보|저작권|무단 전재|Copyright|목록|이전글|다음글|조회수|바로가기|인쇄|공유하기|다운로드")
    private val cue = Regex("발표|추진|개선|시행|확대|강화|결정|의결|제재|과징금|과태료|조치|지도|요청|권고|계획|예정|발간|분석|전망|회신|판단|해당|가능|불가|인정|허용|제한|금지|필요")
    private val replyMarker = Regex("회신|답변|결론|의견")
    private val wordSplit = Regex("[\\s,.:;()\\[\\]\"'‘’“”/·-]+")

    /** 항상 쓸 수 있다. */
    suspend fun isAvailable(): Boolean = true

    suspend fun summarize(title: String, body: String, kind: String = "", focus: String = ""): Outcome = withContext(Dispatchers.Default) {
        if (body.length < MIN_BODY_CHARS) return@withContext Outcome(reason = "본문이 너무 짧음")
        val sentences = body.split(sentenceSplit).map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 25..260 && !noise.containsMatchIn(it) && it.count { c -> c in '가'..'힣' } * 2 >= it.length }
            .distinct()
        if (sentences.isEmpty()) return@withContext Outcome(reason = "요약할 문장을 찾지 못함")
        val titleWords = title.split(wordSplit).filter { it.length >= 2 }
        val wantsReply = focus.contains("회신")
        val replyAt = if (wantsReply) sentences.indexOfFirst { replyMarker.containsMatchIn(it) && it.length < 60 } else -1
        val scored = sentences.mapIndexed { i, s ->
            val overlap = titleWords.count { w -> s.contains(w.take(maxOf(2, w.length - 1))) }.toDouble() / maxOf(1, titleWords.size)
            val position = 1.0 / (1.0 + i * 0.12)
            val cues = minOf(3, cue.findAll(s).count()) * 0.12
            val reply = if (wantsReply && replyAt in 0 until i) 0.5 else 0.0
            val length = if (s.length in 40..160) 0.15 else 0.0
            i to (overlap * 1.2 + position * 0.8 + cues + reply + length)
        }
        val picked = scored.sortedByDescending { it.second }.take(2).sortedBy { it.first }.map { sentences[it.first] }
        var text = picked.joinToString(" ")
        if (text.length > MAX_SUMMARY_CHARS) text = picked.first().take(MAX_SUMMARY_CHARS)
        text = text.removePrefix("-").trim()
        if (text.length < 20) Outcome(reason = "요약할 문장을 찾지 못함") else Outcome(text = text)
    }
}

/** 요약문 저장소: 같은 자료를 다시 요약하지 않도록 링크별로 기기에 보관(최근 400건). */
internal class SummaryStore(context: Context, name: String = "summary_cache") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun load(): Map<String, String> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun save(all: Map<String, String>) {
        val json = JSONObject()
        all.entries.toList().takeLast(MAX).forEach { json.put(it.key, it.value) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "summaries"
        const val MAX = 400
    }
}

/** 자료 상세 페이지에서 본문 글만 뽑는다. 읽지 못하거나 본문이 없으면 null. */
internal object ArticleText {
    private val pdfLink = Regex("""(?i)\.pdf(\?|$)|common/downloadw?\?""")
    private val skipLink = Regex("""(?i)\.(hwp|hwpx|docx?|xlsx?|pptx?|zip)(\?|$)""")
    private val noise = Regex("(?is)<!--.*?-->|<(script|style|noscript|nav|header|footer|aside|form)\\b.*?</\\1>")
    private val main = Regex("(?is)<(article|main)\\b.*?</\\1>")
    private val tag = Regex("(?s)<[^>]+>")

    /** 본문 글. 제목과 관련 없는 글(엉뚱한 페이지가 열린 경우)이면 이유만 담아 돌려준다. */
    suspend fun fetch(context: Context, url: String, title: String): Outcome {
        if (skipLink.containsMatchIn(url)) return Outcome(reason = "문서 파일(HWP·오피스)은 읽을 수 없음")
        // 1차: 가벼운 HTTP. 막히거나 본문이 비면(자바스크립트로 그리는 사이트) 2차로 숨은 WebView로 읽는다.
        val html = runCatching { HtmlFetcher.get(url) }.getOrNull()
        val http = html?.let(::bodyText)
        if (http != null && relatedTo(title, http)) return Outcome(text = http)
        val rendered = runCatching { WebViewFetcher.get(context, url) }.getOrNull()
            ?: return Outcome(reason = if (html == null) "페이지를 읽지 못함" else "본문이 짧거나 없음")
        val body = bodyText(rendered) ?: return Outcome(reason = "본문이 짧거나 없음")
        return if (relatedTo(title, body)) Outcome(text = body) else Outcome(reason = "제목과 맞지 않는 페이지가 열림")
    }

    fun isPdf(url: String) = pdfLink.containsMatchIn(url)

    /** 제목의 낱말 중 40% 이상이 본문에 있어야 같은 자료로 본다(목록 페이지 등이 열려 모든 자료가 같은 요약이 되는 것을 막는다). */
    private fun relatedTo(title: String, body: String): Boolean {
        val words = title.split(Regex("[\\s,.:;()\\[\\]\"'‘’“”/-]+")).filter { it.length >= 2 }
        if (words.isEmpty()) return true
        val lower = body.lowercase()
        return words.count { lower.contains(it.lowercase()) } * 100 >= words.size * 40
    }

    private fun bodyText(html: String): String? {
        val cleaned = noise.replace(html, " ")
        val region = main.find(cleaned)?.value ?: cleaned
        val text = HtmlListParser.compact(HtmlListParser.decodeEntities(tag.replace(region, " ")))
        return text.takeIf { it.length >= 150 }
    }
}

/** PDF 링크를 내려받아 앞쪽 본문 글을 뽑는다(표지를 건너뛰고 2~5쪽). 실패하면 이유를 담는다. */
internal object PdfText {
    private const val MAX_BYTES = 10 * 1024 * 1024

    private fun download(url: String, file: File): String? {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36")
            connection.setRequestProperty("Accept", "application/pdf,*/*;q=0.8")
            connection.setRequestProperty("Referer", URI(url).let { "${it.scheme}://${it.host}/" })
            val code = connection.responseCode
            if (code !in 200..299) return "서버 응답 $code"
            // 너무 큰 PDF는 앞쪽 [MAX_BYTES]만 받는다(본문 글은 대개 파일 앞부분에 있다).
            connection.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (total < MAX_BYTES) {
                        val read = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total))
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun extract(context: Context, url: String): Outcome {
        // 요청이 너무 잦아 서버가 429로 제한하면 잠시 기다렸다가 다시 시도한다.
        var attempt = 0
        while (true) {
            val outcome = extractOnce(context, url)
            val limited = outcome.reason?.contains("429") == true
            if (!limited || attempt >= 3) return outcome
            attempt++
            kotlinx.coroutines.delay(4_000L * attempt)
        }
    }

    private suspend fun extractOnce(context: Context, url: String): Outcome = withContext(Dispatchers.IO) {
        val file = File.createTempFile("summary-source", ".pdf", context.cacheDir)
        try {
            val failure = runCatching { download(url, file) }.getOrElse { it.javaClass.simpleName }
            if (failure != null) return@withContext Outcome(reason = "PDF 내려받기 실패($failure)")
            if (file.length() == 0L) return@withContext Outcome(reason = "PDF 내려받기 실패(빈 파일)")
            try {
                Loader.loadPDF(file).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    val pages = doc.numberOfPages
                    // 첫 쪽은 표지인 경우가 많아, 쪽이 3장 이상이면 2쪽부터 읽는다.
                    stripper.startPage = if (pages >= 3) 2 else 1
                    stripper.endPage = minOf(pages, 5)
                    val text = HtmlListParser.compact(stripper.getText(doc))
                    if (text.length >= 150) Outcome(text = text) else Outcome(reason = "PDF에서 읽을 글이 없음(스캔본일 수 있음)")
                }
            } catch (e: Throwable) {
                Outcome(reason = "PDF 읽기 실패(${e.javaClass.simpleName}${e.message?.let { ": " + it.take(40) }.orEmpty()})")
            }
        } finally {
            file.delete()
        }
    }
}
