package io.r_a_d.geiravor.data

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.geiravor_core.NewsArticle
import uniffi.geiravor_core.NewsAuthor

class NewsStoreTest {
    @Test
    fun listSaveKeepsExistingArticleBody() {
        val existing = NewsStore.fromArticle(
            article(id = 81, header = "old flavor", text = "<p>full body</p>"),
        )
        val incoming = article(id = 81, header = "new flavor", text = "")
        val merged = NewsStore.mergeListRow(existing, incoming)
        assertEquals("<p>full body</p>", merged.text)
        assertEquals("new flavor", merged.header)
        assertEquals("Title", merged.title)
    }

    @Test
    fun listSaveDoesNotInventABody() {
        val incoming = article(id = 81, header = "flavor", text = "")
        val merged = NewsStore.mergeListRow(existing = null, incoming)
        assertEquals("", merged.text)
        assertEquals("flavor", merged.header)
    }

    private fun article(id: Long, header: String, text: String) = NewsArticle(
        id = id,
        title = "Title",
        header = header,
        text = text,
        updatedAt = "2026-01-01",
        author = NewsAuthor(id = 1, user = "exci", role = "staff"),
    )
}
