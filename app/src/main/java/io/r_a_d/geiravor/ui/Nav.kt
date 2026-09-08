package io.r_a_d.geiravor.ui

enum class BottomTab(val label: String) {
    NowPlaying("Now Playing"),
    Songs("Songs"),
    Board("Board"),
    Settings("Settings"),
}

fun phoneTabs(): List<BottomTab> = BottomTab.entries

fun paneTabs(): List<BottomTab> = listOf(BottomTab.Songs, BottomTab.Board, BottomTab.Settings)

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

enum class SettingsSection(val label: String) {
    General("General"),
    Auto("Auto"),
    Connection("Connection"),
    Alerts("Alerts"),
}
