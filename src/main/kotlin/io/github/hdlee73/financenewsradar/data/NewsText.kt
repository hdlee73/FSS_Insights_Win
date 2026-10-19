package io.github.hdlee73.financenewsradar.data

object NewsText {
    private val bracketPrefix = Regex("^(\\[[^]]+]|【[^】]+】|\\([^)]*단독[^)]*\\))\\s*")
    private val spaces = Regex("\\s+")
    private val priorityTerms = listOf(
        "금융감독원", "금감원", "검사", "제재", "징계", "기관경고", "기관주의", "경영유의",
        "과징금", "과태료", "증권선물위원회", "불완전판매", "내부통제", "횡령", "배임"
    )

    fun normalizeTitle(title: String): String = title
        .replace(bracketPrefix, "")
        .replace(Regex("[^가-힣a-zA-Z0-9]"), "")
        .lowercase()

    fun matchingKeywords(title: String, summary: String, keywords: List<String>): List<String> {
        val text = "$title $summary"
        return keywords.filter { it.isNotBlank() && text.contains(it, ignoreCase = true) }
    }

    fun isPriority(title: String, summary: String): Boolean {
        val text = "$title $summary"
        return priorityTerms.count { text.contains(it, ignoreCase = true) } >= 2
    }

    fun compact(text: String): String = text.replace(spaces, " ").trim()
}
