package io.r_a_d.geiravor.ui

object AppLayout {
    const val TWO_PANE_MIN_WIDTH_DP = 840
    const val TABLET_SMALLEST_WIDTH_DP = 600

    fun twoPane(widthDp: Int, smallestWidthDp: Int = widthDp): Boolean =
        widthDp >= TWO_PANE_MIN_WIDTH_DP && smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP

    fun tabs(twoPane: Boolean): List<AppTab> =
        if (twoPane) {
            listOf(AppTab.Songs, AppTab.News, AppTab.Settings)
        } else {
            AppTab.entries.toList()
        }

    fun clampTab(tab: AppTab, twoPane: Boolean): AppTab {
        if (!twoPane || tab != AppTab.NowPlaying) {
            return tab
        }
        return AppTab.Songs
    }
}
