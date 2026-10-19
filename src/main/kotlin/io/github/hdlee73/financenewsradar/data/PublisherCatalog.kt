package io.github.hdlee73.financenewsradar.data

import java.net.URI

object PublisherCatalog {
    // 종합지·방송·통신·경제지를 고르게 포함한 앱 내 기준 목록입니다.
    val major30 = listOf(
        "연합뉴스", "뉴시스", "뉴스1", "KBS", "MBC", "SBS", "YTN", "JTBC", "TV조선", "MBN", "채널A",
        "조선일보", "중앙일보", "동아일보", "한겨레", "경향신문", "한국일보", "서울신문", "세계일보", "국민일보", "문화일보",
        "매일경제", "한국경제", "서울경제", "머니투데이", "이데일리", "아시아경제", "헤럴드경제", "파이낸셜뉴스", "디지털타임스"
    )

    private val aliases = mapOf(
        "yna" to "연합뉴스", "연합 뉴스" to "연합뉴스",
        "newsis" to "뉴시스", "news1" to "뉴스1",
        "kbs" to "KBS", "mbc" to "MBC", "sbs" to "SBS", "ytn" to "YTN",
        "jtbc" to "JTBC", "tv조선" to "TV조선", "tv chosun" to "TV조선", "mbn" to "MBN", "채널a" to "채널A",
        "chosun" to "조선일보", "joongang" to "중앙일보", "donga" to "동아일보",
        "hani" to "한겨레", "khan" to "경향신문", "hankookilbo" to "한국일보",
        "seoul.co.kr" to "서울신문", "segye" to "세계일보", "kmib" to "국민일보", "munhwa" to "문화일보",
        "mk.co.kr" to "매일경제", "매경" to "매일경제", "hankyung" to "한국경제",
        "sedaily" to "서울경제", "mt.co.kr" to "머니투데이", "머니s" to "머니투데이",
        "edaily" to "이데일리", "asiae" to "아시아경제", "heraldcorp" to "헤럴드경제",
        "fnnews" to "파이낸셜뉴스", "dt.co.kr" to "디지털타임스"
    )

    fun canonicalName(source: String, link: String = ""): String {
        val raw = source.trim().removeSuffix("언론사 선정")
        major30.firstOrNull { raw.equals(it, ignoreCase = true) }?.let { return it }
        val host = runCatching { URI(link).host.orEmpty() }.getOrDefault("")
        val haystack = "$raw $host".lowercase()
        aliases.entries.firstOrNull { haystack.contains(it.key.lowercase()) }?.let { return it.value }
        return raw.ifBlank { host.removePrefix("www.").ifBlank { "출처 미상" } }
    }

    fun isMajor(source: String, link: String = ""): Boolean = canonicalName(source, link) in major30
}
