package io.r_a_d.geiravor.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskPolicyTest {
    @Test
    fun firstWriteIsAChange() {
        assertTrue(DiskPolicy.changed(old = null, new = "blob"))
        assertTrue(DiskPolicy.changed(old = null, new = emptyList<Int>()))
    }

    @Test
    fun equalPayloadIsNotWritten() {
        assertFalse(DiskPolicy.changed("blob", "blob"))
        assertFalse(DiskPolicy.changed(listOf(1, 2), listOf(1, 2)))
        assertFalse(
            DiskPolicy.changed(
                FaveMembershipEntity("nick", "A - B", 1L),
                FaveMembershipEntity("nick", "A - B", 1L),
            ),
        )
    }

    @Test
    fun differentPayloadIsAChange() {
        assertTrue(DiskPolicy.changed("old", "new"))
        assertTrue(DiskPolicy.changed(listOf(1), listOf(1, 2)))
        assertTrue(
            DiskPolicy.changed(
                FaveMembershipEntity("nick", "A - B", 1L),
                FaveMembershipEntity("nick", "A - B", 2L),
            ),
        )
    }

    @Test
    fun membershipIgnoresRowOrder() {
        val a = FaveMembershipEntity("nick", "A - B", 1L)
        val b = FaveMembershipEntity("nick", "C - D", 2L)
        assertFalse(MembershipStore.changed(listOf(a, b), listOf(b, a)))
        assertTrue(MembershipStore.changed(listOf(a), listOf(a, b)))
    }
}
