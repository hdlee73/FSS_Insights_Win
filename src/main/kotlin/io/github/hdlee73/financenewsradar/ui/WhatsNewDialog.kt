package io.github.hdlee73.financenewsradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.hdlee73.financenewsradar.BuildConfig
import io.github.hdlee73.financenewsradar.data.SettingsStore

/** 이번 버전에서 바뀐 내용만 담는다. 버전을 올릴 때마다 이 목록을 새로 쓴다. */
private val WHATS_NEW = listOf(
    "스마트 법령검색 메뉴가 추가됐습니다. 법조문을 입력하면 위임한 시행령·시행규칙·감독규정·시행세칙을 한 화면에 보여 주고, 비교표 보기와 PDF 저장을 지원합니다.",
    "금융시장 동향 KOSPI·KOSDAQ 칸과 오늘의 브리핑에 투자자별 순매수(개인·외국인·기관계)가 표시됩니다.",
    "보도자료·리포트·유권해석·비조치의견서·금감원/금융위 행정지도 목록에 핵심 내용 요약이 표시됩니다.",
    "Windows on ARM(ARM64)용 설치 파일이 함께 제공됩니다. 새 버전 확인은 기기 종류에 맞는 설치 파일을 고릅니다.",
    "안드로이드 앱 v2.0.2까지의 수집 보완과 화면 개선을 모두 반영했습니다."
)

/** 업데이트를 설치하고 처음 열 때 한 번만 띄운다. 처음 설치한 경우에는 띄우지 않는다. */
@Composable
fun WhatsNewDialog() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    var show by remember {
        val current = BuildConfig.VERSION_NAME
        val last = store.lastSeenVersion()
        val upgraded = last != current && store.disclaimerAccepted()
        if (last != current) store.setLastSeenVersion(current)
        mutableStateOf(upgraded)
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("v${BuildConfig.VERSION_NAME} 업데이트 내용") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WHATS_NEW.forEach { Text("• $it", modifier = Modifier.padding(bottom = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { show = false }) { Text("확인") } }
    )
}
