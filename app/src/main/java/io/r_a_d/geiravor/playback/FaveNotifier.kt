package io.r_a_d.geiravor.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.settings.SettingsStore
import kotlinx.coroutines.flow.first
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status

object FaveNotifier {
    suspend fun consider(
        context: Context,
        settings: SettingsStore,
        radio: RadioCore,
        status: Status,
        streamDown: Boolean,
        playing: Boolean,
    ) {
        val enabled = settings.faveNotifierEnabled.first()
        val next = FaveNotifierPolicy.fromStatus(status)
        if (!enabled) {
            return
        }
        val previous = settings.faveSeen()
        val nicks = FavePolicy.membershipNicks(
            settings.favesNick.first(),
            settings.ircNick.first(),
        )
        val isMember = FavePolicy.isMember(nicks, status, radio::isFavorite)
        if (
            FaveNotifierPolicy.shouldNotify(
                enabled = enabled,
                previous = previous,
                next = next,
                isMember = isMember,
                streamDown = streamDown,
                playing = playing,
            )
        ) {
            notify(context, next)
        }
        if (!streamDown) {
            settings.setFaveSeen(next)
        }
    }

    fun notify(context: Context, next: FaveNotifierPolicy.Seen) {
        if (!Notifications.granted(context)) {
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, FaveNotifierPolicy.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fave)
            .setContentTitle(FaveNotifierPolicy.TITLE)
            .setContentText(FaveNotifierPolicy.body(next.np))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        NotificationManagerCompat.from(context)
            .notify(FaveNotifierPolicy.NOTIFICATION_ID, notification)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            FaveNotifierPolicy.CHANNEL_ID,
            "Fave currently playing",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
}
