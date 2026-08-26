package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLayoutTest {
    @Test
    fun twoPaneIsTabletWideNotPhoneLandscape() {
        assertFalse(AppLayout.twoPane(widthDp = 411, smallestWidthDp = 411))
        assertFalse(AppLayout.twoPane(widthDp = 839, smallestWidthDp = 411))
        assertFalse(AppLayout.twoPane(widthDp = 915, smallestWidthDp = 411))
        assertFalse(AppLayout.twoPane(widthDp = 840, smallestWidthDp = 411))
        assertTrue(AppLayout.twoPane(widthDp = 840, smallestWidthDp = 600))
        assertTrue(AppLayout.twoPane(widthDp = 1280, smallestWidthDp = 800))
    }

    @Test
    fun twoPaneOmitsNowPlayingTab() {
        assertEquals(AppTab.entries.toList(), AppLayout.tabs(twoPane = false))
        assertEquals(listOf(AppTab.Songs, AppTab.Settings), AppLayout.tabs(twoPane = true))
    }

    @Test
    fun twoPaneUsesSongsWhenNowPlayingWasSelected() {
        assertEquals(AppTab.NowPlaying, AppLayout.clampTab(AppTab.NowPlaying, twoPane = false))
        assertEquals(AppTab.Songs, AppLayout.clampTab(AppTab.NowPlaying, twoPane = true))
        assertEquals(AppTab.Settings, AppLayout.clampTab(AppTab.Settings, twoPane = true))
    }
}
