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

/** 안드로이드 Configuration 대역: 창 너비(dp)만 제공한다. */
class Configuration(val screenWidthDp: Int)

/** 안드로이드 LocalConfiguration 대역. 창의 현재 너비를 dp로 돌려준다. */
object LocalConfiguration {
    val current: Configuration
        @androidx.compose.runtime.Composable
        get() {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widthPx = LocalWindowInfo.current.containerSize.width
            return Configuration(with(density) { widthPx.toDp().value.toInt() })
        }
}
