package io.r_a_d.geiravor.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.playback.PlaybackService

object Alerts {
    const val CHANNEL = "geiravor_alerts"
    const val ID_DJ = 32
    const val ID_FAVE = 33
    const val ID_ALARM = 34

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alerts", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun show(context: Context, id: Int, body: String, alarmActions: Boolean = false) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(!alarmActions)
        if (alarmActions) {
            b.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_speaker_off,
                        "Stop",
                        PendingIntent.getService(
                            context,
                            1,
                            PlaybackService.stopIntent(context),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
                )
                .addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_speaker,
                        "Snooze",
                        PendingIntent.getBroadcast(
                            context,
                            2,
                            Intent(context, io.r_a_d.geiravor.alert.AlarmReceiver::class.java)
                                .setAction(io.r_a_d.geiravor.alert.AlarmReceiver.SNOOZE),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
                )
        }
        context.getSystemService(NotificationManager::class.java)?.notify(id, b.build())
    }
}
