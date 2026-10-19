package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 안드로이드 Compose의 LocalContext 데스크톱 대역. 앱 컨텍스트 한 개를 그대로 제공한다.
 */
val LocalContext = staticCompositionLocalOf<android.content.Context> { android.content.Context.app }

/**
 * 안드로이드 Compose의 LocalView 데스크톱 대역(항상 빈 View).
 */
val LocalView = staticCompositionLocalOf { android.view.View() }
