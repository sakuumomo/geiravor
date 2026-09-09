package io.r_a_d.geiravor.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.r_a_d.geiravor.GeiravorApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? GeiravorApp ?: return
        val pending = goAsync()
        AlarmScheduler.offMain {
            try {
                AlarmScheduler.schedule(context, app.core) {}
            } finally {
                pending.finish()
            }
        }
    }
}
