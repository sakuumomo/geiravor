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
        assertEquals("https://r-a-d.io/api/dj-image/", LivePlaybackPolicy.DJ_IMAGE_BASE)
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

    @Test
    fun muteTogglesToZeroAndRestoresLastGain() {
        val muted = LivePlaybackPolicy.toggleMute(current = 0.8f, lastUnmuted = 0.5f)
        assertEquals(0f, muted.gain, 0.0001f)
        assertEquals(0.8f, muted.lastUnmuted, 0.0001f)
        assertTrue(LivePlaybackPolicy.isMuted(muted.gain))
        val restored = LivePlaybackPolicy.toggleMute(current = 0f, lastUnmuted = 0.8f)
        assertEquals(0.8f, restored.gain, 0.0001f)
        assertFalse(LivePlaybackPolicy.isMuted(restored.gain))
        val fromZero = LivePlaybackPolicy.toggleMute(current = 0f, lastUnmuted = 0f)
        assertEquals(LivePlaybackPolicy.DEFAULT_GAIN, fromZero.gain, 0.0001f)
    }

    @Test
    fun gearheadIsAuto() {
        assertTrue(LivePlaybackPolicy.isAutoPackage("com.google.android.projection.gearhead"))
        assertFalse(LivePlaybackPolicy.isAutoPackage("io.r_a_d.geiravor"))
    }

    @Test
    fun faveCommandIsLive() {
        assertEquals("io.r_a_d.geiravor.FAVE", LivePlaybackPolicy.FAVE)
        assertEquals("io.r_a_d.geiravor.MUTE", LivePlaybackPolicy.MUTE)
    }

    @Test
    fun afkSongWindowIsNotReportedAsLiveBroadcast() {
        assertFalse(LivePlaybackPolicy.isLiveBroadcast(durationMs = 180_000L))
        assertTrue(LivePlaybackPolicy.isLiveBroadcast(durationMs = androidx.media3.common.C.TIME_UNSET))
        assertTrue(LivePlaybackPolicy.isLiveBroadcast(durationMs = 0L))
    }

    @Test
    fun songBufferDoesNotUseIcecastDownload() {
        assertEquals(65_000L, LivePlaybackPolicy.songBufferedPositionMs(positionMs = 65_000L))
    }

    @Test
    fun idleLiveItemHoldsAsPausedReadyBeforeFirstPlayAndAfterPause() {
        val connect = LivePlaybackPolicy.sessionPlaybackState(
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
            wantsPlayback = false,
            hasLiveItem = true,
        )
        assertEquals(Player.STATE_READY, connect.state)
        assertFalse(connect.playWhenReady)
        val afterPause = LivePlaybackPolicy.sessionPlaybackState(
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
            wantsPlayback = false,
            hasLiveItem = true,
        )
        assertEquals(Player.STATE_READY, afterPause.state)
        val noItem = LivePlaybackPolicy.sessionPlaybackState(
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
            wantsPlayback = false,
            hasLiveItem = false,
        )
        assertEquals(Player.STATE_IDLE, noItem.state)
        val connecting = LivePlaybackPolicy.sessionPlaybackState(
            playbackState = Player.STATE_IDLE,
            playWhenReady = true,
            wantsPlayback = true,
            hasLiveItem = true,
        )
        assertEquals(Player.STATE_IDLE, connecting.state)
    }

    @Test
    fun autoConnectDoesNotPlayUnlessVehicleAutoStart() {
        assertTrue(LivePlaybackPolicy.suppressAutoConnectPlay(vehicleOn = false, wantsPlayback = false))
        assertFalse(LivePlaybackPolicy.suppressAutoConnectPlay(vehicleOn = true, wantsPlayback = false))
        assertFalse(LivePlaybackPolicy.suppressAutoConnectPlay(vehicleOn = false, wantsPlayback = true))
    }
}
