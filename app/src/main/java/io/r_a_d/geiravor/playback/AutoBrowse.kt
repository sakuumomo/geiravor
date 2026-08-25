package io.r_a_d.geiravor.playback

import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status

data class BrowseNode(
    val id: String,
    val title: String,
    val playable: Boolean,
    val browsable: Boolean,
)

object AutoBrowse {
    const val ROOT = "root"
    const val NOW_PLAYING = "now"
    const val LAST_PLAYED = "lp"
    const val QUEUE = "queue"

    fun rootChildren(isAfkStream: Boolean): List<BrowseNode> {
        val nodes = mutableListOf(
            BrowseNode(NOW_PLAYING, "Now Playing", playable = true, browsable = false),
            BrowseNode(LAST_PLAYED, "Last Played", playable = false, browsable = true),
        )
        if (isAfkStream) {
            nodes.add(BrowseNode(QUEUE, "Queue", playable = false, browsable = true))
        }
        return nodes
    }

    fun children(parentId: String, status: Status?): List<BrowseNode> {
        return when (parentId) {
            ROOT -> rootChildren(status?.isAfkStream == true)
            LAST_PLAYED -> displayEntries("lp", status?.lastPlayed.orEmpty())
            QUEUE -> {
                if (status?.isAfkStream != true) {
                    emptyList()
                } else {
                    displayEntries("queue", status.queue)
                }
            }
            else -> emptyList()
        }
    }

    fun isLiveStream(mediaId: String): Boolean = mediaId == NOW_PLAYING

    fun subtitle(
        lastPlayedMeta: String?,
        nextInQueueMeta: String?,
        isAfkStream: Boolean?,
    ): String? {
        val parts = mutableListOf<String>()
        if (lastPlayedMeta != null) {
            parts.add("Prev: $lastPlayedMeta")
        }
        when (isAfkStream) {
            false -> parts.add("Next: ???")
            true -> if (nextInQueueMeta != null) {
                parts.add("Next: $nextInQueueMeta")
            }
            null -> Unit
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun displayEntries(prefix: String, entries: List<ListEntry>): List<BrowseNode> =
        entries.mapIndexed { index, entry ->
            BrowseNode(
                id = "$prefix:$index",
                title = entry.meta,
                playable = false,
                browsable = false,
            )
        }
}
