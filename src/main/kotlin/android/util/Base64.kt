package android.util

/** 안드로이드 Base64의 데스크톱 대역. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String =
        if (flags and NO_WRAP != 0) java.util.Base64.getEncoder().encodeToString(input)
        else java.util.Base64.getMimeEncoder().encodeToString(input)

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray =
        java.util.Base64.getMimeDecoder().decode(str)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).toByteArray()
}
