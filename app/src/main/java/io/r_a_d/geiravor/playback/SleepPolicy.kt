package io.r_a_d.geiravor.playback

object SleepPolicy {
    const val ENABLED_DEFAULT = false
    const val DEFAULT_MINUTES = 30
    val DURATION_CHOICES = listOf(15, 30, 45, 60, 90)
    const val FADE_MS = 15_000L
    const val TICK_SLOW_MS = 1_000L
    const val TICK_FADE_MS = 250L

    fun clampMinutes(minutes: Int): Int =
        DURATION_CHOICES.minByOrNull { kotlin.math.abs(it - minutes) } ?: DEFAULT_MINUTES

    fun nextDurationChoice(current: Int): Int {
        val i = DURATION_CHOICES.indexOf(clampMinutes(current))
        return DURATION_CHOICES[(i + 1) % DURATION_CHOICES.size]
    }

    fun durationMillis(minutes: Int): Long = clampMinutes(minutes) * 60_000L

    fun formatMinutes(minutes: Int): String = "${clampMinutes(minutes)} min"

    fun endsAtMillis(nowMillis: Long, minutes: Int): Long =
        nowMillis + durationMillis(minutes)

    fun remainingMillis(endsAtMillis: Long, nowMillis: Long): Long =
        (endsAtMillis - nowMillis).coerceAtLeast(0L)

    fun fadeMultiplier(remainingMs: Long, fadeMs: Long = FADE_MS): Float {
        if (remainingMs >= fadeMs) {
            return 1f
        }
        if (remainingMs <= 0L || fadeMs <= 0L) {
            return 0f
        }
        return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    fun outputGain(userGain: Float, remainingMs: Long): Float =
        userGain.coerceIn(0f, 1f) * fadeMultiplier(remainingMs)

    fun shouldStop(enabled: Boolean, remainingMs: Long): Boolean =
        enabled && remainingMs <= 0L

    fun shouldCancelOnStop(enabled: Boolean, endsAtMillis: Long, nowMillis: Long): Boolean =
        enabled && endsAtMillis > nowMillis

    fun tickMs(remainingMs: Long): Long =
        if (remainingMs <= FADE_MS) TICK_FADE_MS else TICK_SLOW_MS
}
