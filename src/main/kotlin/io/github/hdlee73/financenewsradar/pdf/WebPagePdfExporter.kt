package io.github.hdlee73.financenewsradar.pdf

import io.github.hdlee73.financenewsradar.platform.EdgeHeadless
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 안드로이드 WebView 인쇄의 데스크톱 대역.
 * 헤드리스 Edge로 페이지 전체를 A4 PDF로 저장한다.
 */
class WebPagePdfExporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun export(url: String, destination: File, title: String, onResult: (Result<File>) -> Unit) {
        if (job?.isActive == true) {
            onResult(Result.failure(IllegalStateException("PDF 생성이 이미 진행 중입니다.")))
            return
        }
        job = scope.launch {
            val result = runCatching {
                EdgeHeadless.printToPdf(url, destination)
            }.onFailure {
                runCatching { destination.delete() }
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
}
