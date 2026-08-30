package io.r_a_d.geiravor.ui

import android.content.res.Configuration
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

object ThemePolicy {
    const val OPT_OUT_DEFAULT = false
    const val USER_DEFAULT = RadioPacks.DEFAULT_DARK
    val SNIFF_INTERVAL: Duration = Duration.ofHours(1)
    val OUT_OF_WINDOW_CAP: Duration = Duration.ofHours(12)

    fun holidayWindow(date: LocalDate): String? {
        val month = date.monthValue
        val day = date.dayOfMonth
        return when {
            month == 10 && day >= 29 -> RadioPacks.HALLOWEEN
            month == 11 && day <= 1 -> RadioPacks.HALLOWEEN
            month == 12 && day <= 26 -> RadioPacks.CHRISTMAS
            month == 12 && day >= 27 -> RadioPacks.NEWYEARS
            month == 1 && day <= 3 -> RadioPacks.NEWYEARS
            else -> null
        }
    }

    /** New Years spans Dec–Jan; January uses the previous calendar year so the window is one key. */
    fun windowKey(date: LocalDate): String? {
        val window = holidayWindow(date) ?: return null
        val year = if (date.monthValue == 1) date.year - 1 else date.year
        return "$year-$window"
    }

    fun holidayPacksOnThisUi(uiModeType: Int): Boolean =
        uiModeType != Configuration.UI_MODE_TYPE_CAR

    fun clampUserPick(id: String): String =
        if (RadioPacks.PICKS.any { it.first == id }) id else RadioPacks.DEFAULT_DARK

    fun autoPack(userPick: String): String {
        val pick = clampUserPick(userPick)
        return if (pick in RadioPacks.HOLIDAYS) RadioPacks.DEFAULT_DARK else pick
    }

    /** Auto has no holiday tokens. Default light is day; everything else is night. */
    fun isNight(pack: String): Boolean =
        clampUserPick(pack) != RadioPacks.DEFAULT_LIGHT

    /** Night mode recreates the activity; skip when it is already applied. */
    fun nightModeChanged(applied: Boolean?, night: Boolean): Boolean = applied != night

    fun activePack(
        userPick: String,
        sniffed: String?,
        optOut: Boolean,
        date: LocalDate,
    ): String {
        val pick = clampUserPick(userPick)
        if (optOut || holidayWindow(date) == null) {
            return pick
        }
        if (sniffed != null && sniffed in RadioPacks.HOLIDAYS) {
            return sniffed
        }
        return pick
    }

    fun shouldSniff(
        date: LocalDate,
        now: java.time.Instant,
        lastSniff: java.time.Instant?,
        seenOnKey: String?,
        processStart: Boolean,
    ): Boolean {
        val key = windowKey(date) ?: return false
        if (processStart) {
            return true
        }
        if (seenOnKey == key) {
            return false
        }
        if (lastSniff == null) {
            return true
        }
        return !Duration.between(lastSniff, now).minus(SNIFF_INTERVAL).isNegative
    }

    fun nextWindowStart(date: LocalDate): LocalDate {
        if (holidayWindow(date) != null) {
            return date
        }
        val year = date.year
        return listOf(
            LocalDate.of(year, 10, 29),
            LocalDate.of(year, 12, 1),
            LocalDate.of(year, 12, 27),
            LocalDate.of(year + 1, 1, 1),
            LocalDate.of(year + 1, 10, 29),
        ).filter { it > date && holidayWindow(it) != null }.minOrNull()
            ?: LocalDate.of(year + 1, 10, 29)
    }

    fun delayMs(
        date: LocalDate,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Long {
        if (holidayWindow(date) != null) {
            return SNIFF_INTERVAL.toMillis()
        }
        val start = nextWindowStart(date).atStartOfDay(now.zone)
        val until = Duration.between(now, start).toMillis().coerceAtLeast(60_000L)
        return minOf(until, OUT_OF_WINDOW_CAP.toMillis())
    }
}
