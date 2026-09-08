package io.r_a_d.geiravor.ui

import uniffi.geiravor_core.RoleColor

/** Staff grid from 600dp. `docs/spec/schedule-staff.md`. */
object StaffLayout {
    const val WIDE_DP = 600

    fun sideBySide(smallestWidthDp: Int): Boolean = smallestWidthDp >= WIDE_DP

    fun columns(role: RoleColor, smallestWidthDp: Int): Int =
        if (role == RoleColor.DJ && smallestWidthDp >= WIDE_DP) 4 else 2
}
