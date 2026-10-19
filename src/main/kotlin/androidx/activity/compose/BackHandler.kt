package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * 안드로이드 뒤로가기의 데스크톱 대역. 창의 Esc 키가 가장 나중에 등록된 처리기를 부른다.
 */
object BackDispatcher {
    private val handlers = ArrayDeque<() -> Unit>()

    fun register(handler: () -> Unit) { handlers.addLast(handler) }
    fun unregister(handler: () -> Unit) { handlers.remove(handler) }

    /** Esc 키를 눌렀을 때 가장 위(나중에 등록된) 처리기를 실행한다. 처리했으면 true. */
    fun dispatch(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    DisposableEffect(enabled, onBack) {
        if (!enabled) return@DisposableEffect onDispose { }
        val handler = { onBack() }
        BackDispatcher.register(handler)
        onDispose { BackDispatcher.unregister(handler) }
    }
}
