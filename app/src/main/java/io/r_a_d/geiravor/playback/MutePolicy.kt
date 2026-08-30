package io.r_a_d.geiravor.playback

import androidx.media3.session.CommandButton
import io.r_a_d.geiravor.R

object MutePolicy {
    fun iconRes(muted: Boolean): Int =
        if (muted) R.drawable.ic_speaker else R.drawable.ic_speaker_off

    fun commandIcon(muted: Boolean): Int =
        if (muted) CommandButton.ICON_VOLUME_OFF else CommandButton.ICON_VOLUME_UP
}
