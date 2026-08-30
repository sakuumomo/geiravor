package io.r_a_d.geiravor.playback

import io.r_a_d.geiravor.settings.SettingsPolicy
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status

data class BrowseNode(
    val id: String,
    val title: String,
    val playable: Boolean,
    val browsable: Boolean,
    val subtitle: String? = null,
)

data class AutoSettingsSnapshot(
    val vehicleOn: Boolean = false,
    val plugOn: Boolean = false,
    val versionName: String = "",
)

object AutoBrowse {
    const val ROOT = "root"
    const val NOW_PLAYING = "now"
    const val SONGS = "songs"
    const val LAST_PLAYED = "lp"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val SETTING_VEHICLE = "settings:vehicle"
    const val SETTING_PLUG = "settings:plug"
    const val SETTING_ABOUT = "settings:about"

    fun rootChildren(): List<BrowseNode> = listOf(
        BrowseNode(SONGS, "Songs", playable = false, browsable = true),
        BrowseNode(SETTINGS, "Settings", playable = false, browsable = true),
    )

    fun lookup(
        mediaId: String,
        status: Status?,
        settings: AutoSettingsSnapshot = AutoSettingsSnapshot(),
    ): BrowseNode? {
        if (isLiveStream(mediaId)) {
            val title = status?.np?.trim().orEmpty().ifEmpty { "r/a/dio" }
            return BrowseNode(
                NOW_PLAYING,
                title = title,
                playable = true,
                browsable = false,
            )
        }
        return listOf(ROOT, SONGS, LAST_PLAYED, QUEUE, SETTINGS)
            .asSequence()
            .flatMap { children(it, status, settings).asSequence() }
            .find { it.id == mediaId }
    }

    fun statusForBrowse(live: Status?, hydrated: Status?): Status? = live ?: hydrated

    fun children(
        parentId: String,
        status: Status?,
        settings: AutoSettingsSnapshot = AutoSettingsSnapshot(),
    ): List<BrowseNode> {
        return when (parentId) {
            ROOT -> rootChildren()
            SONGS -> songsChildren(status?.isAfkStream == true)
            LAST_PLAYED -> displayEntries("lp", status?.lastPlayed.orEmpty())
            QUEUE -> {
                if (status?.isAfkStream != true) {
                    emptyList()
                } else {
                    displayEntries("queue", status.queue)
                }
            }
            SETTINGS -> settingsChildren(settings)
            else -> emptyList()
        }
    }

    fun isSettingsToggle(mediaId: String): Boolean =
        mediaId == SETTING_VEHICLE || mediaId == SETTING_PLUG

    fun isLiveStream(mediaId: String): Boolean = mediaId == NOW_PLAYING

    fun isReferenceTap(mediaId: String): Boolean =
        mediaId == LAST_PLAYED ||
            mediaId == QUEUE ||
            mediaId == SONGS ||
            mediaId == SETTINGS ||
            mediaId == SETTING_ABOUT ||
            mediaId.startsWith("lp:") ||
            mediaId.startsWith("queue:")

    fun allowsPlayback(mediaId: String, uri: String?): Boolean {
        if (isDisplayOnly(mediaId) || mediaId == ROOT) {
            return false
        }
        return isLiveStream(mediaId) ||
            (mediaId == NOW_PLAYING && uri == LivePlaybackPolicy.STREAM_URL)
    }

    fun skipRedundantLiveSet(
        currentMediaId: String?,
        currentUri: String?,
        incoming: List<Pair<String?, String?>>,
        activelyPlaying: Boolean,
        metadataOnly: Boolean = false,
    ): Boolean {
        if (incoming.isEmpty()) {
            return false
        }
        if (!allowsPlayback(currentMediaId.orEmpty(), currentUri)) {
            return false
        }
        val incomingLive = incoming.all { (id, uri) -> allowsPlayback(id.orEmpty(), uri) }
        val incomingSettings = incoming.all { (id, _) -> isSettingsToggle(id.orEmpty()) }
        if (!incomingLive && !incomingSettings) {
            return false
        }
        if (metadataOnly && incomingLive && incoming.size == 1) {
            val (id, uri) = incoming[0]
            if (id == currentMediaId && uri == currentUri) {
                return false
            }
        }
        if (activelyPlaying) {
            return true
        }
        return incomingSettings
    }

    private fun isDisplayOnly(mediaId: String): Boolean =
        mediaId == SONGS ||
            mediaId == LAST_PLAYED ||
            mediaId == QUEUE ||
            mediaId == SETTINGS ||
            mediaId == SETTING_VEHICLE ||
            mediaId == SETTING_PLUG ||
            mediaId == SETTING_ABOUT ||
            mediaId.startsWith("lp:") ||
            mediaId.startsWith("queue:")

    private fun songsChildren(isAfkStream: Boolean): List<BrowseNode> {
        val nodes = mutableListOf(
            BrowseNode(LAST_PLAYED, "Last Played", playable = false, browsable = true),
        )
        if (isAfkStream) {
            nodes.add(BrowseNode(QUEUE, "Queue", playable = false, browsable = true))
        }
        return nodes
    }

    private fun settingsChildren(settings: AutoSettingsSnapshot): List<BrowseNode> = listOf(
        BrowseNode(
            SETTING_VEHICLE,
            "Auto-start in vehicle",
            playable = true,
            browsable = false,
            subtitle = onOff(settings.vehicleOn),
        ),
        BrowseNode(
            SETTING_PLUG,
            "Auto-start on plug",
            playable = true,
            browsable = false,
            subtitle = onOff(settings.plugOn),
        ),
        BrowseNode(
            SETTING_ABOUT,
            SettingsPolicy.ABOUT_NAME,
            playable = false,
            browsable = false,
            subtitle = aboutSubtitle(settings.versionName),
        ),
    )

    private fun onOff(on: Boolean): String = if (on) "On" else "Off"

    private fun aboutSubtitle(versionName: String): String =
        listOfNotNull(
            versionName.takeIf { it.isNotEmpty() },
            SettingsPolicy.ABOUT_LINE,
        ).joinToString(" · ")

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
