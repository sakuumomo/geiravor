package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.RoleColor

class StaffLayoutTest {
    @Test
    fun phoneStacksAndTwoAcross() {
        assertFalse(StaffLayout.sideBySide(599))
        assertEquals(2, StaffLayout.columns(RoleColor.STAFF, 411))
        assertEquals(2, StaffLayout.columns(RoleColor.DEV, 411))
        assertEquals(2, StaffLayout.columns(RoleColor.DJ, 411))
    }

    @Test
    fun tabletStaffDevSideBySideDjsFourAcross() {
        assertTrue(StaffLayout.sideBySide(600))
        assertEquals(2, StaffLayout.columns(RoleColor.STAFF, 600))
        assertEquals(2, StaffLayout.columns(RoleColor.DEV, 840))
        assertEquals(4, StaffLayout.columns(RoleColor.DJ, 600))
    }
}
