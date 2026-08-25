package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamStatusTest {
    @Test
    fun keepsNpWhenStreamIsDown() {
        assertEquals(
            "Hirasawa Susumu - Gats",
            StreamStatus.headline("Hirasawa Susumu - Gats", streamDown = true),
        )
        assertFalse(StreamStatus.headline("Hirasawa Susumu - Gats", streamDown = true) == "Stream down")
    }

    @Test
    fun streamDownIsABannerNotAMagicTitle() {
        assertTrue(StreamStatus.showBanner(streamDown = true))
        assertFalse(StreamStatus.showBanner(streamDown = false))
        assertEquals("Stream down", StreamStatus.banner)
        assertEquals("Stream down", StreamStatus.headline(np = null, streamDown = true))
        assertEquals("…", StreamStatus.headline(np = null, streamDown = false))
    }
}
