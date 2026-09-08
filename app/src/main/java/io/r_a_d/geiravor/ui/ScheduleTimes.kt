package io.r_a_d.geiravor.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Paint-time EST → device local. Disk keeps the original. `docs/spec/schedule-staff.md`. */
object ScheduleTimes {
    fun rewrite(
        body: String,
        weekday: String,
        etZone: String,
        localZone: String,
        nowEpochSecs: Long,
    ): String {
        val et = ZoneId.of(etZone)
        val local = ZoneId.of(localZone)
        val now = Instant.ofEpochSecond(nowEpochSecs).atZone(local)
        val day = weekdayOf(weekday) ?: now.dayOfWeek
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val tok = matchClock(body, i)
            if (tok == null || looksLikeDate(body, i) || looksLikeUrl(body, i)) {
                out.append(body[i])
                i++
            } else {
                out.append(convert(tok, day, et, local, now))
                i = tok.end
            }
        }
        return out.toString()
    }

    private data class ClockTok(
        val end: Int,
        val hour: Int,
        val minute: Int,
        val h12: Boolean,
        val ampm: String?,
        val hadZone: Boolean,
    )

    private fun matchClock(body: String, i: Int): ClockTok? {
        if (i > 0 && (body[i - 1].isDigit() || body[i - 1] == '-' || body[i - 1] == '>')) {
            return null
        }
        val rest = body.substring(i)
        val m12s = Regex("^(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])(?:\\s*(EST|EDT))?", RegexOption.IGNORE_CASE)
            .find(rest)
        if (m12s != null) {
            val h = m12s.groupValues[1].toInt()
            val min = m12s.groupValues[2].toInt()
            if (h in 1..12 && min in 0..59) {
                return ClockTok(
                    i + m12s.value.length,
                    h,
                    min,
                    true,
                    m12s.groupValues[3],
                    m12s.groupValues[4].isNotEmpty(),
                )
            }
        }
        val m12 = Regex("^(\\d{1,2})\\s*([AaPp][Mm])(?:\\s*(EST|EDT))?", RegexOption.IGNORE_CASE)
            .find(rest)
        if (m12 != null) {
            val h = m12.groupValues[1].toInt()
            if (h in 1..12) {
                return ClockTok(
                    i + m12.value.length,
                    h,
                    0,
                    true,
                    m12.groupValues[2],
                    m12.groupValues[3].isNotEmpty(),
                )
            }
        }
        val m24 = Regex("^(\\d{1,2}):(\\d{2})(?:\\s*(EST|EDT))?", RegexOption.IGNORE_CASE).find(rest)
        if (m24 != null) {
            val h = m24.groupValues[1].toInt()
            val min = m24.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                return ClockTok(
                    i + m24.value.length,
                    h,
                    min,
                    false,
                    null,
                    m24.groupValues[3].isNotEmpty(),
                )
            }
        }
        return null
    }

    private fun looksLikeDate(body: String, i: Int): Boolean {
        if (i >= 4 && body[i] == '-') {
            return body.substring((i - 4).coerceAtLeast(0), i).all { it.isDigit() }
        }
        return false
    }

    private fun looksLikeUrl(body: String, i: Int): Boolean {
        val pre = body.substring(0, i).takeLast(8).lowercase()
        return pre.endsWith("http://") || pre.endsWith("https:/") || pre.endsWith("://")
    }

    private fun convert(
        tok: ClockTok,
        weekday: DayOfWeek,
        et: ZoneId,
        local: ZoneId,
        nowLocal: ZonedDateTime,
    ): String {
        var hour = tok.hour
        if (tok.h12) {
            val pm = tok.ampm!!.lowercase().startsWith("p")
            hour = when {
                tok.hour == 12 && !pm -> 0
                tok.hour != 12 && pm -> tok.hour + 12
                else -> tok.hour
            }
        }
        val todayEt = nowLocal.withZoneSameInstant(et)
        var etTime = todayEt.with(LocalTime.of(hour, tok.minute))
        var guard = 0
        while (etTime.dayOfWeek != weekday && guard < 8) {
            etTime = etTime.plusDays(1)
            guard++
        }
        val loc = etTime.withZoneSameInstant(local)
        val text = if (tok.h12) {
            val h12 = loc.hour % 12
            val h = if (h12 == 0) 12 else h12
            val ap = tok.ampm!!
            val minute = if (tok.minute == 0) "" else ":%02d".format(loc.minute)
            val upper = ap[0].isUpperCase()
            val bodyAmpm = when {
                loc.hour >= 12 && upper -> "PM"
                loc.hour >= 12 -> "pm"
                upper -> "AM"
                else -> "am"
            }
            "$h$minute$bodyAmpm"
        } else {
            "%d:%02d".format(loc.hour, loc.minute)
        }
        val extra = if (loc.dayOfWeek != weekday) {
            " " + loc.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        } else {
            ""
        }
        return text + extra
    }

    private fun weekdayOf(name: String): DayOfWeek? =
        when (name) {
            "Monday" -> DayOfWeek.MONDAY
            "Tuesday" -> DayOfWeek.TUESDAY
            "Wednesday" -> DayOfWeek.WEDNESDAY
            "Thursday" -> DayOfWeek.THURSDAY
            "Friday" -> DayOfWeek.FRIDAY
            "Saturday" -> DayOfWeek.SATURDAY
            "Sunday" -> DayOfWeek.SUNDAY
            else -> null
        }
}
