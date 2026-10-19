package io.github.hdlee73.financenewsradar.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 안드로이드 ViewModel의 데스크톱 대역. 화면 상태와 백그라운드 작업 범위만 제공한다.
 * (안드로이드의 androidx.lifecycle.ViewModel 대신 앱 안에서 정의해 쓴다)
 */
open class AppViewModel {
    val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    open fun onCleared() {
        viewModelScope.cancel()
    }
}
