package io.r_a_d.geiravor.playback

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList

/** Product: docs/spec/playback.md and docs/spec/android-auto.md. */
object LivePlaybackPolicy {
    const val STREAM_URL = "https://stream.r-a-d.io/main.mp3"
    const val FAVE = "FAVE"
    const val MUTE = "MUTE"
    const val VOL_UP = "VOL_UP"
    const val VOL_DOWN = "VOL_DOWN"
    const val DEFAULT_GAIN = 0.8f
    const val VOL_STEP = 0.05f
    const val RECONNECT_DELAY_MS = 2000L

    data class MediaButtonSpec(
        val action: String,
        val displayName: String,
        val icon: Int,
        val slot: Int,
    )

    fun shouldReconnect(wantPlay: Boolean): Boolean = wantPlay

    fun stepGain(current: Float, delta: Float): Float =
        (current + delta).coerceIn(0f, 1f)

    fun unmuteGain(lastNonZero: Float): Float =
        if (lastNonZero > 0f) lastNonZero else DEFAULT_GAIN

    fun mediaButtonSpecs(heartFilled: Boolean): List<MediaButtonSpec> =
        listOf(
            MediaButtonSpec(MUTE, "Mute", CommandButton.ICON_VOLUME_OFF, CommandButton.SLOT_BACK),
            MediaButtonSpec(
                FAVE,
                "Fave",
                if (heartFilled) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
                CommandButton.SLOT_FORWARD,
            ),
            MediaButtonSpec(
                VOL_DOWN,
                "Vol −",
                CommandButton.ICON_VOLUME_DOWN,
                CommandButton.SLOT_BACK_SECONDARY,
            ),
            MediaButtonSpec(
                VOL_UP,
                "Vol +",
                CommandButton.ICON_VOLUME_UP,
                CommandButton.SLOT_FORWARD_SECONDARY,
            ),
        )

    fun mediaButtons(heartFilled: Boolean): ImmutableList<CommandButton> {
        val buttons = mediaButtonSpecs(heartFilled).map { spec ->
            CommandButton.Builder(spec.icon)
                .setDisplayName(spec.displayName)
                .setSessionCommand(SessionCommand(spec.action, android.os.Bundle.EMPTY))
                .setSlots(spec.slot)
                .build()
        }
        return ImmutableList.copyOf(buttons)
    }

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

    /**
     * After pause/stop Icecast is torn down (`IDLE`) but the session must stay
     * paused `STATE_READY` so Auto/shade keep the live card.
     */
    fun reportedPlaybackState(wantPlay: Boolean, exoState: Int): Int =
        if (!wantPlay && (exoState == Player.STATE_IDLE || exoState == Player.STATE_ENDED)) {
            Player.STATE_READY
        } else {
            exoState
        }
}
