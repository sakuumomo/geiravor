package io.r_a_d.geiravor.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != AlarmPolicy.ACTION_FIRE &&
            action != AlarmPolicy.ACTION_STOP &&
            action != AlarmPolicy.ACTION_SNOOZE
        ) {
            return
        }
        val service = Intent(context, AlarmRingService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, service)
    }
}
