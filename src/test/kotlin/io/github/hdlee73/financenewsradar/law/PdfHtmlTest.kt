package io.github.hdlee73.financenewsradar.law

import com.hdlee73.everylaw.core.data.repository.SampleLawRepository
import com.hdlee73.everylaw.core.domain.DelegationProgress
import com.hdlee73.everylaw.core.domain.GetDelegationTreeUseCase
import com.hdlee73.everylaw.core.model.LawLevel
import com.hdlee73.everylaw.core.model.PdfExtras
import io.github.hdlee73.financenewsradar.law.tree.PdfExporter
import io.github.hdlee73.financenewsradar.law.tree.PdfOptions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfHtmlTest {
    private fun tree() = runBlocking {
        GetDelegationTreeUseCase(SampleLawRepository(latencyMs = 0))("자본시장법 제12조").toList()
            .filterIsInstance<DelegationProgress.Done>().first().tree
    }

    @Test fun compareHtmlHasColumnsAndAnchors() {
        val html = PdfExporter.buildHtml(tree(), PdfOptions(compare = true, levels = LawLevel.entries.toSet()), PdfExtras())
        assertTrue(html.contains("<table class=\"cmp"))
        assertTrue(html.contains("i class=\"anchor\""))
        File(System.getProperty("java.io.tmpdir"), "law-compare-sample.html").writeText(html)
    }

    @Test fun listHtmlHasSource() {
        val html = PdfExporter.buildHtml(tree(), PdfOptions(), PdfExtras())
        assertTrue(html.contains("국가법령정보센터"))
        File(System.getProperty("java.io.tmpdir"), "law-list-sample.html").writeText(html)
    }
}
