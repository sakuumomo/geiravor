package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LivePlaybackPolicyTest {
    @Test
    fun streamUrlIsOfficialM3u() {
        assertEquals("https://stream.r-a-d.io/main.mp3", LivePlaybackPolicy.STREAM_URL)
        assertFalse(LivePlaybackPolicy.STREAM_URL.startsWith("https://r-a-d.io/main"))
    }

    @Test
    fun pauseIsImplementedAsStop() {
        assertEquals(LivePlaybackPolicy.Action.STOP, LivePlaybackPolicy.onPause())
        assertEquals(LivePlaybackPolicy.Action.STOP, LivePlaybackPolicy.onStop())
    }

    @Test
    fun seekAndSkipAreRejected() {
        assertEquals(LivePlaybackPolicy.Action.REJECT, LivePlaybackPolicy.onSeek())
        assertEquals(LivePlaybackPolicy.Action.REJECT, LivePlaybackPolicy.onSkipNext())
        assertEquals(LivePlaybackPolicy.Action.REJECT, LivePlaybackPolicy.onSkipPrevious())
    }

    @Test
    fun advertisedControlsArePlayPauseStopOnly() {
        assertEquals(
            setOf(
                LivePlaybackPolicy.Command.PLAY,
                LivePlaybackPolicy.Command.PAUSE,
                LivePlaybackPolicy.Command.STOP,
            ),
            LivePlaybackPolicy.advertised,
        )
    }

    @Test
    fun defaultGainMatchesSiteVolumeEighty() {
        assertEquals(0.8f, LivePlaybackPolicy.DEFAULT_GAIN, 0.0001f)
    }

    @Test
    fun gainMapsToSitePercentScale() {
        assertEquals(80f, LivePlaybackPolicy.toPercent(LivePlaybackPolicy.DEFAULT_GAIN), 0.0001f)
        assertEquals(0.8f, LivePlaybackPolicy.fromPercent(80f), 0.0001f)
        assertEquals(0f, LivePlaybackPolicy.fromPercent(-5f), 0.0001f)
        assertEquals(1f, LivePlaybackPolicy.fromPercent(140f), 0.0001f)
        assertEquals(0f, LivePlaybackPolicy.toPercent(-1f), 0.0001f)
        assertEquals(100f, LivePlaybackPolicy.toPercent(2f), 0.0001f)
    }
}
