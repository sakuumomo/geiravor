package io.r_a_d.geiravor.playback

import androidx.media3.common.Player

object LivePlaybackPolicy {
    const val STREAM_URL = "https://stream.r-a-d.io/main.mp3"
    const val DEFAULT_GAIN = 0.8f

    enum class Command {
        PLAY,
        PAUSE,
        STOP,
    }

    enum class Action {
        STOP,
        REJECT,
    }

    val advertised: Set<Command> =
        setOf(Command.PLAY, Command.PAUSE, Command.STOP)

    fun onPause(): Action = Action.STOP

    fun onStop(): Action = Action.STOP

    fun onSeek(): Action = Action.REJECT

    fun onSkipNext(): Action = Action.REJECT

    fun onSkipPrevious(): Action = Action.REJECT

    fun toPercent(gain: Float): Float = (gain * 100f).coerceIn(0f, 100f)

    fun fromPercent(percent: Float): Float = (percent / 100f).coerceIn(0f, 1f)

    fun shouldReconnect(userWantsPlay: Boolean): Boolean = userWantsPlay

    fun reconnectDelayMs(): Long = 2_000L

    fun playRequiresNotificationPermission(): Boolean = false

    fun keepNotificationAfterStop(): Boolean = true

    fun leaveUnpreparedLiveItemAfterStop(): Boolean = true

    fun leavePlaybackCommand(): Command = Command.PAUSE

    fun showAsPlaying(playbackState: Int, isPlaying: Boolean, playWhenReady: Boolean): Boolean {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            return false
        }
        return isPlaying || playWhenReady
    }
}
