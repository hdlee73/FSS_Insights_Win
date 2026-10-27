package io.github.hdlee73.financenewsradar.law.tree

import android.content.Context
import io.github.hdlee73.financenewsradar.BuildConfig
import com.hdlee73.everylaw.core.domain.CompareTable
import com.hdlee73.everylaw.core.domain.ItemAttachment
import com.hdlee73.everylaw.core.model.ArticleItem
import com.hdlee73.everylaw.core.model.DelegationTree
import com.hdlee73.everylaw.core.model.ItemPath
import com.hdlee73.everylaw.core.model.PdfExtras
import com.hdlee73.everylaw.core.model.LawLevel
import com.hdlee73.everylaw.core.model.LawNode
import com.hdlee73.everylaw.core.model.formatYmd
import io.github.hdlee73.financenewsradar.platform.EdgeHeadless
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 검색 결과(조문·하위 규정 트리·판례)를 PDF로 만든다.
 * 윈도우에서는 결과를 HTML로 그린 뒤 설치된 Edge(또는 Chrome)의 인쇄 기능으로 PDF로 저장하므로
 * 시스템 한글 글꼴(맑은 고딕)로 깨짐 없이 출력된다.
 */
/** PDF에 담을 범위. 법률 조문은 항상 들어가고, 나머지는 고른 것만 담는다. */
data class PdfOptions(
    val levels: Set<LawLevel> = LawLevel.entries.toSet(),
    val precedentList: Boolean = true,
    val precedentBodies: Boolean = false,
    val relatedArticles: Boolean = false,
    /** true 면 고른 단계(levels)를 열로 나란히 놓는 비교표(가로), false 면 현행 방식(위에서 아래로 이어 쓰기) */
    val compare: Boolean = false,
    val paper: PdfPaper = PdfPaper.A4,
    /** 저장 형식: PDF 또는 한글(HWPX). 담기는 내용과 서식은 같다. */
    val format: SaveFormat = SaveFormat.PDF,
)

enum class SaveFormat(val label: String, val ext: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    HWPX("한글(HWPX)", "hwpx", "application/hwp+zip"),
}

/** 용지 크기(1/72 인치, 세로 기준). 비교표는 가로로 돌려 쓴다. B4는 ISO 250x353mm. */
enum class PdfPaper(val label: String, val w: Int, val h: Int, val mmW: Int, val mmH: Int) {
    A4("A4", 595, 842, 210, 297),
    B4("B4", 709, 1001, 250, 353),
    A3("A3", 842, 1191, 297, 420),
}

object PdfExporter {

    internal val levelColor = mapOf(
        LawLevel.LAW to "#6750A4",
        LawLevel.DECREE to "#0B5CAD",
        LawLevel.RULE to "#0F6B50",
        LawLevel.SUPERVISORY_REGULATION to "#9A4400",
        LawLevel.ENFORCEMENT_DETAIL to "#9C2452",
    )

    private const val MARGIN_MM = 14
    private const val PURPLE = "#4F378B"

    fun fileName(tree: DelegationTree, ext: String = "pdf"): String {
        val base = "${tree.law.abbreviation ?: tree.law.name}_${tree.query.target}"
        val asOf = tree.asOf?.let { "_${it}기준" }.orEmpty()
        return (base + asOf).replace(Regex("""[\\/:*?"<>|\s]"""), "_") + "." + ext
    }

    /** 고른 단계에 없는 노드는 빼고, 그 아래 노드는 한 단계 위로 올린다. */
    private fun keepLevels(children: List<LawNode>, levels: Set<LawLevel>): List<LawNode> =
        children.flatMap { c ->
            val kept = keepLevels(c.children, levels)
            if (c.level in levels) listOf(c.copy(children = kept)) else kept
        }

    internal fun esc(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** 줄 단위 글 한 덩어리를 문단 요소로. */
    private class Html(val sb: StringBuilder = StringBuilder()) {
        fun p(text: String, size: Double = 9.5, bold: Boolean = false, color: String? = null, indent: Double = 0.0, gapAfter: Double = 1.0) {
            if (text.isBlank()) return
            val style = buildString {
                append("font-size:${size}pt;")
                if (bold) append("font-weight:700;")
                if (color != null) append("color:$color;")
                if (indent > 0) append("margin-left:${indent}pt;")
                append("margin-bottom:${gapAfter + 1}pt;")
            }
            sb.append("<div class=\"t\" style=\"$style\">").append(esc(text)).append("</div>\n")
        }

        fun gap(pt: Double) {
            sb.append("<div style=\"height:${pt}pt\"></div>\n")
        }
    }

    /** 문서 전체 HTML. 비교표는 가로, 현행 방식은 세로. */
    internal fun buildHtml(tree: DelegationTree, options: PdfOptions, extras: PdfExtras): String {
        val compare = options.compare && CompareTable.columns(options.levels).size >= 2
        val paper = options.paper
        val pageW = if (compare) paper.mmH else paper.mmW
        val h = Html()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date())

        h.p("FSS Insights 위임 법령 조회 결과", 18.0, bold = true, color = PURPLE)
        h.p("${tree.law.name} ${tree.query.target}", 14.0, bold = true)
        h.p(
            buildString {
                append("기준: ")
                append(tree.version?.let { "${it.effectiveDateLabel} 시행 (${it.status ?: "연혁"})" } ?: "현행")
                append("   ·   출력: $now")
                if (tree.isSample) append("   ·   데모 데이터(실제 조문 아님)")
            },
            9.0, color = "#444"
        )
        val scope = (listOf("법률") + LawLevel.entries.filter { it != LawLevel.LAW && it in options.levels }.map { it.label } +
            listOfNotNull(
                if (options.precedentList) "판례 목록" else null,
                if (options.precedentBodies) "판례 본문" else null,
                if (options.relatedArticles) "인용 조문" else null,
            )).joinToString(", ")
        if (compare) {
            val cols = CompareTable.columns(options.levels)
            h.p("비교 방식: ${cols.size}단 비교 (${cols.joinToString("·") { it.label }}) · ${paper.label} 가로", 9.0, color = "#444")
        } else h.p("포함 범위: $scope", 9.0, color = "#444")
        tree.warnings.forEach { h.p("※ $it", 8.5, color = "#888") }
        h.gap(10.0)

        fun nodeBlock(n: LawNode, depth: Int) {
            val indent = depth * 14.0
            h.p("[${n.level.label}] ${n.sourceName}", 9.0, bold = true, color = levelColor.getValue(n.level), indent = indent)
            h.p(n.displayHeading, 11.0, bold = true, indent = indent)
            h.p(n.article.body, 9.5, indent = indent, gapAfter = 2.0)
            h.gap(6.0)
            n.children.forEach { nodeBlock(it, depth + 1) }
        }

        val root = tree.root
        if (compare) {
            compareTable(h, root, options.levels)
        } else {
            // 법률 조문: 항·호·목 줄 바로 아래에 그 위치가 위임한 하위 규정을 둔다.
            val children = keepLevels(root.children, options.levels)
            val attached = ItemAttachment.attach(root.article, children)
            h.p("[${root.level.label}] ${root.sourceName}", 9.0, bold = true, color = levelColor.getValue(root.level))
            h.p(root.article.heading, 11.0, bold = true)
            val parsed = root.article.parsed
            if (parsed.lead.isNotBlank()) h.p(parsed.lead, 9.5, gapAfter = 2.0)
            fun item(i: ArticleItem, depth: Int) {
                h.p(i.text, 9.5, indent = depth * 14.0, gapAfter = 2.0)
                attached[i.path]?.let { nodes ->
                    h.p("▼ ${i.path.display} 하위 규정", 8.5, bold = true, color = "#444", indent = depth * 14.0 + 8.0)
                    nodes.forEach { nodeBlock(it, depth + 1) }
                }
                i.children.forEach { item(it, depth + 1) }
            }
            if (parsed.items.isEmpty() && parsed.lead.isBlank()) h.p(root.article.content, 9.5, gapAfter = 2.0)
            attached[ItemPath.LEAD]?.let { nodes ->
                h.p("▼ 각 호 외의 부분 하위 규정", 8.5, bold = true, color = "#444", indent = 8.0)
                nodes.forEach { nodeBlock(it, 1) }
            }
            parsed.items.forEach { item(it, 0) }
            attached[ItemPath()]?.let { nodes ->
                h.gap(4.0)
                h.p("▼ 조 전체에 연결된 하위 규정", 8.5, bold = true, color = "#444", indent = 8.0)
                nodes.forEach { nodeBlock(it, 1) }
            }
        }

        val cited = keepLevels(tree.cited, options.levels)
        if (cited.isNotEmpty()) {
            h.gap(8.0)
            h.p("인용된 법규 (${cited.sumOf { 1 + it.descendantCount }}건)", 13.0, bold = true, color = PURPLE)
            h.p("이 조문을 인용했지만 위임받은 규정은 아닙니다.", 9.0, color = "#444")
            cited.forEach { n ->
                nodeBlock(n, 0)
                if (n.delegatedBy.isNotEmpty()) h.p("위임 근거: " + n.delegatedBy.joinToString(", ") { "${root.sourceName} ${it.heading}" }, 8.5, color = "#444")
            }
        }

        if (options.relatedArticles) {
            h.gap(8.0)
            h.p("인용 조문 (${extras.relatedArticles.size}건)", 13.0, bold = true, color = PURPLE)
            if (extras.relatedArticles.isEmpty()) h.p("본문이 인용한 같은 법의 다른 조문이 없거나 불러오지 못했습니다.", 9.5, color = "#888")
            extras.relatedArticles.forEach { a ->
                h.p("${tree.law.name} ${a.heading}", 10.5, bold = true)
                h.p(a.body, 9.5, indent = 10.0, gapAfter = 2.0)
                h.gap(6.0)
            }
        }

        if (options.precedentList) {
            h.gap(8.0)
            h.p("관련 판례 (${tree.precedents.size}건)", 13.0, bold = true, color = PURPLE)
            if (tree.precedents.isEmpty()) h.p("찾은 판례가 없습니다.", 9.5, color = "#888")
            tree.precedents.forEach { p ->
                h.p("• ${p.caseName}", 10.0, bold = true)
                h.p(listOfNotNull(p.court, p.caseNumber, p.decisionDate?.let(::formatYmd)).joinToString(" · "), 9.0, color = "#444", indent = 10.0)
                h.gap(3.0)
            }
        }

        if (options.precedentBodies) {
            h.gap(8.0)
            h.p("판례 본문 (${extras.precedents.size}건)", 13.0, bold = true, color = PURPLE)
            if (extras.precedents.isEmpty()) h.p("불러온 판례 본문이 없습니다.", 9.5, color = "#888")
            extras.precedents.forEach { d ->
                h.p(d.summary.caseName, 10.5, bold = true)
                h.p(listOfNotNull(d.summary.court, d.summary.caseNumber, d.summary.decisionDate?.let(::formatYmd)).joinToString(" · "), 9.0, color = "#444", indent = 10.0)
                listOf("판시사항" to d.holdings, "판결요지" to d.gist, "참조조문" to d.referencedArticles, "참조판례" to d.referencedCases).forEach { (title, body) ->
                    if (body.isNotBlank()) {
                        h.p("[$title]", 9.0, bold = true, indent = 10.0)
                        h.p(body, 9.0, indent = 10.0, gapAfter = 2.0)
                    }
                }
                h.gap(8.0)
            }
        }

        h.gap(12.0)
        h.p(
            "출처: 국가법령정보센터 공동활용 API (law.go.kr). 법적 판단의 근거로는 반드시 원문을 확인하세요.\n" +
                "FSS Insights ${BuildConfig.VERSION_NAME} · 만든이 이현덕 (hdlee73@gmail.com)",
            8.0, color = "#888"
        )

        return buildString {
            append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><style>\n")
            append("@page { size: ${paper.label} ${if (compare) "landscape" else "portrait"}; margin: ${MARGIN_MM}mm; }\n")
            append("html, body { margin:0; padding:0; }\n")
            // 화면(JS 측정)과 인쇄의 줄바꿈이 같도록 본문 너비를 용지에서 여백을 뺀 값으로 고정한다.
            append("body { width:${pageW - MARGIN_MM * 2}mm; font-family:'Malgun Gothic','맑은 고딕','Noto Sans CJK KR','Noto Sans KR',sans-serif; color:#000; }\n")
            append(".t { white-space:pre-wrap; word-break:keep-all; overflow-wrap:anywhere; line-height:1.5; }\n")
            append(COMPARE_CSS)
            append("</style></head><body>\n")
            append(h.sb)
            append(COMPARE_SCRIPT)
            append("</body></html>")
        }
    }

    private const val COMPARE_CSS = """
table.cmp { border-collapse:collapse; width:100%; table-layout:fixed; }
table.cmp thead { display:table-header-group; }
table.cmp th { color:#fff; text-align:center; font-size:9.5pt; padding:3pt 4pt; font-weight:700; }
table.cmp td { border:0.5pt solid #888; vertical-align:top; padding:4pt; font-size:9pt; line-height:18px; white-space:pre-wrap; overflow-wrap:anywhere; }
table.cmp.five td { font-size:8.5pt; }
table.cmp td b { font-weight:700; }
table.cmp td .src { color:#888; display:block; }
table.cmp td .gap { display:block; height:18px; }
i.anchor { display:inline; font-style:normal; }
"""

    /** 하위 규정 칸은 법률 칸에서 위임받은 항·호가 시작하는 줄과 같은 줄에서 시작하도록, 그 줄 앞의 높이만큼 위 여백을 준다. */
    private const val COMPARE_SCRIPT = """<script>
window.addEventListener('load', function () {
  document.querySelectorAll('table.cmp tr.row').forEach(function (tr) {
    var law = tr.cells[0];
    var m = law.querySelector('i.anchor');
    if (!m) return;
    var lh = 18;
    var cs = getComputedStyle(law);
    var top = law.getBoundingClientRect().top + parseFloat(cs.paddingTop) + parseFloat(cs.borderTopWidth);
    var lines = Math.max(0, Math.floor((m.getBoundingClientRect().top - top + 1) / lh));
    for (var i = 1; i < tr.cells.length; i++) {
      var c = tr.cells[i];
      if (lines > 0 && c.textContent.trim().length > 0) c.style.paddingTop = (parseFloat(getComputedStyle(c).paddingTop) + lines * lh) + 'px';
    }
  });
});
</script>
"""

    /** 3단·4단·5단 비교표: 법률 조문의 위임 위치마다 한 줄, 단계마다 한 열. */
    private fun compareTable(h: Html, root: LawNode, chosen: Set<LawLevel>) {
        val levels = CompareTable.columns(chosen)
        val rows = CompareTable.build(root, chosen)
        val sb = h.sb
        sb.append("<table class=\"cmp${if (levels.size > 3) " five" else ""}\"><thead><tr>")
        levels.forEach { lv -> sb.append("<th style=\"background:${levelColor.getValue(lv)};width:${100.0 / levels.size}%\">").append(esc(lv.label)).append("</th>") }
        sb.append("</tr></thead><tbody>\n")
        rows.forEach { row ->
            sb.append("<tr class=\"row\"><td>")
            val text = row.lawText
            val at = row.anchorStart.coerceIn(0, text.length)
            sb.append(esc(text.substring(0, at))).append("<i class=\"anchor\"></i>").append(esc(text.substring(at)))
            sb.append("</td>")
            levels.drop(1).forEach { lv ->
                sb.append("<td>")
                row.cells[lv].orEmpty().forEachIndexed { i, n ->
                    if (i > 0) sb.append("<span class=\"gap\"></span>")
                    sb.append("<b>").append(esc(n.displayHeading.ifBlank { n.sourceName })).append("</b>")
                    // 감독규정·시행세칙은 여러 문서가 섞이므로 문서 이름을 밝힌다
                    if (lv.ordinal >= LawLevel.SUPERVISORY_REGULATION.ordinal) sb.append("\n<span class=\"src\">").append(esc(n.sourceName)).append("</span>")
                    else sb.append("\n")
                    sb.append(esc(n.article.body))
                }
                sb.append("</td>")
            }
            sb.append("</tr>\n")
        }
        sb.append("</tbody></table>\n")
        h.gap(8.0)
    }

    /** HTML을 Edge(또는 Chrome)로 인쇄해 PDF 바이트를 [out]에 쓴다. */
    suspend fun write(
        tree: DelegationTree,
        options: PdfOptions,
        extras: PdfExtras,
        out: OutputStream,
    ) = withContext(Dispatchers.IO) {
        if (!EdgeHeadless.isAvailable) error("PDF를 만들려면 Microsoft Edge 또는 Chrome이 필요해요")
        val dir = File(System.getProperty("java.io.tmpdir"), "fss-insights-law-${System.nanoTime()}").apply { mkdirs() }
        try {
            val html = File(dir, "law.html").apply { writeText(buildHtml(tree, options, extras), Charsets.UTF_8) }
            val pdf = EdgeHeadless.printToPdf(html.toURI().toString(), File(dir, "law.pdf"), virtualTimeMs = 3_000)
            pdf.inputStream().use { it.copyTo(out) }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 한글(HWPX) 문서로 쓴다. 현행/비교표 선택과 포함 단계는 PDF와 같다. */
    suspend fun writeHwpx(tree: DelegationTree, options: PdfOptions, extras: PdfExtras, out: OutputStream) = withContext(Dispatchers.IO) {
        com.hdlee73.everylaw.core.domain.HwpxExporter.write(
            tree,
            com.hdlee73.everylaw.core.domain.HwpxOptions(
                compare = options.compare, levels = options.levels,
                paperLabel = options.paper.label, paperWidthPt = options.paper.w, paperHeightPt = options.paper.h,
                precedentList = options.precedentList, precedentBodies = options.precedentBodies,
                relatedArticles = options.relatedArticles, extras = extras,
                footer = "FSS Insights ${BuildConfig.VERSION_NAME} · 만든이 이현덕 (hdlee73@gmail.com)",
            ),
            out,
        )
    }

    /** 만든 문서를 임시 폴더에 저장한 뒤 기본 보기 프로그램으로 연다(공유는 거기서). */
    suspend fun share(context: Context, tree: DelegationTree, options: PdfOptions, extras: PdfExtras) {
        val dir = File(context.cacheDir, "law_pdf").apply { mkdirs() }
        val file = File(dir, fileName(tree, options.format.ext))
        file.outputStream().use { if (options.format == SaveFormat.HWPX) writeHwpx(tree, options, extras, it) else write(tree, options, extras, it) }
        android.content.Desktop.openFile(file)
    }
}
