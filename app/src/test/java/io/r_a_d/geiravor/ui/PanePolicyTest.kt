package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanePolicyTest {
    @Test
    fun newsCardsFitAvailableHeight() {
        val slot = (PanePolicy.NEWS_CARD_DP + PanePolicy.NEWS_GAP_DP).toFloat()
        assertEquals(1, PanePolicy.newsThatFit(50f))
        assertEquals(3, PanePolicy.newsThatFit(slot * 3.4f))
        assertEquals(1, PanePolicy.newsThatFit(Float.POSITIVE_INFINITY))
        assertEquals(4, PanePolicy.thatFitSlot(640f, 160f))
    }

    @Test
    fun phoneLandscapeUsesPortraitEquivalentHeight() {
        assertEquals(700f, PanePolicy.normalListDp(700f, screenWidthDp = 360, screenHeightDp = 800, twoPane = false))
        assertEquals(
            360f + (800f - 360f),
            PanePolicy.normalListDp(360f, screenWidthDp = 800, screenHeightDp = 360, twoPane = false),
        )
        assertEquals(400f, PanePolicy.normalListDp(400f, screenWidthDp = 900, screenHeightDp = 600, twoPane = true))
    }

    @Test
    fun freezeKeepsTheFirstPositiveCount() {
        assertEquals(6, PanePolicy.freezeVisible(0, 6))
        assertEquals(6, PanePolicy.freezeVisible(6, 3))
        assertEquals(1, PanePolicy.freezeVisible(0, 0))
    }

    @Test
    fun catalogSlicesEvenlyAndLeftoverIsOnlyTheTrueLastPage() {
        val rows = (1..65).toList()
        assertEquals(11, PanePolicy.lastPage(65, 6))
        assertEquals((1..6).toList(), PanePolicy.slice(rows, 1, 6))
        assertEquals((7..12).toList(), PanePolicy.slice(rows, 2, 6))
        assertEquals((61..65).toList(), PanePolicy.slice(rows, 11, 6))
        assertEquals(emptyList<Int>(), PanePolicy.slice(rows, 12, 6))
        assertEquals(1, PanePolicy.lastPage(0, 6))
    }

    @Test
    fun searchWindowStitchesServerPagesToFillThePane() {
        val pages = mapOf(
            1 to (0 until 20).toList(),
            2 to (20 until 40).toList(),
        )
        assertEquals((18..23).toList(), PanePolicy.window(pages, start = 18, count = 6, perServer = 20))
        assertEquals((0..5).toList(), PanePolicy.window(pages, start = 0, count = 6, perServer = 20))
        assertNull(PanePolicy.window(pages, start = 40, count = 6, perServer = 20))
        assertEquals(1..2, PanePolicy.serverPages(start = 18, count = 6, perServer = 20, serverLast = 4))
        assertEquals(1..1, PanePolicy.serverPages(start = 0, count = 6, perServer = 20, serverLast = 4))
    }

    @Test
    fun favesCatalogSizeUsesLeftoverNotAFullLastPage() {
        assertEquals(100, PanePolicy.favesCatalogSize(serverLast = 1, lastPageCount = 100))
        assertEquals(37, PanePolicy.favesCatalogSize(serverLast = 1, lastPageCount = 37))
        assertEquals(7 * 100 + 30, PanePolicy.favesCatalogSize(serverLast = 8, lastPageCount = 30))
        assertEquals(8 * 100, PanePolicy.favesCatalogSize(serverLast = 8, lastPageCount = 100))
        assertEquals(67, PanePolicy.lastPage(8 * 100, 12))
        assertEquals(61, PanePolicy.lastPage(PanePolicy.favesCatalogSize(8, 30), 12))
    }
}
