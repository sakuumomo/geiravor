package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTest {
    @Test
    fun phoneTabsAreFourWithSettingsLast() {
        val tabs = phoneTabs()
        assertEquals(4, tabs.size)
        assertEquals(BottomTab.NowPlaying, tabs.first())
        assertEquals(BottomTab.Settings, tabs.last())
        assertEquals(
            listOf(BottomTab.NowPlaying, BottomTab.Songs, BottomTab.Board, BottomTab.Settings),
            tabs,
        )
    }

    @Test
    fun twoPaneDropsNowPlayingTab() {
        val tabs = paneTabs()
        assertFalse(tabs.contains(BottomTab.NowPlaying))
        assertEquals(listOf(BottomTab.Songs, BottomTab.Board, BottomTab.Settings), tabs)
    }

    @Test
    fun songsQueueIsASectionNotABottomTab() {
        assertTrue(SongsSection.entries.any { it.label == "Last Played" })
        assertTrue(SongsSection.entries.any { it.label == "Favorites" })
        assertFalse(SongsSection.entries.any { it.label.contains("Faves") })
    }
}
