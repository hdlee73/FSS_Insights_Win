package io.github.hdlee73.financenewsradar

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.activity.compose.BackDispatcher
import io.github.hdlee73.financenewsradar.data.BriefingAlert
import io.github.hdlee73.financenewsradar.data.KeywordAlerts
import io.github.hdlee73.financenewsradar.data.UpdateChecker
import io.github.hdlee73.financenewsradar.platform.Notifications
import io.github.hdlee73.financenewsradar.ui.FinanceNewsRadarApp
import io.github.hdlee73.financenewsradar.ui.theme.FinanceNewsRadarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Windows 데스크톱 앱의 시작점(안드로이드 MainActivity 대역). */
fun main() {
    val app = Context.app
    Notifications.init()

    runCatching { openBrowserPresence() }

    // 앱을 열 때: 업데이트 확인, 알림 예약 복구, 백그라운드 확인 예약.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching { UpdateChecker.check(app) }
        runCatching {
            KeywordAlerts.reschedule(app)
            BriefingAlert.ensureScheduled(app)
        }
        runCatching { UpdateChecker.scheduleBackgroundCheck(app) }
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "FSS Insights",
            state = rememberWindowState(width = 480.dp, height = 920.dp),
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) BackDispatcher.dispatch() else false
            }
        ) {
            window.minimumSize = java.awt.Dimension(380, 620)
            LaunchedEffect(Unit) { }
            FinanceNewsRadarTheme { FinanceNewsRadarApp() }
        }
        with(ArticleReaderActivity) { ArticleReaderHost() }
    }
}

private fun openBrowserPresence() {
    // 헤드리스 렌더링에 쓸 브라우저를 미리 찾아 둔다(첫 사용 때 지연을 줄인다).
    io.github.hdlee73.financenewsradar.platform.EdgeHeadless.executable
}
