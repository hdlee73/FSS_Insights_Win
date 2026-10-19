package io.github.hdlee73.financenewsradar.data

import android.content.Context
import io.github.hdlee73.financenewsradar.platform.EdgeHeadless

/**
 * 일반 HTTP 요청이 막히거나(403 등) 자바스크립트로 목록을 그리는 사이트를 위해,
 * 헤드리스 Edge(또는 Chrome)로 페이지를 열고 그려진 HTML을 가져온다.
 * 브라우저가 없으면 자바스크립트 없이 일반 HTTP로 내려받는다.
 */
internal object WebViewFetcher {
    private const val TOTAL_TIMEOUT_MS = 45_000L

    /** [moreLabel]은 안드로이드 WebView에서 '더보기'를 누르던 값이다. 데스크톱에서는 렌더링 시간을 늘려 대신한다. */
    suspend fun get(context: Context, url: String, moreLabel: String? = null, isReady: (String) -> Boolean = { true }): String {
        if (EdgeHeadless.isAvailable) {
            var budget = 6_000L
            var last = ""
            repeat(3) {
                val html = runCatching { EdgeHeadless.dumpDom(url, TOTAL_TIMEOUT_MS, budget) }.getOrNull()
                if (!html.isNullOrBlank()) {
                    last = html
                    if (html.length > 3_000 && isReady(html)) return html
                }
                budget += 6_000
            }
            if (last.isNotBlank()) return last
        }
        return EdgeHeadless.plainFetch(url)
    }
}
