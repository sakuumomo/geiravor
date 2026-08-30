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
    fun phoneTabsAreNowPlayingSongsBoardSettings() {
        assertEquals(
            listOf(AppTab.NowPlaying, AppTab.Songs, AppTab.Board, AppTab.Settings),
            AppLayout.tabs(twoPane = false),
        )
        assertEquals(AppTab.entries.toList(), AppLayout.tabs(twoPane = false))
    }

    @Test
    fun twoPaneOmitsNowPlayingTab() {
        assertEquals(
            listOf(AppTab.Songs, AppTab.Board, AppTab.Settings),
            AppLayout.tabs(twoPane = true),
        )
    }

    @Test
    fun labelsAreFavoritesNotFaves() {
        assertEquals("Now Playing", AppTab.NowPlaying.label)
        assertEquals("Board", AppTab.Board.label)
        assertTrue(AppTab.entries.none { it.label.contains("Faves") })
        assertEquals(2, TabLabelPolicy.MAX_LINES)
    }

    @Test
    fun twoPaneUsesSongsWhenNowPlayingWasSelected() {
        assertEquals(AppTab.NowPlaying, AppLayout.clampTab(AppTab.NowPlaying, twoPane = false))
        assertEquals(AppTab.Songs, AppLayout.clampTab(AppTab.NowPlaying, twoPane = true))
        assertEquals(AppTab.Settings, AppLayout.clampTab(AppTab.Settings, twoPane = true))
        assertEquals(AppTab.Board, AppLayout.clampTab(AppTab.Board, twoPane = true))
    }
}
