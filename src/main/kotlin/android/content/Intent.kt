package android.content

import android.net.Uri

/**
 * 안드로이드 Intent의 데스크톱 대역(안드로이드 앱 소스 호환용).
 * 웹 열기·메일·파일 열기·텍스트 복사만 실제 동작으로 처리한다.
 */
open class Intent {
    var action: String? = null
    var data: Uri? = null
    var type: String? = null
    var clipData: ClipData? = null
    private val extras = LinkedHashMap<String, Any?>()

    constructor()
    constructor(action: String?) { this.action = action }
    constructor(action: String?, data: Uri?) { this.action = action; this.data = data }
    constructor(context: Context?, cls: Class<*>?) {}

    fun addFlags(flags: Int): Intent = this
    fun setData(uri: Uri?): Intent { data = uri; return this }
    fun setType(t: String?): Intent { type = t; return this }
    fun setDataAndType(uri: Uri?, t: String?): Intent { data = uri; type = t; return this }
    fun putExtra(name: String, value: Any?): Intent { extras[name] = value; return this }
    fun putExtras(other: Intent): Intent { extras.putAll(other.extras); return this }
    fun getStringExtra(name: String): String? = extras[name] as? String
    fun hasExtra(name: String): Boolean = extras.containsKey(name)

    /** 데스크톱에서 할 수 있는 동작으로 옮긴다. */
    fun dispatch() {
        when (action) {
            ACTION_SEND -> {
                val stream = extras[EXTRA_STREAM] as? Uri
                val file = stream?.file
                when {
                    file != null && file.exists() -> Desktop.openFile(file)
                    else -> {
                        val text = (extras[EXTRA_TEXT] as? String).orEmpty()
                        if (text.isNotBlank()) Desktop.copyToClipboard(text)
                    }
                }
                return
            }
        }
        val uri = data ?: return
        when (uri.scheme?.lowercase()) {
            "http", "https", "mailto" -> Desktop.browse(uri.toString())
            "file" -> uri.file?.let { Desktop.openFile(it) }
            else -> Desktop.browse(uri.toString())
        }
    }

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_ACTIVITY_SINGLE_TOP = 0x20000000
        const val FLAG_GRANT_READ_URI_PERMISSION = 0x00000001

        @JvmStatic
        fun createChooser(target: Intent, title: CharSequence?): Intent = target
    }
}
