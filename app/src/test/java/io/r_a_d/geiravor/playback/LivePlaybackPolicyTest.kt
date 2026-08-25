package io.r_a_d.geiravor.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun idleOrEndedNeverShowsAsPlayingEvenIfIsPlayingStale() {
        assertFalse(
            LivePlaybackPolicy.showAsPlaying(
                playbackState = Player.STATE_IDLE,
                isPlaying = true,
                playWhenReady = true,
            ),
        )
        assertFalse(
            LivePlaybackPolicy.showAsPlaying(
                playbackState = Player.STATE_ENDED,
                isPlaying = false,
                playWhenReady = true,
            ),
        )
        assertTrue(
            LivePlaybackPolicy.showAsPlaying(
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
                playWhenReady = true,
            ),
        )
        assertTrue(
            LivePlaybackPolicy.showAsPlaying(
                playbackState = Player.STATE_READY,
                isPlaying = true,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun leavePlaybackUsesPauseSoMedia3WillFirePlayPause() {
        assertEquals(LivePlaybackPolicy.Command.PAUSE, LivePlaybackPolicy.leavePlaybackCommand())
    }

    @Test
    fun playDoesNotRequireNotificationPermission() {
        assertFalse(LivePlaybackPolicy.playRequiresNotificationPermission())
    }

    @Test
    fun pauseKeepsIdlePlayTargetSoNotificationCanStay() {
        assertTrue(LivePlaybackPolicy.keepNotificationAfterStop())
        assertTrue(LivePlaybackPolicy.leaveUnpreparedLiveItemAfterStop())
    }

    @Test
    fun autoReconnectsOnlyWhileUserWantsPlay() {
        assertTrue(LivePlaybackPolicy.shouldReconnect(userWantsPlay = true))
        assertFalse(LivePlaybackPolicy.shouldReconnect(userWantsPlay = false))
        assertEquals(2_000L, LivePlaybackPolicy.reconnectDelayMs())
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

    @Test
    fun integerPercentsSurviveGainRoundTrip() {
        for (p in 0..100) {
            assertEquals(
                p.toFloat(),
                LivePlaybackPolicy.toPercent(LivePlaybackPolicy.fromPercent(p.toFloat())),
                0.001f,
            )
        }
    }

    @Test
    fun autoVolumeNudgesFivePercentAndClamps() {
        assertEquals(0.85f, LivePlaybackPolicy.nudgeGain(0.8f, up = true), 0.0001f)
        assertEquals(0.75f, LivePlaybackPolicy.nudgeGain(0.8f, up = false), 0.0001f)
        assertEquals(1f, LivePlaybackPolicy.nudgeGain(1f, up = true), 0.0001f)
        assertEquals(0f, LivePlaybackPolicy.nudgeGain(0f, up = false), 0.0001f)
        assertEquals("80", LivePlaybackPolicy.volumeLabel(LivePlaybackPolicy.DEFAULT_GAIN))
    }
}
