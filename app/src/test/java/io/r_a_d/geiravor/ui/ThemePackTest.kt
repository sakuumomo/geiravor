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
        assertTrue(
            RadioPacks.channelLuma(RadioPacks.highlight(RadioPacks.newyears)) >
                RadioPacks.channelLuma(RadioPacks.newyears.blue),
        )
        assertTrue(
            RadioPacks.channelLuma(RadioPacks.highlight(RadioPacks.halloween)) >=
                RadioPacks.channelLuma(RadioPacks.halloween.blue),
        )
        assertEquals(RadioPacks.defaultDark.blue, RadioPacks.highlight(RadioPacks.defaultDark))
        assertEquals(RadioPacks.christmas.blue, RadioPacks.highlight(RadioPacks.christmas))
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
