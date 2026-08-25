package io.r_a_d.geiravor.playback

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
}
