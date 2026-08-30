package io.r_a_d.geiravor.ui

object SectionLayout {
    enum class SongsSection(val label: String) {
        LastPlayed("Last Played"),
        Queue("Queue"),
        Request("Request"),
        Favorites("Favorites"),
    }

    enum class BoardSection(val label: String) {
        News("News"),
        Schedule("Schedule"),
        Staff("Staff"),
    }

    fun boardSections(): List<BoardSection> = BoardSection.entries.toList()

    enum class SettingsSection(val label: String) {
        General("General"),
        Auto("Auto"),
        Connection("Connection"),
        Alerts("Alerts"),
    }

    fun songsSections(isAfkStream: Boolean): List<SongsSection> =
        if (SongListPolicy.showQueue(isAfkStream)) {
            listOf(
                SongsSection.LastPlayed,
                SongsSection.Queue,
                SongsSection.Request,
                SongsSection.Favorites,
            )
        } else {
            listOf(
                SongsSection.LastPlayed,
                SongsSection.Request,
                SongsSection.Favorites,
            )
        }

    fun clampSongsSection(selected: SongsSection, isAfkStream: Boolean): SongsSection {
        val allowed = songsSections(isAfkStream)
        return if (selected in allowed) selected else SongsSection.LastPlayed
    }

    fun settingsSections(): List<SettingsSection> =
        listOf(
            SettingsSection.General,
            SettingsSection.Auto,
            SettingsSection.Connection,
            SettingsSection.Alerts,
        )
}
