package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {
    @Test
    fun gifIsImageMp4WebmAreVideo() {
        assertEquals(MediaPolicy.Kind.Image, MediaPolicy.kind("https://r-a-d.io/x.gif"))
        assertEquals(MediaPolicy.Kind.Image, MediaPolicy.kind("https://r-a-d.io/x.png"))
        assertEquals(MediaPolicy.Kind.Video, MediaPolicy.kind("https://r-a-d.io/x.mp4"))
        assertEquals(MediaPolicy.Kind.Video, MediaPolicy.kind("https://r-a-d.io/x.webm?foo=1"))
        assertTrue(MediaPolicy.isVideo("https://static.r-a-d.io/exci/clip.webm"))
        assertFalse(MediaPolicy.isVideo("https://static.r-a-d.io/exci/loop.gif"))
    }
}
