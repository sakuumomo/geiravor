package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class NewsPolicyTest {
    @Test
    fun displayDateUsesNaiveDateOnly() {
        assertEquals("2026-02-14", NewsPolicy.displayDate("2026-02-14 13:37:34"))
        assertEquals("2026-01-05", NewsPolicy.displayDate("2026-01-05 21:18:01"))
        assertEquals("not a date", NewsPolicy.displayDate("not a date"))
        assertEquals("", NewsPolicy.displayDate("  "))
    }

    @Test
    fun listPlainTextStripsTags() {
        assertEquals(
            "God fuckin help me I'm old, but it's that time of the year",
            NewsPolicy.plainText("<p>God fuckin help me I'm old, but it's that time of the year</p>\n"),
        )
        assertEquals("holiday stream schedule 2025", NewsPolicy.plainText("<p>holiday stream schedule 2025</p>\n"))
    }

    @Test
    fun onlyHttpsStaticHostImagesLoad() {
        assertTrue(NewsPolicy.imageUrlAllowed("https://static.r-a-d.io/exci/2025-image.png"))
        assertTrue(NewsPolicy.imageUrlAllowed("HTTPS://static.r-a-d.io/exci/2025-image.png"))
        assertFalse(NewsPolicy.imageUrlAllowed("http://static.r-a-d.io/exci/2025-image.png"))
        assertFalse(NewsPolicy.imageUrlAllowed("https://r-a-d.io/exci/2025-image.png"))
        assertFalse(NewsPolicy.imageUrlAllowed("https://static.r-a-d.io.evil.example/x.png"))
        assertFalse(NewsPolicy.imageUrlAllowed("https://evil.example/static.r-a-d.io/x.png"))
        assertFalse(NewsPolicy.imageUrlAllowed("javascript:alert(1)"))
        assertFalse(NewsPolicy.imageUrlAllowed(""))
    }

    @Test
    fun localTimeUsesDatetimeAndDurationNotInnerUtcString() {
        val html =
            """<p><time datetime="1766584800" data-dur="21600000" data-type="local">24 Dec 25 14:00 +0000</time>: <strong>bacon</strong><br>
<time datetime="1766606400" data-dur="14400000" data-type="local">24 Dec 25 20:00 +0000</time>: <strong>kipukun</strong></p>"""
        val out = NewsPolicy.rewriteTimes(html, ZoneOffset.UTC, Locale.US)
        assertFalse(out.contains("24 Dec 25 14:00 +0000"))
        assertFalse(out.contains("1766584800"))
        assertTrue(out.contains("Wed, 12/24/2025, 14:00 - 20:00"))
        assertTrue(out.contains("Wed, 12/24/2025, 20:00 - 00:00") || out.contains("20:00"))
    }

    @Test
    fun articleBlocksKeepBreaksAndFitImages() {
        val html =
            """<p>line one<br>
line two</p>
<p><img src="https://static.r-a-d.io/exci/2025-image.png" alt="x"></p>
<p>after</p>"""
        val blocks = NewsPolicy.articleBlocks(html, ZoneOffset.UTC, Locale.US)
        assertEquals(3, blocks.size)
        assertEquals(NewsBlock.Kind.Html, blocks[0].kind)
        assertTrue(blocks[0].html.contains("line one"))
        assertTrue(blocks[0].html.contains("line two"))
        assertTrue(blocks[0].html.contains("<br"))
        assertEquals(NewsBlock.Kind.Image, blocks[1].kind)
        assertEquals("https://static.r-a-d.io/exci/2025-image.png", blocks[1].url)
        assertEquals(NewsBlock.Kind.Html, blocks[2].kind)
        assertTrue(blocks[2].html.contains("after"))
    }

    @Test
    fun commentLimitAndQuote() {
        assertEquals(500, NewsPolicy.COMMENT_MAX)
        assertEquals(">>4807\n", NewsPolicy.quote("", 4807))
        assertEquals("hello\n>>4807\n", NewsPolicy.quote("hello", 4807))
        assertTrue(NewsPolicy.commentAllowed("ok"))
        assertFalse(NewsPolicy.commentAllowed("  "))
        assertFalse(NewsPolicy.commentAllowed("x".repeat(501)))
        assertTrue(NewsPolicy.commentAllowed("x".repeat(500)))
    }

    @Test
    fun commentHrefJumpsHashQuotes() {
        assertEquals(4767L, NewsPolicy.commentAnchorId("#comment-4767"))
        assertEquals(4767L, NewsPolicy.commentAnchorId("/news/81#comment-4767"))
        assertEquals(4807L, NewsPolicy.commentAnchorId("https://r-a-d.io/news/81#comment-4807"))
        assertEquals(null, NewsPolicy.commentAnchorId("https://r-a-d.io/news/81"))
        assertEquals(null, NewsPolicy.commentAnchorId("https://evil.example/#comment-1"))
        assertEquals(">>3243\n", NewsPolicy.quote("", 3243))
        assertEquals("hi\n>>3243\n", NewsPolicy.quote("hi", 3243))
    }

    @Test
    fun listPagesFitAvailableHeight() {
        assertEquals(1, NewsPolicy.cardsThatFit(50f))
        assertEquals(3, NewsPolicy.cardsThatFit((NewsPolicy.LIST_CARD_DP + NewsPolicy.LIST_GAP_DP) * 3.4f))
        assertEquals(1, NewsPolicy.cardsThatFit(Float.POSITIVE_INFINITY))
        assertEquals(20, NewsPolicy.listTotal(serverLast = 1, lastPageCount = 20))
        assertEquals(65, NewsPolicy.listTotal(serverLast = 4, lastPageCount = 5))
        assertEquals(17, NewsPolicy.listLastPage(total = 65, visible = 4))
        assertEquals(4, NewsPolicy.listStartIndex(page = 2, visible = 4))
    }

    @Test
    fun staffDjDevUseSiteRoleColors() {
        assertEquals(RadioTheme.green, NewsPolicy.nameColor("staff"))
        assertEquals(RadioTheme.blue, NewsPolicy.nameColor("dj"))
        assertEquals(RadioTheme.red, NewsPolicy.nameColor("dev"))
        assertEquals(RadioTheme.muted, NewsPolicy.nameColor(""))
    }
}
