package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
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
}
