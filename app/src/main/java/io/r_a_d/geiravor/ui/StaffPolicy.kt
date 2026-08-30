package io.r_a_d.geiravor.ui

import uniffi.geiravor_core.StaffMember

object StaffPolicy {
    val ROLES = listOf("staff", "dev", "dj")

    fun label(role: String): String =
        when (role) {
            "staff" -> "Staff"
            "dev" -> "Developers"
            "dj" -> "DJs"
            else -> role
        }

    fun groups(members: List<StaffMember>): List<Pair<String, List<StaffMember>>> =
        ROLES.map { role -> role to members.filter { it.role == role } }
}
