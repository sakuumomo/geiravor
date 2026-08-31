package io.r_a_d.geiravor.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uniffi.geiravor_core.NewsArticle
import uniffi.geiravor_core.NewsAuthor
import uniffi.geiravor_core.NewsComment

class NewsStoreTest {
    @Before
    fun resetRam() {
        NewsStore.clearForTests()
    }

    @Test
    fun catalogRowsStopAtTheFirstMissingHtmlPage() {
        val one = article(id = 1, header = "a", text = "")
        val two = article(id = 2, header = "b", text = "")
        NewsStore.putHtmlPage(1, 4, listOf(one, two), dropLater = false)
        assertEquals(listOf(1L, 2L), NewsStore.snapshot().rows().map { it.id })
    }

    @Test
    fun catalogRowsFlattenHtmlPages() {
        val one = article(id = 1, header = "a", text = "")
        val two = article(id = 2, header = "b", text = "")
        val three = article(id = 3, header = "c", text = "<p>body</p>")
        NewsStore.putHtmlPage(1, 2, listOf(one, two), dropLater = false)
        NewsStore.putHtmlPage(2, 2, listOf(three), dropLater = false)
        assertEquals(listOf(1L, 2L, 3L), NewsStore.snapshot().rows().map { it.id })
        NewsStore.rememberOpened(three, emptyList())
        assertEquals("<p>body</p>", NewsStore.opened(3)!!.first.text)
        NewsStore.putHtmlPage(1, 2, listOf(one, two), dropLater = true)
        assertEquals(listOf(1L, 2L), NewsStore.snapshot().rows().map { it.id })
        assertEquals("<p>body</p>", NewsStore.opened(3)!!.first.text)
    }

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

    @Test
    fun pageWriteSkipsWhenMergedPayloadMatchesDisk() {
        val stored = article(id = 81, header = "flavor", text = "<p>body</p>")
        val existing = NewsStore.fromArticle(stored)
        val page = NewsPageEntity(page = 1, lastPage = 4, ids = "81")
        val incoming = article(id = 81, header = "flavor", text = "")
        assertEquals(
            null,
            NewsStore.pageWrite(
                existingPage = page,
                existingById = mapOf(81L to existing),
                page = 1,
                lastPage = 4,
                articles = listOf(incoming),
            ),
        )
    }

    @Test
    fun pageWriteWhenHeaderChanges() {
        val stored = article(id = 81, header = "old", text = "<p>body</p>")
        val existing = NewsStore.fromArticle(stored)
        val page = NewsPageEntity(page = 1, lastPage = 4, ids = "81")
        val incoming = article(id = 81, header = "new", text = "")
        val write = NewsStore.pageWrite(
            existingPage = page,
            existingById = mapOf(81L to existing),
            page = 1,
            lastPage = 4,
            articles = listOf(incoming),
        )
        assertEquals("new", write!!.second.single().header)
        assertEquals("<p>body</p>", write.second.single().text)
    }

    @Test
    fun articleWriteSkipsWhenBodyAndCommentsMatch() {
        val stored = article(id = 81, header = "flavor", text = "<p>body</p>")
        val existing = NewsStore.fromArticle(stored)
        val comments = listOf(
            NewsComment(
                id = 1,
                author = "anon",
                postedAt = "2026-01-01 00:00:00 UTC",
                body = "hi",
                role = "",
            ),
        )
        val existingComments = NewsStore.fromComments(81, comments)
        assertEquals(
            null,
            NewsStore.articleWrite(existing, existingComments, stored, comments),
        )
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
