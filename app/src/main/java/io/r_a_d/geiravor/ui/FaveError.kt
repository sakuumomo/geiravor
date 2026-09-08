package io.r_a_d.geiravor.ui

/** Failures stay on screen until `np` changes, then they fade. `docs/spec/ui.md`. */
object FaveError {
    fun shouldFade(error: String?, oldNp: String?, newNp: String?): Boolean =
        !error.isNullOrEmpty() && oldNp != newNp
}
