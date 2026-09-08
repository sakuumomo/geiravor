package io.r_a_d.geiravor.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.ThemePack

class TokensTest {
    @Test
    fun defaultDarkHasNoWallpaper() {
        val t = tokens(ThemePack.DEFAULT_DARK)
        assertNull(t.wallpaper)
        assertFalse(t.glass)
    }

    @Test
    fun holidaysAreDistinctAndHaveWallpapers() {
        val c = tokens(ThemePack.CHRISTMAS)
        val h = tokens(ThemePack.HALLOWEEN)
        val n = tokens(ThemePack.NEW_YEARS)
        assertNotNull(c.wallpaper)
        assertNotNull(h.wallpaper)
        assertNotNull(n.wallpaper)
        assertNotEquals(c.accent, h.accent)
        assertNotEquals(h.accent, n.accent)
        assertTrue(h.glass)
        assertTrue(n.glass)
        assertFalse(c.glass)
    }

    @Test
    fun defaultLightIsNotInvertedDark() {
        val dark = tokens(ThemePack.DEFAULT_DARK)
        val light = tokens(ThemePack.DEFAULT_LIGHT)
        assertNotEquals(dark.accent, light.accent)
        assertFalse(light.glass)
        assertNull(light.wallpaper)
    }

    @Test
    fun hslSteelBlueMatchesSpec() {
        val c = hsl(208f, 27f, 39f)
        assertEquals(73, (c.red * 255).toInt())
        assertEquals(101, (c.green * 255).toInt())
        assertEquals(126, (c.blue * 255).toInt())
    }
}
