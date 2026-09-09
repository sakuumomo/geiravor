package io.r_a_d.geiravor.compat

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Compat: FOREGROUND_SERVICE_IMMEDIATE. Remove when minSdk >= 31.
 */
fun Notification.Builder.setImmediateForeground(): Notification.Builder {
    if (Build.VERSION.SDK_INT >= 31) {
        setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
    }
    return this
}

/**
 * Compat: FGS mediaPlayback start. Typed FGS from API 29; required type from 34.
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
