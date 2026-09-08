package io.r_a_d.geiravor.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.Notifications
import io.r_a_d.geiravor.settings.SettingsStore
import kotlinx.coroutines.flow.first
import uniffi.geiravor_core.Status
import java.util.concurrent.TimeUnit

object DjNotifier {
    suspend fun consider(context: Context, settings: SettingsStore, status: Status, streamDown: Boolean) {
        val enabled = settings.djNotifierEnabled.first()
        val next = DjNotifierPolicy.fromStatus(status)
        if (!enabled) {
            return
        }
        val previous = settings.djSeen()
        if (DjNotifierPolicy.shouldNotify(enabled, previous, next, streamDown)) {
            notify(context, next)
        }
        if (!streamDown) {
            settings.setDjSeen(next)
        }
    }

    fun enqueue(context: Context, djOn: Boolean, faveOn: Boolean) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!FaveNotifierPolicy.workerNeeded(djOn, faveOn)) {
            wm.cancelUniqueWork(DjNotifierPolicy.WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<DjNotifierWorker>(
            DjNotifierPolicy.PERIOD_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(
            DjNotifierPolicy.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    suspend fun notify(context: Context, next: DjNotifierPolicy.Seen) {
        if (!Notifications.granted(context)) {
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val artwork = DjArtwork.loadStill(context, DjNotifierPolicy.artworkUrl(next.djImage))
        val notification = NotificationCompat.Builder(context, DjNotifierPolicy.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fave)
            .setContentTitle(DjNotifierPolicy.TITLE)
            .setContentText(DjNotifierPolicy.body(next))
            .setLargeIcon(artwork)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        NotificationManagerCompat.from(context)
            .notify(DjNotifierPolicy.NOTIFICATION_ID, notification)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            DjNotifierPolicy.CHANNEL_ID,
            "DJ notifier",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
}
