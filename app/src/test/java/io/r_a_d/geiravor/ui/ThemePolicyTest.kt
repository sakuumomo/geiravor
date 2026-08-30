package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ThemePolicyTest {
    @Test
    fun windowsAreDeviceLocalInclusive() {
        assertEquals(RadioPacks.HALLOWEEN, ThemePolicy.holidayWindow(LocalDate.of(2026, 10, 29)))
        assertEquals(RadioPacks.HALLOWEEN, ThemePolicy.holidayWindow(LocalDate.of(2026, 11, 1)))
        assertNull(ThemePolicy.holidayWindow(LocalDate.of(2026, 11, 2)))
        assertEquals(RadioPacks.CHRISTMAS, ThemePolicy.holidayWindow(LocalDate.of(2026, 12, 1)))
        assertEquals(RadioPacks.CHRISTMAS, ThemePolicy.holidayWindow(LocalDate.of(2026, 12, 26)))
        assertEquals(RadioPacks.NEWYEARS, ThemePolicy.holidayWindow(LocalDate.of(2026, 12, 27)))
        assertEquals(RadioPacks.NEWYEARS, ThemePolicy.holidayWindow(LocalDate.of(2027, 1, 3)))
        assertNull(ThemePolicy.holidayWindow(LocalDate.of(2027, 1, 4)))
        assertNull(ThemePolicy.holidayWindow(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun windowKeyIncludesYearAndSpansNewYears() {
        assertEquals("2026-halloween", ThemePolicy.windowKey(LocalDate.of(2026, 10, 29)))
        assertEquals("2026-christmas", ThemePolicy.windowKey(LocalDate.of(2026, 12, 25)))
        assertEquals("2026-newyears", ThemePolicy.windowKey(LocalDate.of(2026, 12, 27)))
        assertEquals("2026-newyears", ThemePolicy.windowKey(LocalDate.of(2027, 1, 3)))
        assertEquals("2027-halloween", ThemePolicy.windowKey(LocalDate.of(2027, 10, 29)))
        assertNull(ThemePolicy.windowKey(LocalDate.of(2026, 6, 15)))
        assertFalse(
            ThemePolicy.windowKey(LocalDate.of(2026, 12, 25)) ==
                ThemePolicy.windowKey(LocalDate.of(2027, 12, 25)),
        )
    }

    @Test
    fun sniffOnlyInsideWindow() {
        val now = Instant.parse("2026-06-15T12:00:00Z")
        assertFalse(
            ThemePolicy.shouldSniff(
                date = LocalDate.of(2026, 6, 15),
                now = now,
                lastSniff = null,
                seenOnKey = null,
                processStart = true,
            ),
        )
        assertTrue(
            ThemePolicy.shouldSniff(
                date = LocalDate.of(2026, 12, 25),
                now = now,
                lastSniff = null,
                seenOnKey = null,
                processStart = true,
            ),
        )
    }

    @Test
    fun lastYearsSeenOnDoesNotBlockThisYear() {
        val date = LocalDate.of(2027, 12, 25)
        val t0 = Instant.parse("2027-12-25T00:00:00Z")
        assertTrue(
            ThemePolicy.shouldSniff(
                date = date,
                now = t0.plusSeconds(60 * 60),
                lastSniff = t0,
                seenOnKey = "2026-christmas",
                processStart = false,
            ),
        )
        assertFalse(
            ThemePolicy.shouldSniff(
                date = date,
                now = t0.plusSeconds(60 * 60),
                lastSniff = t0,
                seenOnKey = "2027-christmas",
                processStart = false,
            ),
        )
    }

    @Test
    fun hourlyUntilOnThenStopExceptProcessStart() {
        val date = LocalDate.of(2026, 12, 25)
        val key = "2026-christmas"
        val t0 = Instant.parse("2026-12-25T00:00:00Z")
        val t30m = t0.plusSeconds(30 * 60)
        val t1h = t0.plusSeconds(60 * 60)
        assertFalse(
            ThemePolicy.shouldSniff(date, t30m, lastSniff = t0, seenOnKey = null, processStart = false),
        )
        assertTrue(
            ThemePolicy.shouldSniff(date, t1h, lastSniff = t0, seenOnKey = null, processStart = false),
        )
        assertFalse(
            ThemePolicy.shouldSniff(date, t1h, lastSniff = t0, seenOnKey = key, processStart = false),
        )
        assertTrue(
            ThemePolicy.shouldSniff(date, t1h, lastSniff = t0, seenOnKey = key, processStart = true),
        )
    }

    @Test
    fun holidayNeverAppliesOutsideWindow() {
        val june = LocalDate.of(2026, 6, 15)
        assertEquals(
            RadioPacks.DEFAULT_DARK,
            ThemePolicy.activePack(
                RadioPacks.DEFAULT_DARK,
                RadioPacks.CHRISTMAS,
                optOut = false,
                date = june,
            ),
        )
        assertEquals(
            RadioPacks.CHRISTMAS,
            ThemePolicy.activePack(
                RadioPacks.DEFAULT_DARK,
                RadioPacks.CHRISTMAS,
                optOut = false,
                date = LocalDate.of(2026, 12, 25),
            ),
        )
        assertEquals(
            RadioPacks.DEFAULT_LIGHT,
            ThemePolicy.activePack(
                RadioPacks.DEFAULT_LIGHT,
                RadioPacks.CHRISTMAS,
                optOut = true,
                date = LocalDate.of(2026, 12, 25),
            ),
        )
        assertEquals(
            RadioPacks.DEFAULT_DARK,
            ThemePolicy.activePack(
                RadioPacks.DEFAULT_DARK,
                "suzu",
                optOut = false,
                date = LocalDate.of(2026, 12, 25),
            ),
        )
        assertEquals(RadioPacks.DEFAULT_DARK, ThemePolicy.clampUserPick("suzu"))
        assertEquals(RadioPacks.CHRISTMAS, ThemePolicy.clampUserPick(RadioPacks.CHRISTMAS))
        assertEquals(RadioPacks.HALLOWEEN, ThemePolicy.clampUserPick(RadioPacks.HALLOWEEN))
        assertEquals(
            RadioPacks.HALLOWEEN,
            ThemePolicy.activePack(
                RadioPacks.HALLOWEEN,
                sniffed = null,
                optOut = true,
                date = LocalDate.of(2026, 6, 15),
            ),
        )
        assertEquals(RadioPacks.DEFAULT_DARK, ThemePolicy.autoPack(RadioPacks.CHRISTMAS))
        assertEquals(RadioPacks.DEFAULT_LIGHT, ThemePolicy.autoPack(RadioPacks.DEFAULT_LIGHT))
        assertFalse(ThemePolicy.OPT_OUT_DEFAULT)
        assertFalse(ThemePolicy.isNight(RadioPacks.DEFAULT_LIGHT))
        assertTrue(ThemePolicy.isNight(RadioPacks.DEFAULT_DARK))
        assertTrue(ThemePolicy.isNight(RadioPacks.CHRISTMAS))
        assertTrue(ThemePolicy.isNight(RadioPacks.HALLOWEEN))
        assertTrue(ThemePolicy.isNight(RadioPacks.NEWYEARS))
        val holidayOnLight = ThemePolicy.activePack(
            RadioPacks.DEFAULT_LIGHT,
            RadioPacks.HALLOWEEN,
            optOut = false,
            date = LocalDate.of(2026, 10, 31),
        )
        assertEquals(RadioPacks.HALLOWEEN, holidayOnLight)
        assertEquals(RadioPacks.DEFAULT_DARK, ThemePolicy.autoPack(holidayOnLight))
        assertTrue(ThemePolicy.isNight(holidayOnLight))
    }

    @Test
    fun nextWindowAndDelay() {
        assertEquals(LocalDate.of(2026, 10, 29), ThemePolicy.nextWindowStart(LocalDate.of(2026, 6, 15)))
        assertEquals(LocalDate.of(2026, 12, 1), ThemePolicy.nextWindowStart(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2027, 10, 29), ThemePolicy.nextWindowStart(LocalDate.of(2027, 1, 4)))
        val june = LocalDate.of(2026, 6, 15)
        val now = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(ThemePolicy.OUT_OF_WINDOW_CAP.toMillis(), ThemePolicy.delayMs(june, now))
        assertEquals(
            ThemePolicy.SNIFF_INTERVAL.toMillis(),
            ThemePolicy.delayMs(LocalDate.of(2026, 12, 25), now),
        )
    }

    @Test
    fun holidayPacksStayPhoneOnly() {
        assertTrue(ThemePolicy.holidayPacksOnThisUi(android.content.res.Configuration.UI_MODE_TYPE_NORMAL))
        assertFalse(ThemePolicy.holidayPacksOnThisUi(android.content.res.Configuration.UI_MODE_TYPE_CAR))
    }
}
