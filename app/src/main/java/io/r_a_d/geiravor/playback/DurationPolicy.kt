package io.r_a_d.geiravor.playback

object DurationPolicy {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 12 * 60

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun hours(totalMinutes: Int): Int = clampMinutes(totalMinutes) / 60

    fun minutePart(totalMinutes: Int): Int = clampMinutes(totalMinutes) % 60

    fun fromHoursMinutes(hours: Int, minutes: Int): Int {
        val h = hours.coerceAtLeast(0)
        val m = minutes.coerceIn(0, 59)
        return clampMinutes(h * 60 + m)
    }

    fun format(totalMinutes: Int): String {
        val n = clampMinutes(totalMinutes)
        val h = n / 60
        val m = n % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }
}
