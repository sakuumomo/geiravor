package io.r_a_d.geiravor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.geiravor_core.StaffMember

class StaffStoreTest {
    @Test
    fun writeSkipsWhenPayloadMatches() {
        val members = listOf(
            StaffMember(name = "exci", image = "41.png", role = "staff"),
            StaffMember(name = "Vin", image = "29.png", role = "dev"),
        )
        val existing = StaffStore.fromMembers(members)
        assertNull(StaffStore.write(existing, members))
    }

    @Test
    fun writeWhenANameChanges() {
        val existing = StaffStore.fromMembers(
            listOf(StaffMember(name = "exci", image = "41.png", role = "staff")),
        )
        val incoming = listOf(StaffMember(name = "jii-san", image = "58.png", role = "staff"))
        val write = StaffStore.write(existing, incoming)
        assertEquals("jii-san", write!!.single().name)
        assertEquals("58.png", write.single().image)
    }
}
