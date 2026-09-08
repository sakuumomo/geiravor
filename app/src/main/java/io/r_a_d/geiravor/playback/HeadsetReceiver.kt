package io.r_a_d.geiravor.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.GeiravorApp

/** Unplug stops Icecast. Plug may auto-start. */
class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY ->
                context.startService(PlaybackService.stopIntent(context))
            Intent.ACTION_HEADSET_PLUG -> {
                if (intent.getIntExtra("state", 0) != 1) return
                val app = context.applicationContext as? GeiravorApp ?: return
                if (app.ui.autoStartPlug) {
                    ContextCompat.startForegroundService(
                        context,
                        PlaybackService.playIntent(context),
                    )
                }
            }
        }
    }
}
