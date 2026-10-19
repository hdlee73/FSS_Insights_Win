package android.view

/** 안드로이드 View의 최소 대역. */
open class View {
    val context: android.content.Context? = null
    val isAttachedToWindow: Boolean = false
    fun post(action: Runnable) { action.run() }
    fun postDelayed(action: Runnable, delayMillis: Long) { action.run() }
    fun setOnClickListener(listener: Any?) {}
}

/** 안드로이드 Activity의 최소 대역. */
class Activity private constructor() {
    val window: Any? = null
    fun finish() {}
    fun runOnUiThread(action: Runnable) { action.run() }
}
