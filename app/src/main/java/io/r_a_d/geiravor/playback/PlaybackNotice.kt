package io.r_a_d.geiravor.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R

/**
 * Playback shade channel and the FGS placeholder posted before Icecast is
 * playing. Notification id matches Media3's default (1001). `docs/spec/playback.md`.
 */
object PlaybackNotice {
    const val CHANNEL = "geiravor_playback"
    const val ID = 1001
    const val IMPORTANCE = NotificationManager.IMPORTANCE_LOW
    private const val GROUP_KEY = "media3_group_key"

    fun needsImmediateForeground(action: String?): Boolean =
        action == PlaybackService.ACTION_PLAY || action == PlaybackService.ACTION_ALARM

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

    fun connecting(context: Context, title: CharSequence, text: CharSequence): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setGroup(GROUP_KEY)
            .build()
    }
}
