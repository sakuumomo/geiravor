package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcyTagsTest {
    @Test
    fun parsesIcyTagsKey() {
        val raw = "StreamTitle='A - B';Icy-Tags='yuru yuri';StreamUrl='';"
        assertEquals(listOf("yuru", "yuri"), IcyTags.parse(raw))
    }

    @Test
    fun ignoresStreamTitle() {
        assertTrue(IcyTags.parse("StreamTitle='not tags';").isEmpty())
    }

    @Test
    fun apiTagsWinOverIcy() {
        assertEquals(listOf("api"), IcyTags.shown(listOf("api"), listOf("icy")))
        assertEquals(listOf("icy"), IcyTags.shown(emptyList(), listOf("icy")))
    }
}
