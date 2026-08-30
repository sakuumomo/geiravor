package io.r_a_d.geiravor.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

object SchedulePolicy {
    const val CONVERT_DEFAULT = false
    val SOURCE_ZONE: ZoneId = ZoneId.of("America/New_York")
    val WEEKDAYS = listOf(
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday",
        "Sunday",
    )

    private val token = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*[ap]m|\d{1,2}\s*[ap]m|\d{1,2}:\d{2})\s*(EST|EDT)?\b""",
    )

    fun dayOfWeek(weekday: String): DayOfWeek? =
        when (weekday) {
            "Monday" -> DayOfWeek.MONDAY
            "Tuesday" -> DayOfWeek.TUESDAY
            "Wednesday" -> DayOfWeek.WEDNESDAY
            "Thursday" -> DayOfWeek.THURSDAY
            "Friday" -> DayOfWeek.FRIDAY
            "Saturday" -> DayOfWeek.SATURDAY
            "Sunday" -> DayOfWeek.SUNDAY
            else -> null
        }

    fun isToday(weekday: String, today: DayOfWeek): Boolean = dayOfWeek(weekday) == today

    fun displayBody(
        body: String,
        weekday: String,
        convert: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        nowEt: LocalDate = LocalDate.now(SOURCE_ZONE),
    ): String {
        if (!convert) {
            return body
        }
        return token.replace(body) { match ->
            convertToken(match.value, weekday, zone, nowEt) ?: match.value
        }
    }

    internal fun convertToken(
        raw: String,
        weekday: String,
        zone: ZoneId,
        nowEt: LocalDate,
    ): String? {
        val parsed = parseToken(raw) ?: return null
        val etDay = dayOfWeek(weekday)?.let { nowEt.with(it) } ?: nowEt
        val et = ZonedDateTime.of(etDay, LocalTime.of(parsed.hour, parsed.minute), SOURCE_ZONE)
        val local = et.withZoneSameInstant(zone)
        var out = formatTime(local, raw, parsed.twelve)
        if (local.toLocalDate() != et.toLocalDate()) {
            out += " " + local.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)
        }
        return out
    }

    private data class Parsed(val hour: Int, val minute: Int, val twelve: Boolean)

    private fun parseToken(raw: String): Parsed? {
        val trimmed = raw.replace(Regex("(?i)\\s*(EST|EDT)$"), "").trim()
        val ampm = Regex("(?i)[ap]m").find(trimmed)
        val timePart = trimmed.replace(Regex("(?i)\\s*[ap]m"), "").trim()
        val hour: Int
        val minute: Int
        if (':' in timePart) {
            val parts = timePart.split(':')
            if (parts.size != 2) {
                return null
            }
            hour = parts[0].toIntOrNull() ?: return null
            minute = parts[1].toIntOrNull() ?: return null
        } else {
            hour = timePart.toIntOrNull() ?: return null
            minute = 0
        }
        if (minute !in 0..59) {
            return null
        }
        if (ampm != null) {
            if (hour !in 1..12) {
                return null
            }
            var hour24 = hour % 12
            if (ampm.value.equals("pm", ignoreCase = true)) {
                hour24 += 12
            }
            return Parsed(hour24, minute, twelve = true)
        }
        if (hour !in 0..23) {
            return null
        }
        return Parsed(hour, minute, twelve = false)
    }

    private fun formatTime(local: ZonedDateTime, original: String, twelve: Boolean): String {
        val minutes = local.minute
        val hadMinutes = original.contains(':')
        if (twelve) {
            var hour = local.hour % 12
            if (hour == 0) {
                hour = 12
            }
            val sample = Regex("(?i)[ap]m").find(original)?.value ?: "am"
            val period = if (local.hour < 12) "am" else "pm"
            val cased = caseLike(sample, period)
            val space = if (Regex("(?i)\\s[ap]m").containsMatchIn(original)) " " else ""
            val min = if (hadMinutes || minutes != 0) {
                ":${minutes.toString().padStart(2, '0')}"
            } else {
                ""
            }
            return "$hour$min$space$cased"
        }
        val padded = Regex("^\\d{2}:").containsMatchIn(original.trim())
        val hour = if (padded) {
            local.hour.toString().padStart(2, '0')
        } else {
            local.hour.toString()
        }
        return "$hour:${minutes.toString().padStart(2, '0')}"
    }

    private fun caseLike(sample: String, word: String): String =
        when {
            sample.uppercase(Locale.US) == sample && sample.lowercase(Locale.US) != sample ->
                word.uppercase(Locale.US)
            sample.lowercase(Locale.US) == sample -> word.lowercase(Locale.US)
            else -> word.replaceFirstChar { it.uppercase(Locale.US) }
        }
}
