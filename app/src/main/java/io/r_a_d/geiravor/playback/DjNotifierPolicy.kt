package io.r_a_d.geiravor.playback

import uniffi.geiravor_core.Status

object DjNotifierPolicy {
    const val ENABLED_DEFAULT = false
    const val PERIOD_MINUTES = 15L
    const val CHANNEL_ID = "dj"
    const val NOTIFICATION_ID = 45
    const val WORK_NAME = "dj-notifier"
    const val TITLE = "r/a/dio"
    const val BATTERY =
        "The station has no push. This checks r-a-d.io about every 15 minutes and uses extra battery."
    const val NOTIFICATIONS_DENIED =
        "Notifications are off. Allow them so the DJ notifier can appear."

    data class Seen(
        val isAfk: Boolean,
        val djId: Long,
        val djName: String,
    )

    fun fromStatus(status: Status): Seen = Seen(
        isAfk = status.isAfkStream,
        djId = status.dj.id,
        djName = status.dj.name,
    )

    fun shouldNotify(
        enabled: Boolean,
        previous: Seen?,
        next: Seen,
        streamDown: Boolean,
    ): Boolean {
        if (!enabled || streamDown) {
            return false
        }
        if (previous == null) {
            return false
        }
        if (next.isAfk) {
            return false
        }
        if (previous.isAfk) {
            return true
        }
        return previous.djId != next.djId || previous.djName != next.djName
    }

    fun body(djName: String): String {
        val name = djName.trim()
        return if (name.isEmpty()) "A DJ is online" else "$name is online"
    }

    fun shouldExplainDenied(needsGrant: Boolean, granted: Boolean): Boolean =
        needsGrant && !granted
}
