package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class NewsHtmlTest {
    @Test
    fun articleImageHtmlJoinsBodyAndComments() {
        assertEquals(
            "<img src=\"a\"><img src=\"b\">",
            articleImageHtml("<img src=\"a\">", listOf("<img src=\"b\">")),
        )
    }

    @Test
    fun splitsStaticImages() {
        val blocks = newsBlocks("""<p>Hi</p><img src="https://static.r-a-d.io/x.jpg"><p>Bye</p>""")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is NewsBlock.Text)
        assertEquals("https://static.r-a-d.io/x.jpg", (blocks[1] as NewsBlock.Image).url)
    }

    @Test
    fun commentHrefIsJumpId() {
        assertEquals(5276L, commentJumpId("#comment-5276"))
        assertEquals(null, commentJumpId("https://r-a-d.io/news/1"))
    }

    @Test
    fun quoteInsertsSpaceWhenDraftHasNoTrailingNewline() {
        assertEquals(">>12\n", quoteComment("", 12L))
        assertEquals(">>12\n>>13\n", quoteComment(">>12\n", 13L))
        assertEquals("hello >>12 ", quoteComment("hello", 12L))
        assertEquals("hello\n>>12\n", quoteComment("hello\n", 12L))
    }

    @Test
    fun newsFilmIsPartialUntilHover() {
        val opaque = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)
        val rest = newsFilmFill(false, opaque)
        assertTrue(rest.alpha > 0f && rest.alpha < opaque.alpha)
        assertEquals(opaque, newsFilmFill(true, opaque))
    }

    @Test
    fun commentJumpIndexSkipsHeaderItem() {
        assertEquals(1, commentListIndex(listOf(5279L, 5282L), 5279L))
        assertEquals(2, commentListIndex(listOf(5279L, 5282L), 5282L))
        assertEquals(null, commentListIndex(listOf(5279L), 1L))
    }

    @Test
    fun localTimeZeroDurIsNotUtcOrUnix() {
        val html =
            """<time datetime="1700000000" data-type="local" data-dur="0">2023-11-14 22:13:20 +0000</time>"""
        val out = rewriteLocalTimes(html, ZoneOffset.UTC, Locale.US)
        assertFalse(out.contains("+0000"))
        assertFalse(out.contains(">1700000000<") || out.trim() == "1700000000")
        assertTrue(out.contains("22:13"))
        assertTrue(out.contains("Tue"))
    }

    @Test
    fun localTimeRangeSameDayUsesClockOnlyEnd() {
        val html =
            """<time datetime="1700000000" data-type="local" data-dur="3600000">x</time>"""
        val out = rewriteLocalTimes(html, ZoneOffset.UTC, Locale.US)
        assertTrue(out.contains(" - 23:13"))
        assertFalse(out.substringAfter(" - ").contains("Tue"))
    }

    @Test
    fun localTimeRangeNextDayUsesFullEnd() {
        val html =
            """<time datetime="1700000000" data-type="local" data-dur="10800000">x</time>"""
        val out = rewriteLocalTimes(html, ZoneOffset.UTC, Locale.US)
        assertTrue(out.contains(" - "))
        assertTrue(out.substringAfter(" - ").contains("Wed") || out.substringAfter(" - ").contains("11/15"))
    }
}
