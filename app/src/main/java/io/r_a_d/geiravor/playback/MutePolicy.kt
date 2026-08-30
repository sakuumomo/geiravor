package io.r_a_d.geiravor.playback

import io.r_a_d.geiravor.R

object MutePolicy {
    fun iconRes(muted: Boolean): Int =
        if (muted) R.drawable.ic_speaker else R.drawable.ic_speaker_off
}
