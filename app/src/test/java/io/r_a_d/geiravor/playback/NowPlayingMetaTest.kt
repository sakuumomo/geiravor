package io.r_a_d.geiravor.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingMetaTest {
    @Test
    fun autoSplitsArtistAndDj() {
        val f = NowPlayingMeta.fields(
            title = "ninelie",
            artist = "Aimer with chelly (EGOIST)",
            np = "Aimer with chelly (EGOIST) - ninelie",
            dj = "Hanyuu-sama",
        )
        assertEquals("ninelie", f.title)
        assertEquals("Aimer with chelly (EGOIST)", f.subtitle)
        assertEquals("Hanyuu-sama", f.description)
        assertEquals("Aimer with chelly (EGOIST)", f.artist)
        assertEquals("Hanyuu-sama", f.dj)
    }

    @Test
    fun blankArtistUsesDjAsSubtitleWithoutDescription() {
        val f = NowPlayingMeta.fields("mix", "  ", "mix", "The DJ")
        assertEquals("mix", f.title)
        assertEquals("The DJ", f.subtitle)
        assertEquals("", f.description)
        assertEquals("", f.artist)
        assertEquals("The DJ", f.dj)
    }

    @Test
    fun blankTitleFallsBackToNp() {
        val f = NowPlayingMeta.fields("", "A", "A - B", "Hanyuu-sama")
        assertEquals("A - B", f.title)
        assertEquals("A", f.subtitle)
        assertEquals("Hanyuu-sama", f.description)
    }

    @Test
    fun liveOnlyWhenDurationUnknown() {
        assertTrue(NowPlayingMeta.isLive(C.TIME_UNSET))
        assertFalse(NowPlayingMeta.isLive(180_000))
        assertFalse(NowPlayingMeta.isLive(1))
    }

    @Test
    fun afkPositionIsApiWindowNotPlayerBuffer() {
        assertEquals(
            40_000,
            NowPlayingMeta.songPositionMs(30_000, 1_000_000, 180_000, 1_010_000),
        )
        assertEquals(
            180_000,
            NowPlayingMeta.songPositionMs(30_000, 1_000_000, 180_000, 2_000_000),
        )
        assertEquals(0, NowPlayingMeta.songPositionMs(30_000, 0, C.TIME_UNSET, 1_000_000))
    }
}
