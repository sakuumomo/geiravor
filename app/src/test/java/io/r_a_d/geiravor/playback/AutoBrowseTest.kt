package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.ListEntry

class AutoBrowseTest {
    @Test
    fun rootHidesQueueWhenNotAfk() {
        val afk = AutoBrowse.rootChildren(isAfkStream = true).map { it.id }
        assertEquals(
            listOf(AutoBrowse.NOW_PLAYING, AutoBrowse.LAST_PLAYED, AutoBrowse.QUEUE),
            afk,
        )
        val liveDj = AutoBrowse.rootChildren(isAfkStream = false).map { it.id }
        assertEquals(
            listOf(AutoBrowse.NOW_PLAYING, AutoBrowse.LAST_PLAYED),
            liveDj,
        )
    }

    @Test
    fun onlyNowPlayingIsPlayable() {
        val nodes = AutoBrowse.rootChildren(isAfkStream = true)
        assertTrue(nodes.single { it.id == AutoBrowse.NOW_PLAYING }.playable)
        assertFalse(nodes.single { it.id == AutoBrowse.NOW_PLAYING }.browsable)
        assertFalse(nodes.single { it.id == AutoBrowse.LAST_PLAYED }.playable)
        assertTrue(nodes.single { it.id == AutoBrowse.LAST_PLAYED }.browsable)
        assertFalse(nodes.single { it.id == AutoBrowse.QUEUE }.playable)
        assertTrue(nodes.single { it.id == AutoBrowse.QUEUE }.browsable)
    }

    @Test
    fun lastPlayedChildrenAreDisplayOnly() {
        val children = AutoBrowse.children(
            AutoBrowse.LAST_PLAYED,
            sampleStatus(lastPlayed = listOf(entry("Hirasawa Susumu - Gats"))),
        )
        assertEquals(1, children.size)
        assertEquals("Hirasawa Susumu - Gats", children[0].title)
        assertFalse(children[0].playable)
        assertFalse(children[0].browsable)
    }

    @Test
    fun queueChildrenHiddenWhenNotAfkEvenIfApiSentRows() {
        val liveDj = sampleStatus(
            isAfkStream = false,
            queue = listOf(entry("Should Be Hidden - Track")),
        )
        assertTrue(AutoBrowse.children(AutoBrowse.ROOT, liveDj).none { it.id == AutoBrowse.QUEUE })
        assertTrue(AutoBrowse.children(AutoBrowse.QUEUE, liveDj).isEmpty())
    }

    @Test
    fun subtitleShowsPrevAndNextWhenAfk() {
        assertEquals(
            "Prev: Hirasawa Susumu - Gats · Next: Aimer - ninelie",
            AutoBrowse.subtitle(
                lastPlayedMeta = "Hirasawa Susumu - Gats",
                nextInQueueMeta = "Aimer - ninelie",
                isAfkStream = true,
            ),
        )
    }

    @Test
    fun subtitleOmitsPrevWhenLastPlayedEmpty() {
        assertEquals(
            "Next: Aimer - ninelie",
            AutoBrowse.subtitle(
                lastPlayedMeta = null,
                nextInQueueMeta = "Aimer - ninelie",
                isAfkStream = true,
            ),
        )
    }

    @Test
    fun subtitleLiveDjNextIsUnknown() {
        assertEquals(
            "Prev: Previous - Song · Next: ???",
            AutoBrowse.subtitle(
                lastPlayedMeta = "Previous - Song",
                nextInQueueMeta = "Should Be Hidden - Track",
                isAfkStream = false,
            ),
        )
    }

    @Test
    fun subtitleOmitsNextOnlyWhenAfkQueueEmpty() {
        assertEquals(
            "Prev: Hirasawa Susumu - Gats",
            AutoBrowse.subtitle(
                lastPlayedMeta = "Hirasawa Susumu - Gats",
                nextInQueueMeta = null,
                isAfkStream = true,
            ),
        )
        assertNull(
            AutoBrowse.subtitle(
                lastPlayedMeta = null,
                nextInQueueMeta = null,
                isAfkStream = true,
            ),
        )
    }

    @Test
    fun liveStreamIdsAreThePlayableNowPlayingItem() {
        assertTrue(AutoBrowse.isLiveStream(AutoBrowse.NOW_PLAYING))
        assertFalse(AutoBrowse.isLiveStream(AutoBrowse.LAST_PLAYED))
        assertFalse(AutoBrowse.isLiveStream("lp:0"))
    }
}

private fun entry(meta: String) = ListEntry(
    meta = meta,
    artist = "",
    title = meta,
    timestamp = 0,
    isRequest = false,
)
