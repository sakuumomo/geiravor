package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingCopyTest {
    @Test
    fun listenersAreLabeled() {
        assertEquals("", NowPlayingCopy.listenersLabel(null))
        assertEquals("0 listeners", NowPlayingCopy.listenersLabel(0u))
        assertEquals("1 listener", NowPlayingCopy.listenersLabel(1u))
        assertEquals("42 listeners", NowPlayingCopy.listenersLabel(42u))
    }
}
