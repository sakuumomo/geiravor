package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadPolicyTest {
    @Test
    fun hiddenOnHanyuuEvenIfUrlLeftOver() {
        assertEquals(
            ThreadPolicy.Kind.Hidden,
            ThreadPolicy.kind(isAfkStream = true, thread = "https://boards.4chan.org/a/thread/1"),
        )
        assertEquals(ThreadPolicy.Kind.Hidden, ThreadPolicy.kind(isAfkStream = true, thread = "none"))
        assertEquals(ThreadPolicy.Kind.Hidden, ThreadPolicy.kind(isAfkStream = false, thread = null))
        assertEquals(ThreadPolicy.Kind.Hidden, ThreadPolicy.kind(isAfkStream = false, thread = "none"))
        assertEquals(ThreadPolicy.Kind.Hidden, ThreadPolicy.kind(isAfkStream = false, thread = "  "))
    }

    @Test
    fun imagePrefixAndImageUrlEmbed() {
        assertEquals(
            ThreadPolicy.Kind.Image,
            ThreadPolicy.kind(isAfkStream = false, thread = "image:https://example.com/pic.png"),
        )
        assertEquals(
            "https://example.com/pic.png",
            ThreadPolicy.imageUrl("image:https://example.com/pic.png"),
        )
        assertEquals(
            ThreadPolicy.Kind.Image,
            ThreadPolicy.kind(isAfkStream = false, thread = "https://i.imgur.com/x.jpg"),
        )
        assertEquals(
            "https://r-a-d.io/api/dj-image/7-abc.png",
            ThreadPolicy.imageUrl("https://r-a-d.io/api/dj-image/7-abc.png"),
        )
    }

    @Test
    fun otherHttpsIsABrowserLink() {
        val url = "https://boards.4chan.org/a/thread/1"
        assertEquals(ThreadPolicy.Kind.Link, ThreadPolicy.kind(isAfkStream = false, thread = url))
        assertEquals(url, ThreadPolicy.linkUrl(url))
        assertNull(ThreadPolicy.imageUrl(url))
    }
}
