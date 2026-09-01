package io.r_a_d.geiravor.ui

import io.r_a_d.geiravor.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackTest {
    @Test
    fun defaultLightIsBulmaLightNotAnInvert() {
        val light = RadioPacks.defaultLight
        val dark = RadioPacks.defaultDark
        assertNotEquals(dark.blue, light.blue)
        assertTrue(light.background.red > 0.9f)
        assertTrue(dark.background.red < 0.2f)
        assertTrue(light.text.red < 0.3f)
        assertTrue(dark.text.red > 0.9f)
    }

    @Test
    fun holidayPacksDifferByAccentAndWallpaper() {
        assertEquals(R.drawable.wallpaper_christmas, RadioPacks.christmas.wallpaper)
        assertEquals(R.drawable.wallpaper_halloween, RadioPacks.halloween.wallpaper)
        assertEquals(R.drawable.wallpaper_newyears, RadioPacks.newyears.wallpaper)
        assertNotEquals(RadioPacks.christmas.blue, RadioPacks.halloween.blue)
        assertNotEquals(RadioPacks.halloween.blue, RadioPacks.newyears.blue)
        assertNotEquals(RadioPacks.christmas.surface, RadioPacks.halloween.surface)
        assertEquals(RadioPacks.halloween.surface, RadioPacks.newyears.surface)
        assertNull(RadioPacks.defaultDark.wallpaper)
        assertNull(RadioPacks.defaultLight.wallpaper)
        assertFalse(RadioPacks.christmas.glass)
        assertTrue(RadioPacks.halloween.glass)
        assertTrue(RadioPacks.newyears.glass)
        assertEquals(0.5f, RadioPacks.halloween.surface.alpha, 0.02f)
        assertEquals(0.5f, RadioPacks.newyears.surface.alpha, 0.02f)
        assertTrue(RadioPacks.christmas.surface.alpha >= 0.94f)
        assertTrue(RadioPacks.channelLuma(RadioPacks.christmas.onBackground) > 0.9f)
        assertTrue(RadioPacks.channelLuma(RadioPacks.christmas.text) < 0.4f)
        assertEquals(0, RadioPacks.paneGutterDp(RadioPacks.defaultDark))
        assertEquals(0, RadioPacks.paneGutterDp(RadioPacks.defaultLight))
        assertEquals(16, RadioPacks.paneGutterDp(RadioPacks.halloween))
        assertEquals(16, RadioPacks.paneGutterDp(RadioPacks.christmas))
        assertEquals(16, RadioPacks.panePadDp(RadioPacks.defaultDark))
        assertEquals(16, RadioPacks.panePadDp(RadioPacks.halloween))
        assertEquals(
            androidx.compose.ui.graphics.Color.Transparent,
            RadioPacks.cardFill(RadioPacks.halloween, nested = true),
        )
        assertEquals(
            RadioPacks.halloween.surface,
            RadioPacks.film(RadioPacks.halloween),
        )
        assertTrue(RadioPacks.film(RadioPacks.christmas).alpha >= 0.90f)
        assertEquals(RadioPacks.defaultDark.surface, RadioPacks.film(RadioPacks.defaultDark))
        assertFalse(RadioPacks.frost(nested = true))
        assertTrue(RadioPacks.frost(nested = false))
        assertTrue(
            RadioPacks.channelLuma(RadioPacks.highlight(RadioPacks.newyears)) >= 0.85f,
        )
        assertTrue(
            RadioPacks.channelLuma(RadioPacks.highlight(RadioPacks.halloween)) >= 0.85f,
        )
        assertEquals(RadioPacks.defaultDark.blue, RadioPacks.highlight(RadioPacks.defaultDark))
        assertEquals(RadioPacks.christmas.blue, RadioPacks.highlight(RadioPacks.christmas))
        assertTrue(RadioPacks.selectionWash(RadioPacks.christmas) >= 0.38f)
        assertTrue(RadioPacks.selectionWash(RadioPacks.newyears) >= 0.78f)
        assertTrue(RadioPacks.selectionWash(RadioPacks.halloween) >= 0.78f)
        assertTrue(RadioPacks.hoverAlpha(RadioPacks.halloween) >= 0.32f)
        assertTrue(RadioPacks.hoverAlpha(RadioPacks.newyears) >= 0.32f)
        assertTrue(RadioPacks.hoverAlpha(RadioPacks.defaultDark) <= 0.10f)
        assertTrue(
            RadioPacks.channelLuma(RadioPacks.christmas.muted) >
                RadioPacks.channelLuma(RadioPacks.christmas.text),
        )
        assertTrue(RadioPacks.chipIdle(RadioPacks.halloween).alpha >= 0.12f)
        assertTrue(RadioPacks.chipIdle(RadioPacks.halloween) != RadioPacks.halloween.surface)
        assertTrue(RadioPacks.dialogSurface(RadioPacks.halloween).alpha >= 0.90f)
        assertEquals(
            listOf(
                RadioPacks.DEFAULT_DARK,
                RadioPacks.DEFAULT_LIGHT,
                RadioPacks.CHRISTMAS,
                RadioPacks.HALLOWEEN,
                RadioPacks.NEWYEARS,
            ),
            RadioPacks.PICKS.map { it.first },
        )
    }
}
