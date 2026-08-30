package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.StaffMember

class StaffPolicyTest {
    @Test
    fun rolesAreStaffDevDj() {
        assertEquals(listOf("staff", "dev", "dj"), StaffPolicy.ROLES)
        assertEquals("Staff", StaffPolicy.label("staff"))
        assertEquals("Developers", StaffPolicy.label("dev"))
        assertEquals("DJs", StaffPolicy.label("dj"))
    }

    @Test
    fun groupsKeepEmptyHeadingsAndOrder() {
        val vin = StaffMember(name = "Vin", image = "29.png", role = "dev")
        val groups = StaffPolicy.groups(listOf(vin))
        assertEquals(listOf("staff", "dev", "dj"), groups.map { it.first })
        assertEquals(emptyList<StaffMember>(), groups[0].second)
        assertEquals(listOf(vin), groups[1].second)
        assertEquals(emptyList<StaffMember>(), groups[2].second)
    }

    @Test
    fun djGridIsTwoOnPhoneFourOnWide() {
        assertEquals(2, StaffPolicy.columns(widthDp = 411, role = "dj"))
        assertEquals(4, StaffPolicy.columns(widthDp = 600, role = "dj"))
        assertEquals(2, StaffPolicy.columns(widthDp = 600, role = "staff"))
        assertEquals(2, StaffPolicy.columns(widthDp = 1280, role = "dev"))
        assertFalse(StaffPolicy.pairStaffAndDev(411))
        assertTrue(StaffPolicy.pairStaffAndDev(600))
    }

    @Test
    fun shortRowKeepsMembersTogether() {
        val a = StaffMember(name = "exci", image = "41.png", role = "staff")
        val b = StaffMember(name = "jii-san", image = "58.png", role = "staff")
        val rows = StaffPolicy.rows(listOf(a, b), columns = 4)
        assertEquals(1, rows.size)
        assertEquals(listOf(a, b), rows[0])
    }
}
