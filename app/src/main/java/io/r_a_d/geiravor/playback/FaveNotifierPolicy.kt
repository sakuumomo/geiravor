package io.r_a_d.geiravor.playback

import uniffi.geiravor_core.Status

object FaveNotifierPolicy {
    const val ENABLED_DEFAULT = false
    const val CHANNEL_ID = "fave-on-air"
    const val NOTIFICATION_ID = 46
    const val TITLE = "r/a/dio"
    const val BATTERY =
        "The station has no push. This uses the same 15-minute check as the DJ notifier. It does not notify while this app is playing."
    const val NOTIFICATIONS_DENIED =
        "Notifications are off. Allow them so the fave notifier can appear."

    data class Seen(
        val trackId: Long,
        val np: String,
    )

    fun fromStatus(status: Status): Seen = Seen(
        trackId = FavePolicy.catalogTrackId(status.isAfkStream, status.trackId),
        np = status.np,
    )

    fun sameTrack(previous: Seen?, next: Seen): Boolean {
        if (previous == null) {
            return false
        }
        if (previous.trackId > 0L && next.trackId > 0L) {
            return previous.trackId == next.trackId
        }
        return previous.np.trim().equals(next.np.trim(), ignoreCase = true)
    }

    fun shouldNotify(
        enabled: Boolean,
        previous: Seen?,
        next: Seen,
        isMember: Boolean,
        streamDown: Boolean,
        playing: Boolean,
    ): Boolean {
        if (!enabled || streamDown || playing || !isMember || previous == null) {
            return false
        }
        return !sameTrack(previous, next)
    }

    fun body(np: String): String = np.trim().ifEmpty { "A favorite is playing" }

    fun workerNeeded(djOn: Boolean, faveOn: Boolean): Boolean = djOn || faveOn

    fun shouldExplainDenied(needsGrant: Boolean, granted: Boolean): Boolean =
        needsGrant && !granted
}
