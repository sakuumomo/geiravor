package io.r_a_d.geiravor.ui

import uniffi.geiravor_core.StaffMember

object StaffPolicy {
    val ROLES = listOf("staff", "dev", "dj")
    const val PHONE_COLUMNS = 2
    const val WIDE_COLUMNS = 4
    const val WIDE_MIN_WIDTH_DP = 600

    fun label(role: String): String =
        when (role) {
            "staff" -> "Staff"
            "dev" -> "Developers"
            "dj" -> "DJs"
            else -> role
        }

    fun groups(members: List<StaffMember>): List<Pair<String, List<StaffMember>>> =
        ROLES.map { role -> role to members.filter { it.role == role } }

    /** Live staff page: DJs `has-4-cols has-2-cols-mobile`. */
    fun columns(widthDp: Int, role: String): Int =
        if (role == "dj" && widthDp >= WIDE_MIN_WIDTH_DP) WIDE_COLUMNS else PHONE_COLUMNS

    /** Live `#notdjs`: Staff | Developers side by side from tablet width. */
    fun pairStaffAndDev(widthDp: Int): Boolean = widthDp >= WIDE_MIN_WIDTH_DP

    fun rows(members: List<StaffMember>, columns: Int): List<List<StaffMember>> {
        val cols = columns.coerceAtLeast(1)
        if (members.isEmpty()) {
            return emptyList()
        }
        return members.chunked(cols)
    }
}
