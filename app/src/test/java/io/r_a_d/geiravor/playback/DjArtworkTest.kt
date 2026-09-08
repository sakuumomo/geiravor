package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DjArtworkTest {
    @Test
    fun stillBoundsFlattenAnimatedDrawablesToAFitStill() {
        assertEquals(512 to 512, DjArtwork.stillBounds(0, 0))
        assertEquals(512 to 512, DjArtwork.stillBounds(-1, 64))
        assertEquals(100 to 40, DjArtwork.stillBounds(100, 40))
        assertEquals(512 to 256, DjArtwork.stillBounds(1024, 512))
        assertEquals(256 to 512, DjArtwork.stillBounds(512, 1024))
    }

    @Test
    fun stillFromBitmapDropsMissing() {
        assertNull(DjArtwork.stillFromBitmap(null))
    }
}
