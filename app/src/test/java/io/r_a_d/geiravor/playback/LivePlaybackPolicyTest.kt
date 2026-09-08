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
    fun blankDjImageIsNull() {
        assertNull(LivePlaybackPolicy.djImageUrl(" "))
        assertEquals(
            "https://r-a-d.io/api/dj-image/18-e0177611a37081b5.png",
            LivePlaybackPolicy.djImageUrl("18-e0177611a37081b5.png"),
        )
    }
}
