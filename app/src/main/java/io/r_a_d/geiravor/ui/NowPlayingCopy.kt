package io.r_a_d.geiravor.ui

/** Now Playing copy. `docs/spec/ui.md`. */
object NowPlayingCopy {
    fun listenersLabel(count: UInt?): String {
        val n = count ?: return ""
        return if (n == 1u) "1 listener" else "$n listeners"
    }
}
