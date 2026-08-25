package io.r_a_d.geiravor.compat

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Compat: FGS mediaPlayback start. Remove when minSdk >= 34.
 */
fun Service.startMediaPlaybackForeground(id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= 29) {
        startForeground(
            id,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    } else {
        @Suppress("DEPRECATION")
        startForeground(id, notification)
    }
}
