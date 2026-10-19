package io.github.hdlee73.financenewsradar

import android.content.Context
import android.content.Desktop
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import io.github.hdlee73.financenewsradar.pdf.WebPagePdfExporter
import io.github.hdlee73.financenewsradar.platform.EdgeHeadless
import io.github.hdlee73.financenewsradar.platform.FileChooser
import io.github.hdlee73.financenewsradar.platform.Notifications
import io.github.hdlee73.financenewsradar.ui.theme.FinanceNewsRadarTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * 안드로이드 ArticleReaderActivity의 데스크톱 대역.
 * 기사 원문 창을 별도 창으로 띄우고, 원문 열기·링크 복사·PDF 저장을 제공한다.
 */
object ArticleReaderActivity {

    private data class Request(val id: Long, val url: String, val title: String)

    private val requests = mutableStateListOf<Request>()
    private var nextId = 1L

    /** 새 기사 창을 연다. */
    fun open(context: Context, url: String, title: String) {
        val parsed = Uri.parse(url)
        if (parsed.scheme !in listOf("http", "https")) {
            Notifications.notify("FSS Insights", "올바른 기사 링크가 없습니다.")
            return
        }
        if (requests.none { it.url == url }) {
            requests.add(Request(nextId++, url, title.ifBlank { "기사 원문" }))
        }
    }

    @Composable
    fun ApplicationScope.ArticleReaderHost() {
        requests.toList().forEach { request ->
            key(request.id) {
                Window(
                    onCloseRequest = { requests.remove(request) },
                    state = rememberWindowState(width = 760.dp, height = 900.dp),
                    title = request.title
                ) {
                    window.minimumSize = java.awt.Dimension(420, 480)
                    FinanceNewsRadarTheme {
                        ArticleReaderScreen(request.title, request.url) { requests.remove(request) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleReaderScreen(title: String, url: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val exporter = remember { WebPagePdfExporter() }
    var loading by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var heading by remember { mutableStateOf(title) }
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }

    suspend fun load() {
        loading = true
        pageError = null
        if (!EdgeHeadless.isAvailable) {
            pageError = "원문을 화면에 그리려면 Microsoft Edge가 필요합니다. 외부 열기를 이용해 주세요."
            loading = false
            return
        }
        val html = runCatching { EdgeHeadless.dumpDom(url) }.getOrNull()
        if (html.isNullOrBlank()) {
            pageError = "원문을 불러오지 못했습니다. 연결을 확인하거나 외부 열기를 이용해 주세요."
        } else {
            val parsed = withContext(Dispatchers.Default) { parseArticle(html, url) }
            heading = parsed.first.ifBlank { title }
            paragraphs = parsed.second
            if (paragraphs.isEmpty()) pageError = "본문을 찾지 못했습니다. 외부 열기를 이용해 주세요."
        }
        loading = false
    }

    LaunchedEffect(url, reloadTick) { load() }
    DisposableEffect(Unit) { onDispose { exporter.cancel() } }

    Scaffold(
        topBar = {
            Surface {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, enabled = !exporting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                    }
                    Text(heading, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { Desktop.browse(url) }, enabled = !exporting) {
                        Text("외부 열기", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        if (exporting) "PDF 처리 중…" else "기사 공유는 웹페이지 주소를, PDF 저장은 열린 원문 전체를 저장합니다. (PDF는 로그인·유료 제한, 광고가 포함될 수 있음)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            Desktop.copyToClipboard("$title\n$url")
                            Notifications.notify("FSS Insights", "기사 링크를 클립보드에 복사했습니다.")
                        }, enabled = !exporting, modifier = Modifier.weight(1f)) { Text("기사 링크 복사") }
                        Button(onClick = {
                            val file = FileChooser.save("PDF 저장", safeName(title) + ".pdf", "pdf") ?: return@Button
                            exporting = true
                            exporter.export(url, file, title) { result ->
                                exporting = false
                                result.onSuccess {
                                    Notifications.notify("FSS Insights", "PDF를 저장했습니다: ${it.absolutePath}")
                                }.onFailure {
                                    Notifications.notify("FSS Insights", "PDF 저장 실패: ${it.message}")
                                }
                            }
                        }, enabled = !loading && !exporting && EdgeHeadless.isAvailable, modifier = Modifier.weight(1f)) { Text("PDF 저장") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading || exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            pageError?.let { error ->
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { reloadTick++ }) { Text("재시도") }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(heading, style = MaterialTheme.typography.titleLarge)
                Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                paragraphs.forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** HTML에서 제목과 본문 문단을 뽑는다. */
private fun parseArticle(html: String, baseUrl: String): Pair<String, List<String>> {
    val document = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return "" to emptyList()
    document.select("script,style,noscript,svg,iframe").remove()
    val title = document.selectFirst("meta[property=og:title]")?.attr("content")
        ?.takeIf { it.isNotBlank() }
        ?: document.title()
    val container = document.selectFirst("article")
        ?: document.selectFirst("main")
        ?: document.selectFirst("[id*=article], [class*=article]")
        ?: document.body()
        ?: document
    val paragraphs = container.select("p, h1, h2, h3, h4, li, blockquote")
        .map { it.text().trim() }
        .filter { it.length > 1 }
        .distinct()
    val body = if (paragraphs.size >= 2) paragraphs else listOf(container.text().trim()).filter { it.isNotBlank() }
    return title.trim() to body
}

private fun safeName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(70).ifBlank { "기사" }
