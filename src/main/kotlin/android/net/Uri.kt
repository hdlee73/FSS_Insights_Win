package android.net

import java.io.File

/** 안드로이드 Uri의 데스크톱 대역(문자열 기반, file:// 이면 파일로도 변환). */
class Uri private constructor(private val raw: String) {

    val scheme: String? get() = parsed()?.scheme ?: raw.substringBefore(':', "").takeIf { it.isNotBlank() && !it.contains('/') && !it.contains('.') }

    val host: String?
        get() = parsed()?.host ?: run {
            val withoutScheme = raw.substringAfter("://", raw)
            withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfter('@').substringBefore(':')
                .takeIf { it.isNotBlank() && it.contains('.') }
        }

    val path: String?
        get() = parsed()?.path ?: raw.substringAfter("://", "").substringAfter('/', "").let { "/$it" }.substringBefore('?')

    val lastPathSegment: String?
        get() = path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    val query: String? get() = raw.substringAfter('?', "").takeIf { it.isNotBlank() }

    val file: File?
        get() = when {
            raw.startsWith("file://") -> runCatching { File(java.net.URI(raw)) }.getOrNull()
            scheme == null && (raw.startsWith("/") || Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(raw)) -> File(raw)
            else -> null
        }

    override fun toString(): String = raw

    override fun equals(other: Any?): Boolean = other is Uri && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()

    private fun parsed(): java.net.URI? = runCatching { java.net.URI(raw) }.getOrNull()

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)

        @JvmStatic
        fun fromFile(file: File): Uri = Uri(file.toURI().toString())
    }
}
