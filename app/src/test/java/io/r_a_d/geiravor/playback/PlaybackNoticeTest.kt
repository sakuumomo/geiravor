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
        assertFalse(PlaybackNotice.needsImmediateForeground(null))
    }
}
