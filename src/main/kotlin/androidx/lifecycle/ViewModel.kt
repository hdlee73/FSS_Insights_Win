package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

/** 안드로이드 ViewModel의 데스크톱 대역(스마트 법령검색 화면이 그대로 쓰도록). */
abstract class ViewModel {
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    protected open fun onCleared() {}
    internal fun clear() { onCleared(); scope.cancel() }
}

val ViewModel.viewModelScope: CoroutineScope get() = scope

object ViewModelProvider {
    interface Factory {
        fun <T : ViewModel> create(modelClass: Class<T>): T
    }
}

/** 화면을 오가도 같은 인스턴스를 유지하는 저장소(안드로이드의 액티비티 범위 대역). */
object ViewModelStore {
    private val items = ConcurrentHashMap<Class<*>, ViewModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T : ViewModel> get(clazz: Class<T>, factory: ViewModelProvider.Factory): T =
        items.getOrPut(clazz) { factory.create(clazz) } as T
}
