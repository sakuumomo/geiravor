package io.r_a_d.geiravor.playback

import androidx.media3.common.Player

object LivePlaybackPolicy {
    const val STREAM_URL = "https://stream.r-a-d.io/main.mp3"
    const val DEFAULT_GAIN = 0.8f
    const val VOLUME_STEP_PERCENT = 5f
    const val VOLUME_UP = "io.r_a_d.geiravor.VOLUME_UP"
    const val VOLUME_DOWN = "io.r_a_d.geiravor.VOLUME_DOWN"
    const val FAVE = "io.r_a_d.geiravor.FAVE"

    enum class Command {
        PLAY,
        PAUSE,
        STOP,
    }

    enum class Action {
        STOP,
        REJECT,
    }

    fun onPause(): Action = Action.STOP

    fun onStop(): Action = Action.STOP

    fun onSeek(): Action = Action.REJECT

    fun onSkipNext(): Action = Action.REJECT

    fun onSkipPrevious(): Action = Action.REJECT

    fun toPercent(gain: Float): Float = (gain * 100f).coerceIn(0f, 100f)

    fun fromPercent(percent: Float): Float = (percent / 100f).coerceIn(0f, 1f)

    fun nudgeGain(gain: Float, up: Boolean): Float {
        val delta = if (up) VOLUME_STEP_PERCENT else -VOLUME_STEP_PERCENT
        return fromPercent(toPercent(gain) + delta)
    }

    fun volumeLabel(gain: Float): String = toPercent(gain).toInt().toString()

    fun faveIsStub(): Boolean = true

    fun shouldReconnect(userWantsPlay: Boolean): Boolean = userWantsPlay

    fun reconnectDelayMs(): Long = 2_000L

    fun leavePlaybackCommand(): Command = Command.PAUSE

    data class SessionPlaybackState(
        val state: Int,
        val playWhenReady: Boolean,
    )

    fun sessionPlaybackState(
        playbackState: Int,
        playWhenReady: Boolean,
        wantsPlayback: Boolean,
        hasLiveItem: Boolean,
        holdAsPaused: Boolean,
    ): SessionPlaybackState {
        val hold = holdAsPaused &&
            !wantsPlayback &&
            hasLiveItem &&
            (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED)
        if (!hold) {
            return SessionPlaybackState(playbackState, playWhenReady)
        }
        return SessionPlaybackState(Player.STATE_READY, playWhenReady = false)
    }

    fun showAsPlaying(playbackState: Int, isPlaying: Boolean, playWhenReady: Boolean): Boolean {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            return false
        }
        return isPlaying || playWhenReady
    }
}
