package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongListPolicyTest {
    @Test
    fun hidesQueueWhenNotAfkEvenIfApiSentRows() {
        assertTrue(SongListPolicy.showQueue(isAfkStream = true))
        assertFalse(SongListPolicy.showQueue(isAfkStream = false))
    }

    @Test
    fun requestRowsGetSlashRMarker() {
        assertEquals("in 3 minutes", SongListPolicy.caption(isRequest = false, whenText = "in 3 minutes"))
        assertEquals("/r/ · 1 minute ago", SongListPolicy.caption(isRequest = true, whenText = "1 minute ago"))
    }

    @Test
    fun nowPlayingShowsPreviousAndNextFromListsWhenAfk() {
        val n = SongListPolicy.neighbors(
            lastPlayedMeta = "Hirasawa Susumu - Gats",
            nextInQueueMeta = "Aimer - ninelie",
            isAfkStream = true,
        )
        assertEquals("Hirasawa Susumu - Gats", n.previous)
        assertEquals("Aimer - ninelie", n.next)
    }

    @Test
    fun liveDjNextIsUnknown() {
        val n = SongListPolicy.neighbors(
            lastPlayedMeta = "Previous - Song",
            nextInQueueMeta = "Should Be Hidden - Track",
            isAfkStream = false,
        )
        assertEquals("Previous - Song", n.previous)
        assertEquals("???", n.next)
    }

    @Test
    fun missingNeighborsUseDashWhenAfk() {
        val n = SongListPolicy.neighbors(null, null, isAfkStream = true)
        assertEquals("—", n.previous)
        assertEquals("—", n.next)
    }
}
