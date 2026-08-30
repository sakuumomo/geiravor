package io.r_a_d.geiravor.playback

import androidx.media3.session.CommandButton
import io.r_a_d.geiravor.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MutePolicyTest {
    @Test
    fun muteIconChangesAndIsNotVolumeUp() {
        val muted = MutePolicy.iconRes(muted = true)
        val unmuted = MutePolicy.iconRes(muted = false)
        assertNotEquals(muted, unmuted)
        assertEquals(R.drawable.ic_speaker, muted)
        assertEquals(R.drawable.ic_speaker_off, unmuted)
        assertNotEquals(R.drawable.ic_speaker, R.drawable.ic_speaker_off)
        assertEquals(CommandButton.ICON_VOLUME_OFF, MutePolicy.commandIcon(muted = true))
        assertEquals(CommandButton.ICON_VOLUME_UP, MutePolicy.commandIcon(muted = false))
    }
}
