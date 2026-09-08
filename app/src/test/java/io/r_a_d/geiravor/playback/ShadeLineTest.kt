package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeLineTest {
    private val measure: (String) -> Float = { it.length.toFloat() }

    @Test
    fun blankArtistIsDjOnly() {
        assertEquals("Hanyuu-sama", ShadeLine.fit("  ", "Hanyuu-sama", 100f, measure))
    }

    @Test
    fun pipeStaysWhenArtistIsLong() {
        val out = ShadeLine.fit("A very long artist name indeed", "Hanyuu-sama", 22f, measure)
        assertTrue(out.contains(" | Hanyuu-sama"))
        assertTrue(out.contains("…"))
        assertFalse(out.endsWith("Hanyuu-sa"))
    }

    @Test
    fun shortArtistUncut() {
        assertEquals("Aimer | Hanyuu-sama", ShadeLine.fit("Aimer", "Hanyuu-sama", 80f, measure))
    }
}
