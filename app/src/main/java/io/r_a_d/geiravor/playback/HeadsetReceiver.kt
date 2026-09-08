package io.r_a_d.geiravor.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/** Unplug stops Icecast. */
class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            context.startService(PlaybackService.stopIntent(context))
        }
    }
}
