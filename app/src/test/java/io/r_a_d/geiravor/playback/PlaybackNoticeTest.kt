package io.r_a_d.geiravor.playback

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNoticeTest {
    @Test
    fun playbackChannelIsNotAlerts() {
        assertEquals("geiravor_playback", PlaybackNotice.CHANNEL)
        assertEquals(1001, PlaybackNotice.ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, PlaybackNotice.IMPORTANCE)
        assertFalse(PlaybackNotice.CHANNEL == "geiravor_alerts")
    }

    @Test
    fun playAndAlarmEnterForegroundBeforeIcecast() {
        assertTrue(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_PLAY))
        assertTrue(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_ALARM))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_STOP))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_GAIN))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_SLEEP))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_FAVE))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_MUTE))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_VOL_UP))
        assertFalse(PlaybackNotice.needsImmediateForeground(PlaybackService.ACTION_DISMISS))
        assertFalse(PlaybackNotice.needsImmediateForeground(null))
    }

    @Test
    fun shadeIsOngoingOnlyWhilePlaying() {
        assertTrue(PlaybackNotice.ongoing(true))
        assertFalse(PlaybackNotice.ongoing(false))
    }

    @Test
    fun shadeActionsArePlayMuteFaveVol() {
        assertEquals(
            listOf("Stop", "Mute", "Fave", "Vol −", "Vol +"),
            PlaybackNotice.actionLabels(playing = true, muted = false),
        )
        assertEquals(
            listOf("Play", "Unmute", "Fave", "Vol −", "Vol +"),
            PlaybackNotice.actionLabels(playing = false, muted = true),
        )
        assertTrue(LivePlaybackPolicy.shadeCompactActionIndices().contentEquals(intArrayOf(0, 1, 2)))
    }
}
