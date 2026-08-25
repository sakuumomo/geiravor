package io.r_a_d.geiravor.ui

object SectionLayout {
    enum class SongsSection(val label: String) {
        LastPlayed("Last Played"),
        Queue("Queue"),
    }

    enum class SettingsSection(val label: String) {
        General("General"),
        Auto("Auto"),
    }

    fun songsSections(isAfkStream: Boolean): List<SongsSection> =
        if (SongListPolicy.showQueue(isAfkStream)) {
            listOf(SongsSection.LastPlayed, SongsSection.Queue)
        } else {
            listOf(SongsSection.LastPlayed)
        }

    fun clampSongsSection(selected: SongsSection, isAfkStream: Boolean): SongsSection {
        val allowed = songsSections(isAfkStream)
        return if (selected in allowed) selected else SongsSection.LastPlayed
    }

    fun settingsSections(): List<SettingsSection> =
        listOf(SettingsSection.General, SettingsSection.Auto)
}
