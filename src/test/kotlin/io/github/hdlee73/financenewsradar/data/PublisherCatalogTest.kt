package io.github.hdlee73.financenewsradar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublisherCatalogTest {
    @Test
    fun `maps original domains to canonical publisher names`() {
        assertEquals("연합뉴스", PublisherCatalog.canonicalName("", "https://www.yna.co.kr/view/AKR123"))
        assertEquals("이데일리", PublisherCatalog.canonicalName("", "https://www.edaily.co.kr/News/Read"))
    }

    @Test
    fun `major filter keeps known sources and rejects unknown source`() {
        assertTrue(PublisherCatalog.isMajor("한국경제", "https://www.hankyung.com/article/1"))
        assertFalse(PublisherCatalog.isMajor("지역금융신문", "https://local.example.com/news/1"))
    }
}
