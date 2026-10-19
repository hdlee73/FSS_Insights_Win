package android.widget

import android.content.Context

/** 안드로이드 Toast의 데스크톱 대역(트레이 풍선 알림으로 보여 준다). */
class Toast private constructor(private val message: CharSequence?) {
    fun show() {
        val text = message?.toString().orEmpty()
        if (text.isNotBlank()) {
            io.github.hdlee73.financenewsradar.platform.Notifications.notify("FSS Insights", text)
        }
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast = Toast(text)
    }
}
