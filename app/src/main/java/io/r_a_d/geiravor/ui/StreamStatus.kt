package io.r_a_d.geiravor.ui

object StreamStatus {
    const val banner = "Stream down"

    fun headline(np: String?, streamDown: Boolean): String =
        np ?: if (streamDown) banner else "…"

    fun showBanner(streamDown: Boolean): Boolean = streamDown
}
