package io.r_a_d.geiravor.ui

/** Queue fave/unfave while one IRC session is in flight. `docs/spec/requests-faves.md`. */
object FaveTap {
    const val WINDOW_MAX = 3

    data class Job(val unfave: Boolean, val catalog: Long)

    fun accept(busy: Boolean, tapsInWindow: Int): Boolean =
        if (!busy) true else tapsInWindow < WINDOW_MAX

    fun afterAccept(busy: Boolean, tapsInWindow: Int): Int =
        if (!busy) 1 else (tapsInWindow + 1).coerceAtMost(WINDOW_MAX)
}
