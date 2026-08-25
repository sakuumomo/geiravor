package io.r_a_d.geiravor.ui

data class NeighborTracks(
    val previous: String,
    val next: String,
)

object SongListPolicy {
    const val LIVE_DJ_NEXT = "???"
    const val MISSING = "—"

    fun showQueue(isAfkStream: Boolean): Boolean = isAfkStream

    fun caption(isRequest: Boolean, whenText: String): String =
        if (isRequest) "/r/ · $whenText" else whenText

    fun neighbors(
        lastPlayedMeta: String?,
        nextInQueueMeta: String?,
        isAfkStream: Boolean?,
    ): NeighborTracks {
        val previous = lastPlayedMeta ?: MISSING
        val next = when (isAfkStream) {
            true -> nextInQueueMeta ?: MISSING
            false -> LIVE_DJ_NEXT
            null -> MISSING
        }
        return NeighborTracks(previous, next)
    }
}
