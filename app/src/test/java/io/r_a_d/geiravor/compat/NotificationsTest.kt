package io.r_a_d.geiravor.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {
    @Test
    fun requestsOnlyWhenRuntimePermissionMissing() {
        assertTrue(Notifications.shouldRequest(needsRuntimePermission = true, alreadyGranted = false))
        assertFalse(Notifications.shouldRequest(needsRuntimePermission = true, alreadyGranted = true))
        assertFalse(Notifications.shouldRequest(needsRuntimePermission = false, alreadyGranted = false))
    }
}
