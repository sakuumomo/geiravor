package io.r_a_d.geiravor.compat

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
}
