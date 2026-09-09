package io.r_a_d.geiravor.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.setImmediateForeground

/**
 * Playback shade channel and the FGS media notification posted before Icecast is
 * playing. Notification id matches Media3's default (1001). `docs/spec/playback.md`.
 */
object PlaybackNotice {
    const val CHANNEL = "geiravor_playback"
    const val ID = 1001
    const val IMPORTANCE = NotificationManager.IMPORTANCE_LOW
    private const val GROUP_KEY = "media3_group_key"

    data class Shade(
        val title: CharSequence,
        val text: CharSequence,
        val playing: Boolean,
        val muted: Boolean,
        val heartFilled: Boolean,
        val art: Bitmap?,
    )

    fun needsImmediateForeground(action: String?): Boolean =
        action == PlaybackService.ACTION_PLAY || action == PlaybackService.ACTION_ALARM

    fun ongoing(playing: Boolean): Boolean = playing

    fun actionLabels(playing: Boolean, muted: Boolean): List<String> =
        listOf(
            if (playing) "Stop" else "Play",
            if (muted) "Unmute" else "Mute",
            "Fave",
            "Vol −",
            "Vol +",
        )

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.playback_channel),
                IMPORTANCE,
            ),
        )
    }

    fun shade(context: Context, model: Shade): Notification {
        ensureChannel(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            flags,
        )
        val play = PendingIntent.getService(context, 1, PlaybackService.playIntent(context), flags)
        val stop = PendingIntent.getService(context, 2, PlaybackService.stopIntent(context), flags)
        val mute = PendingIntent.getService(context, 3, PlaybackService.muteIntent(context), flags)
        val fave = PendingIntent.getService(context, 4, PlaybackService.faveIntent(context), flags)
        val down = PendingIntent.getService(context, 5, PlaybackService.volDownIntent(context), flags)
        val up = PendingIntent.getService(context, 6, PlaybackService.volUpIntent(context), flags)
        val dismiss = PendingIntent.getService(context, 7, PlaybackService.dismissIntent(context), flags)
        val compact = LivePlaybackPolicy.shadeCompactActionIndices()
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            .setOngoing(ongoing(model.playing))
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setGroup(GROUP_KEY)
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(*compact))
            .setImmediateForeground()
        if (model.art != null) {
            builder.setLargeIcon(model.art)
        }
        if (model.playing) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", stop)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", play)
        }
        builder.addAction(
            if (model.muted) R.drawable.ic_speaker_off else R.drawable.ic_speaker,
            if (model.muted) "Unmute" else "Mute",
            mute,
        )
        builder.addAction(
            if (model.heartFilled) R.drawable.ic_fave_filled else R.drawable.ic_fave,
            "Fave",
            fave,
        )
        builder.addAction(R.drawable.ic_vol_down, "Vol −", down)
        builder.addAction(R.drawable.ic_vol_up, "Vol +", up)
        return builder.build()
    }
}
