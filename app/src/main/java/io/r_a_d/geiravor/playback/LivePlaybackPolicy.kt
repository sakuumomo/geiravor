package io.r_a_d.geiravor.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
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
    const val SLEEP_FADE_MS = 15_000L

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
                .setSessionCommand(SessionCommand(spec.action, Bundle.EMPTY))
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

    fun liveItem(): MediaItem = MediaItem.fromUri(STREAM_URL)

    /** Command ints the session advertises. Skip/seek are never in this list. */
    fun advertisedPlayerCommands(): IntArray =
        intArrayOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_PREPARE,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        )

    /** Idle ExoPlayer drops Play; Auto then has no start control. */
    fun availableCommands(exo: Player.Commands): Player.Commands {
        val b = Player.Commands.Builder()
        advertisedPlayerCommands().forEach { b.add(it) }
        if (exo.contains(Player.COMMAND_GET_VOLUME)) b.add(Player.COMMAND_GET_VOLUME)
        if (exo.contains(Player.COMMAND_SET_VOLUME)) b.add(Player.COMMAND_SET_VOLUME)
        return b.build()
    }

    fun sessionCommands(): SessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(SessionCommand(FAVE, Bundle.EMPTY))
            .add(SessionCommand(MUTE, Bundle.EMPTY))
            .add(SessionCommand(VOL_UP, Bundle.EMPTY))
            .add(SessionCommand(VOL_DOWN, Bundle.EMPTY))
            .remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
            .build()

    /**
     * Settings rows are function items. Auto still follows a tap with Play.
     * Swallow that only while paused — not with a timer, and not a later user Play.
     */
    fun skipFollowUpPlayAfterSettingsTap(wantPlay: Boolean): Boolean = !wantPlay

    /**
     * Play reconnects Icecast. A second Play while we already want play and are
     * not idle/ended must not replace or re-prepare the live GET.
     */
    fun shouldRestartLive(wantPlay: Boolean, exoState: Int): Boolean =
        !wantPlay || exoState == Player.STATE_IDLE || exoState == Player.STATE_ENDED

    fun shouldPrepare(wantPlay: Boolean, exoState: Int): Boolean =
        wantPlay && (exoState == Player.STATE_IDLE || exoState == Player.STATE_ENDED)

    fun shouldApplyLiveMediaItem(wantPlay: Boolean, alreadyHasLive: Boolean): Boolean =
        !wantPlay && !alreadyHasLive

    /** Same live item across Play; a new MediaItem makes Auto leave now-playing. */
    fun shouldSetMediaItemOnPlay(alreadyHasLive: Boolean): Boolean = !alreadyHasLive

    fun pauseStops(): Boolean = true

    /** Auto remaining bar uses session buffer fields; Icecast bytes are not the song. */
    fun songBufferedPositionMs(positionMs: Long): Long = positionMs

    fun songBufferedPercentage(positionMs: Long, durationMs: Long): Int {
        if (durationMs == C.TIME_UNSET || durationMs <= 0L) return 0
        return ((positionMs.coerceAtLeast(0L) * 100L) / durationMs).toInt().coerceIn(0, 100)
    }

    fun totalBufferedDurationMs(): Long = 0L

    fun isSeekable(): Boolean = false

    /**
     * `song_progress` with local_at_fetch=0 adds unix `current` and clamps to duration
     * (Auto bar stuck full). A snapshot applied now uses now as fetch time.
     */
    fun localAtFetchSecs(nowSecs: Long, storedFetchedAtSecs: Long): Long =
        if (storedFetchedAtSecs <= 0L) nowSecs else storedFetchedAtSecs

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
