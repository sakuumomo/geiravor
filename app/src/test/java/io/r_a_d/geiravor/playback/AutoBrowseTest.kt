package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.ListEntry

class AutoBrowseTest {
    @Test
    fun browseRootIsSongsAndSettingsTabs() {
        val root = AutoBrowse.rootChildren()
        assertEquals(listOf(AutoBrowse.SONGS, AutoBrowse.SETTINGS), root.map { it.id })
        assertTrue(root.all { it.browsable && !it.playable })
        assertTrue(root.none { it.id == AutoBrowse.NOW_PLAYING })
        assertTrue(root.none { it.id == AutoBrowse.QUEUE })
    }

    @Test
    fun songsFolderMirrorsPhoneSongsSections() {
        val afk = AutoBrowse.children(AutoBrowse.SONGS, sampleStatus(isAfkStream = true))
        assertEquals(listOf(AutoBrowse.LAST_PLAYED, AutoBrowse.QUEUE), afk.map { it.id })
        assertTrue(afk.all { it.browsable && !it.playable })
        val liveDj = AutoBrowse.children(AutoBrowse.SONGS, sampleStatus(isAfkStream = false))
        assertEquals(listOf(AutoBrowse.LAST_PLAYED), liveDj.map { it.id })
    }

    @Test
    fun songRowsDoNotAllowPlayback() {
        assertFalse(AutoBrowse.allowsPlayback(mediaId = "lp:0", uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.LAST_PLAYED, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.QUEUE, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.SONGS, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.SETTINGS, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.SETTING_VEHICLE, uri = LivePlaybackPolicy.STREAM_URL))
        assertTrue(
            AutoBrowse.allowsPlayback(
                mediaId = AutoBrowse.NOW_PLAYING,
                uri = LivePlaybackPolicy.STREAM_URL,
            ),
        )
        assertTrue(
            AutoBrowse.allowsPlayback(
                mediaId = AutoBrowse.ROOT,
                uri = LivePlaybackPolicy.STREAM_URL,
            ),
        )
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
        assertTrue(AutoBrowse.children(AutoBrowse.SONGS, liveDj).none { it.id == AutoBrowse.QUEUE })
        assertTrue(AutoBrowse.children(AutoBrowse.QUEUE, liveDj).isEmpty())
        assertTrue(AutoBrowse.rootChildren().none { it.id == AutoBrowse.QUEUE })
    }

    @Test
    fun settingsTogglesArePlayableButNeverTheLiveStream() {
        val off = AutoBrowse.children(
            AutoBrowse.SETTINGS,
            sampleStatus(),
            AutoSettingsSnapshot(vehicleOn = false, plugOn = true, versionName = "0.1.0"),
        )
        assertEquals(
            listOf(AutoBrowse.SETTING_VEHICLE, AutoBrowse.SETTING_PLUG, AutoBrowse.SETTING_ABOUT),
            off.map { it.id },
        )
        assertEquals("Off", off[0].subtitle)
        assertEquals("On", off[1].subtitle)
        assertTrue(off[0].playable && !off[0].browsable)
        assertTrue(off[1].playable && !off[1].browsable)
        assertFalse(off[2].playable)
        assertFalse(off[2].browsable)
        assertTrue(AutoBrowse.isSettingsToggle(AutoBrowse.SETTING_VEHICLE))
        assertTrue(AutoBrowse.isSettingsToggle(AutoBrowse.SETTING_PLUG))
        assertFalse(AutoBrowse.isSettingsToggle(AutoBrowse.SETTING_ABOUT))
        assertFalse(
            AutoBrowse.allowsPlayback(
                mediaId = AutoBrowse.SETTING_VEHICLE,
                uri = "content://io.r_a_d.geiravor/settings/vehicle",
            ),
        )
        assertTrue(
            AutoBrowse.children(
                AutoBrowse.SETTING_VEHICLE,
                sampleStatus(),
                AutoSettingsSnapshot(vehicleOn = true, plugOn = false),
            ).isEmpty(),
        )
    }

    @Test
    fun skipReplacingLiveStreamOnlyWhileActuallyPlaying() {
        assertTrue(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = true,
            ),
        )
        assertTrue(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.SETTING_VEHICLE to "content://io.r_a_d.geiravor/settings/vehicle"),
                activelyPlaying = true,
            ),
        )
        assertFalse(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = false,
            ),
        )
        assertTrue(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.SETTING_PLUG to "content://io.r_a_d.geiravor/settings/plug"),
                activelyPlaying = false,
            ),
        )
        assertFalse(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = null,
                currentUri = null,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = true,
            ),
        )
    }

    @Test
    fun liveStreamIdsAreThePlayableNowPlayingItem() {
        assertTrue(AutoBrowse.isLiveStream(AutoBrowse.NOW_PLAYING))
        assertTrue(AutoBrowse.isLiveStream(AutoBrowse.ROOT))
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
