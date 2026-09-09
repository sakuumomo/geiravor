package io.r_a_d.geiravor.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {
    @Test
    fun shouldRequestOnlyWhenNeededAndMissing() {
        assertTrue(Notifications.shouldRequest(true, false))
        assertFalse(Notifications.shouldRequest(true, true))
        assertFalse(Notifications.shouldRequest(false, false))
    }

    @Test
    fun deniedCopyIsVisibleWhenMissing() {
        assertEquals(Notifications.DENIED, Notifications.deniedCopy(false))
        assertEquals(null, Notifications.deniedCopy(true))
    }
}
