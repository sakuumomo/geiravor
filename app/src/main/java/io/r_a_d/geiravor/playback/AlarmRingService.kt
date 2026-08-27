package io.r_a_d.geiravor.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.startMediaPlaybackForeground
import io.r_a_d.geiravor.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmRingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private var fallback: MediaPlayer? = null
    private var snoozeEnabled = AlarmPolicy.SNOOZE_ENABLED_DEFAULT
    private var snoozeMinutes = AlarmPolicy.DEFAULT_SNOOZE_MINUTES
    private var alarmHour = AlarmPolicy.DEFAULT_HOUR
    private var alarmMinute = AlarmPolicy.DEFAULT_MINUTE
    private var alarmEnabled = AlarmPolicy.ENABLED_DEFAULT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startMediaPlaybackForeground(AlarmPolicy.NOTIFICATION_ID, notification())
        when (intent?.action) {
            AlarmPolicy.ACTION_STOP -> scope.launch {
                loadSettings()
                stopRinging(rescheduleDaily = true)
            }
            AlarmPolicy.ACTION_SNOOZE -> scope.launch {
                loadSettings()
                snooze()
            }
            else -> fire()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        releaseFallback()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loadSettings() {
        val settings = SettingsStore(applicationContext)
        withContext(Dispatchers.IO) {
            snoozeEnabled = settings.snoozeEnabled.first()
            snoozeMinutes = settings.snoozeMinutes.first()
            alarmHour = settings.alarmHour.first()
            alarmMinute = settings.alarmMinute.first()
            alarmEnabled = settings.alarmEnabled.first()
        }
    }

    private fun fire() {
        scope.launch {
            loadSettings()
            startMediaPlaybackForeground(AlarmPolicy.NOTIFICATION_ID, notification())
            playLiveStream(applicationContext)
            main.postDelayed({
                liveStreamPlaying(applicationContext) { playing ->
                    if (AlarmPolicy.shouldPlayFallback(playing)) {
                        stopLiveStream(applicationContext)
                        startFallback()
                    }
                }
            }, AlarmPolicy.FALLBACK_WAIT_MS)
        }
    }

    private fun snooze() {
        AlarmScheduler.scheduleSnooze(applicationContext, snoozeMinutes)
        stopLiveStream(applicationContext)
        stopRinging(rescheduleDaily = false)
    }

    private fun stopRinging(rescheduleDaily: Boolean) {
        main.removeCallbacksAndMessages(null)
        releaseFallback()
        if (rescheduleDaily) {
            AlarmScheduler.scheduleDaily(
                applicationContext,
                enabled = alarmEnabled,
                hour = alarmHour,
                minute = alarmMinute,
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startFallback() {
        if (fallback != null) {
            return
        }
        fallback = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                resources.openRawResourceFd(R.raw.alarm_fallback).use { fd ->
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun releaseFallback() {
        fallback?.run {
            runCatching { if (isPlaying) stop() }
            release()
        }
        fallback = null
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AlarmPolicy.CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, AlarmPolicy.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fave)
            .setContentTitle("r/a/dio")
            .setContentText("Alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Alarm"))
        val labels = AlarmPolicy.ringingActions(snoozeEnabled)
        labels.forEachIndexed { index, label ->
            val action = if (label == "Snooze") AlarmPolicy.ACTION_SNOOZE else AlarmPolicy.ACTION_STOP
            val pi = PendingIntent.getBroadcast(
                this,
                index + 2,
                Intent(this, AlarmReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(R.drawable.ic_fave, label, pi)
        }
        return builder.build()
    }
}
