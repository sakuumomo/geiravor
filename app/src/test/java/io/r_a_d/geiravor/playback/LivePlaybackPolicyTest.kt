package io.r_a_d.geiravor.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackPolicyTest {
    @Test
    fun pauseStops() {
        assertTrue(LivePlaybackPolicy.pauseStops())
    }

    @Test
    fun idleAfterStopLooksPausedReady() {
        assertEquals(
            Player.STATE_READY,
            LivePlaybackPolicy.reportedPlaybackState(false, Player.STATE_IDLE),
        )
        assertEquals(
            Player.STATE_READY,
            LivePlaybackPolicy.reportedPlaybackState(true, Player.STATE_READY),
        )
        assertEquals(
            Player.STATE_BUFFERING,
            LivePlaybackPolicy.reportedPlaybackState(true, Player.STATE_BUFFERING),
        )
    }

    @Test
    fun noSkipCommands() {
        val cmds = LivePlaybackPolicy.advertisedPlayerCommands().toSet()
        assertFalse(Player.COMMAND_SEEK_TO_NEXT in cmds)
        assertFalse(Player.COMMAND_SEEK_TO_PREVIOUS in cmds)
        assertFalse(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM in cmds)
        assertFalse(Player.COMMAND_GET_TIMELINE in cmds)
        assertTrue(Player.COMMAND_PLAY_PAUSE in cmds)
        assertTrue(Player.COMMAND_STOP in cmds)
    }

    @Test
    fun reconnectsAfterTwoSecondsOnlyWhileWantingPlay() {
        assertEquals(2000L, LivePlaybackPolicy.RECONNECT_DELAY_MS)
        assertTrue(LivePlaybackPolicy.shouldReconnect(true))
        assertFalse(LivePlaybackPolicy.shouldReconnect(false))
    }

    @Test
    fun blankDjImageIsNull() {
        assertNull(LivePlaybackPolicy.djImageUrl(" "))
        assertEquals(
            "https://r-a-d.io/api/dj-image/18-e0177611a37081b5.png",
            LivePlaybackPolicy.djImageUrl("18-e0177611a37081b5.png"),
        )
    }

    @Test
    fun autoVolumeStepsFivePercent() {
        assertEquals(0.05f, LivePlaybackPolicy.VOL_STEP, 0.0001f)
        assertEquals(0.85f, LivePlaybackPolicy.stepGain(0.8f, LivePlaybackPolicy.VOL_STEP), 0.0001f)
        assertEquals(0f, LivePlaybackPolicy.stepGain(0.02f, -LivePlaybackPolicy.VOL_STEP), 0.0001f)
        assertEquals(1f, LivePlaybackPolicy.stepGain(0.99f, LivePlaybackPolicy.VOL_STEP), 0.0001f)
        assertEquals(0.8f, LivePlaybackPolicy.unmuteGain(0f), 0.0001f)
        assertEquals(0.4f, LivePlaybackPolicy.unmuteGain(0.4f), 0.0001f)
        assertEquals(0f, LivePlaybackPolicy.nextGainAfterMute(0.8f, 0.8f), 0.0001f)
        assertEquals(0.4f, LivePlaybackPolicy.nextGainAfterMute(0f, 0.4f), 0.0001f)
        assertEquals(0.8f, LivePlaybackPolicy.nextGainAfterMute(0f, 0f), 0.0001f)
    }

    @Test
    fun mediaButtonsAreMuteFaveVol() {
        val outline = LivePlaybackPolicy.mediaButtonSpecs(heartFilled = false)
        assertEquals(4, outline.size)
        assertEquals(LivePlaybackPolicy.MUTE, outline[0].action)
        assertEquals(androidx.media3.session.CommandButton.SLOT_BACK, outline[0].slot)
        assertEquals(LivePlaybackPolicy.FAVE, outline[1].action)
        assertEquals(androidx.media3.session.CommandButton.SLOT_FORWARD, outline[1].slot)
        assertEquals(
            androidx.media3.session.CommandButton.ICON_HEART_UNFILLED,
            outline[1].icon,
        )
        assertEquals(androidx.media3.session.CommandButton.SLOT_BACK_SECONDARY, outline[2].slot)
        assertEquals(androidx.media3.session.CommandButton.SLOT_FORWARD_SECONDARY, outline[3].slot)
        val filled = LivePlaybackPolicy.mediaButtonSpecs(heartFilled = true)
        assertEquals(androidx.media3.session.CommandButton.ICON_HEART_FILLED, filled[1].icon)
        assertEquals("Fave", filled[1].displayName)
        assertFalse(filled.any { it.displayName.contains("Faves") })
    }
}
