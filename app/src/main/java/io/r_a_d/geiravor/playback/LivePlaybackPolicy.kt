package io.r_a_d.geiravor.playback

import androidx.media3.common.Player
import androidx.media3.session.SessionCommand

/** Product: docs/spec/playback.md and docs/spec/android-auto.md. */
object LivePlaybackPolicy {
    const val STREAM_URL = "https://stream.r-a-d.io/main.mp3"
    const val FAVE = "FAVE"
    const val MUTE = "MUTE"
    const val VOL_UP = "VOL_UP"
    const val VOL_DOWN = "VOL_DOWN"
    const val DEFAULT_GAIN = 0.8f

    fun djImageUrl(image: String?): String? {
        val name = image?.trim().orEmpty()
        if (name.isEmpty()) return null
        return "https://r-a-d.io/api/dj-image/$name"
    }

    /** Command ints the session advertises. Skip/seek are never in this list. */
    fun advertisedPlayerCommands(): IntArray =
        intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        )

    fun playerCommands(): Player.Commands {
        val b = Player.Commands.Builder()
        advertisedPlayerCommands().forEach { b.add(it) }
        return b.build()
    }

    fun sessionCommands(): androidx.media3.session.SessionCommands =
        androidx.media3.session.SessionCommands.Builder()
            .add(SessionCommand(FAVE, android.os.Bundle.EMPTY))
            .add(SessionCommand(MUTE, android.os.Bundle.EMPTY))
            .add(SessionCommand(VOL_UP, android.os.Bundle.EMPTY))
            .add(SessionCommand(VOL_DOWN, android.os.Bundle.EMPTY))
            .build()

    fun pauseStops(): Boolean = true
}
