package io.r_a_d.geiravor.playback

import java.time.Instant
import java.time.ZoneId

object AlarmPolicy {
    const val ENABLED_DEFAULT = false
    const val DEFAULT_HOUR = 8
    const val DEFAULT_MINUTE = 0
    const val SNOOZE_ENABLED_DEFAULT = true
    const val DEFAULT_SNOOZE_MINUTES = 10
    val SNOOZE_CHOICES = listOf(5, 10, 15, 30)
    const val EXACT_DENIED =
        "Exact alarms are off. Allow them in system settings so the alarm can fire on time."
    const val CHANNEL_ID = "alarm"
    const val NOTIFICATION_ID = 44
    const val ACTION_FIRE = "io.r_a_d.geiravor.ALARM_FIRE"
    const val ACTION_STOP = "io.r_a_d.geiravor.ALARM_STOP"
    const val ACTION_SNOOZE = "io.r_a_d.geiravor.ALARM_SNOOZE"
    const val FALLBACK_WAIT_MS = 8_000L

    fun clampHour(hour: Int): Int = hour.coerceIn(0, 23)

    fun clampMinute(minute: Int): Int = minute.coerceIn(0, 59)

    fun clampSnoozeMinutes(minutes: Int): Int =
        SNOOZE_CHOICES.minByOrNull { kotlin.math.abs(it - minutes) } ?: DEFAULT_SNOOZE_MINUTES

    fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(clampHour(hour), clampMinute(minute))

    fun nextTriggerMillis(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var at = now
            .withHour(clampHour(hour))
            .withMinute(clampMinute(minute))
            .withSecond(0)
            .withNano(0)
        if (!at.isAfter(now)) {
            at = at.plusDays(1)
        }
        return at.toInstant().toEpochMilli()
    }

    fun snoozeDelayMillis(minutes: Int): Long = clampSnoozeMinutes(minutes) * 60_000L

    fun nextSnoozeChoice(current: Int): Int {
        val i = SNOOZE_CHOICES.indexOf(clampSnoozeMinutes(current))
        return SNOOZE_CHOICES[(i + 1) % SNOOZE_CHOICES.size]
    }

    fun ringingActions(snoozeEnabled: Boolean): List<String> =
        if (snoozeEnabled) listOf("Stop", "Snooze") else listOf("Stop")

    fun shouldPlayFallback(streamPlaying: Boolean): Boolean = !streamPlaying

    fun shouldExplainExactDenied(needsGrant: Boolean, canSchedule: Boolean): Boolean =
        needsGrant && !canSchedule
}
