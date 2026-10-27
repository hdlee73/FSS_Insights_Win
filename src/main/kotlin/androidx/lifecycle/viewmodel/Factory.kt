package androidx.lifecycle.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class InitializerViewModelFactoryBuilder {
    @PublishedApi internal val initializers = LinkedHashMap<Class<*>, () -> ViewModel>()

}

inline fun <reified T : ViewModel> InitializerViewModelFactoryBuilder.initializer(noinline create: () -> T) {
    initializers[T::class.java] = create
}

fun viewModelFactory(builder: InitializerViewModelFactoryBuilder.() -> Unit): ViewModelProvider.Factory {
    val inits = InitializerViewModelFactoryBuilder().apply(builder).initializers
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            (inits[modelClass] ?: error("ViewModel 초기화 방법이 없습니다: ${modelClass.name}"))() as T
    }
}
