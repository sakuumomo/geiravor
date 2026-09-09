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
        val customIcon: Int = 0,
    )

    fun shouldReconnect(wantPlay: Boolean): Boolean = wantPlay

    fun isVehicleController(packageName: String): Boolean {
        val p = packageName.lowercase()
        return p == "com.google.android.projection.gearhead" ||
            p.startsWith("com.google.android.projection.gearhead:") ||
            p.contains("android.projection") ||
            p == "com.google.android.carassistant"
    }

    fun shouldAutoStartVehicle(
        prefOn: Boolean,
        packageName: String,
        alreadyWantPlay: Boolean,
    ): Boolean = prefOn && isVehicleController(packageName) && !alreadyWantPlay

    fun leaveForegroundOnStop(wantPlay: Boolean, inForeground: Boolean): Boolean =
        !wantPlay && inForeground

    fun skipMedia3Notification(): Boolean = true

    /** Poll /api must not rewrite a stopped shade. User mute/fave/play still may. */
    fun shouldRefreshShadeFromSnapshot(wantPlay: Boolean): Boolean = wantPlay

    fun assumeDismissedIfShadeMissing(): Boolean = false

    fun stepGain(current: Float, delta: Float): Float =
        (current + delta).coerceIn(0f, 1f)

    fun unmuteGain(lastNonZero: Float): Float =
        if (lastNonZero > 0f) lastNonZero else DEFAULT_GAIN

    fun nextGainAfterMute(current: Float, lastNonZero: Float): Float =
        if (current > 0f) 0f else unmuteGain(lastNonZero)

    fun muted(gain: Float): Boolean = gain <= 0f

    /** Compact shade: play/stop, mute, Fave. Expanded adds Vol − / Vol +. */
    fun shadeCompactActionIndices(): IntArray = intArrayOf(0, 1, 2)

    fun mediaButtonSpecs(heartFilled: Boolean, muted: Boolean = false): List<MediaButtonSpec> =
        listOf(
            MediaButtonSpec(
                MUTE,
                if (muted) "Unmute" else "Mute",
                if (muted) CommandButton.ICON_VOLUME_OFF else CommandButton.ICON_VOLUME_UP,
                CommandButton.SLOT_BACK,
            ),
            MediaButtonSpec(
                FAVE,
                "Fave",
                if (heartFilled) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
                CommandButton.SLOT_FORWARD,
            ),
            MediaButtonSpec(
                VOL_DOWN,
                "Vol −",
                CommandButton.ICON_UNDEFINED,
                CommandButton.SLOT_BACK_SECONDARY,
                customIcon = io.r_a_d.geiravor.R.drawable.ic_vol_down,
            ),
            MediaButtonSpec(
                VOL_UP,
                "Vol +",
                CommandButton.ICON_UNDEFINED,
                CommandButton.SLOT_FORWARD_SECONDARY,
                customIcon = io.r_a_d.geiravor.R.drawable.ic_vol_up,
            ),
        )

    fun mediaButtons(heartFilled: Boolean, muted: Boolean = false): ImmutableList<CommandButton> {
        val buttons = mediaButtonSpecs(heartFilled, muted).map { spec ->
            val b = CommandButton.Builder(spec.icon)
                .setDisplayName(spec.displayName)
                .setSessionCommand(SessionCommand(spec.action, android.os.Bundle.EMPTY))
                .setSlots(spec.slot)
            if (spec.customIcon != 0) {
                b.setCustomIconResId(spec.customIcon)
            }
            b.build()
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

    fun seekRejected(): Boolean = true

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
