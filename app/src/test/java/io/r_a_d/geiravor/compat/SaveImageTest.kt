package io.r_a_d.geiravor.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveImageTest {
    @Test
    fun nameStripsQueryAndForcesJpeg() {
        assertEquals("pic.jpg", threadSaveName("https://static.r-a-d.io/pic.gif?x=1"))
        assertEquals("thread.jpg", threadSaveName("https://static.r-a-d.io/"))
    }
}
