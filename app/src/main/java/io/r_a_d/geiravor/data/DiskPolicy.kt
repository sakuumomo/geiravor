package io.r_a_d.geiravor.data

object DiskPolicy {
    fun <T> changed(old: T?, new: T): Boolean = old != new
}
