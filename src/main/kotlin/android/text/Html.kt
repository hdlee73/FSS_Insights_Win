package android.text

import org.jsoup.Jsoup

/**
 * 안드로이드 Html의 데스크톱 대역. HTML 태그를 걷어낸 평문을 돌려준다(jsoup 사용).
 */
object Html {
    const val FROM_HTML_MODE_LEGACY = 0
    const val FROM_HTML_MODE_COMPACT = 63

    @JvmStatic
    fun fromHtml(source: String?): Spanned = fromHtml(source, FROM_HTML_MODE_LEGACY)

    @JvmStatic
    fun fromHtml(source: String?, flags: Int): Spanned = Spanned(source.orEmpty())
}

/** 안드로이드 Spanned의 최소 대역(평문 변환만 지원). */
class Spanned(private val raw: String) {
    override fun toString(): String = runCatching {
        Jsoup.parse(raw).text()
    }.getOrDefault(raw)
}
