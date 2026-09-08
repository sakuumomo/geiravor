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
        val djImage: String = "",
    )

    fun fromStatus(status: Status): Seen = Seen(
        isAfk = status.isAfkStream,
        djId = status.dj.id,
        djName = status.dj.name,
        djImage = status.dj.image,
    )

    fun artworkUrl(image: String): String? = LivePlaybackPolicy.djImageUrl(image)

    fun isHanyuu(name: String): Boolean {
        val fold = name.trim().lowercase()
        return fold == "hanyuu-sama" || fold == "hanyuu"
    }

    /** AFK stream or Hanyuu (`isafkstream` can lag a takeover). */
    fun isAfkDj(seen: Seen): Boolean = seen.isAfk || isHanyuu(seen.djName)

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
        val prevAfk = isAfkDj(previous)
        val nextAfk = isAfkDj(next)
        if (prevAfk && nextAfk) {
            return false
        }
        if (prevAfk != nextAfk) {
            return true
        }
        return previous.djId != next.djId || previous.djName != next.djName
    }

    fun body(next: Seen): String {
        val name = next.djName.trim()
        return if (isAfkDj(next)) {
            "${name.ifEmpty { "Hanyuu-sama" }} is back"
        } else {
            "${name.ifEmpty { "A DJ" }} is LIVE"
        }
    }

    fun shouldExplainDenied(needsGrant: Boolean, granted: Boolean): Boolean =
        needsGrant && !granted
}
