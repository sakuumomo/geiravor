package io.r_a_d.geiravor.ui

/** Queue fave/unfave while one IRC session is in flight. `docs/spec/requests-faves.md`. */
object FavesPolicy {
    fun dropOverlap(prev: List<uniffi.geiravor_core.FaveRow>, last: List<uniffi.geiravor_core.FaveRow>) =
        last.filter { row ->
            prev.none { it.tracksId == row.tracksId && row.tracksId != 0L }
        }
}

object FaveTap {
    const val WINDOW_MAX = 3

    data class Job(
        val unfave: Boolean,
        val catalog: Long,
        val np: String,
        val isAfk: Boolean,
        val trackId: Long,
    )

    fun accept(busy: Boolean, tapsInWindow: Int): Boolean =
        if (!busy) true else tapsInWindow < WINDOW_MAX

    fun afterAccept(busy: Boolean, tapsInWindow: Int): Int =
        if (!busy) 1 else (tapsInWindow + 1).coerceAtMost(WINDOW_MAX)
}
