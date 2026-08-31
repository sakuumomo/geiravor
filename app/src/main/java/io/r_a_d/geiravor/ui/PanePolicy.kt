package io.r_a_d.geiravor.ui

/** Even pane pages from a flat list. Visible count is normal (portrait / tablet) viewing. */
object PanePolicy {
    /** Title + byline + two-line blurb + card padding; guess until the first card is measured. */
    const val NEWS_CARD_DP = 148
    const val NEWS_GAP_DP = 12
    const val SONG_ROW_DP = 56
    const val SONG_GAP_DP = 8
    const val SEARCH_PER_PAGE = 20
    const val FAVES_PER_PAGE = 100
    const val NEWS_PER_PAGE = 20

    fun thatFit(availableDp: Float, cardDp: Int, gapDp: Int): Int =
        thatFitSlot(availableDp, (cardDp + gapDp).toFloat())

    fun thatFitSlot(availableDp: Float, slotDp: Float): Int {
        if (!availableDp.isFinite() || availableDp <= 0f || !slotDp.isFinite() || slotDp <= 0f) {
            return 1
        }
        return kotlin.math.floor(availableDp / slotDp).toInt().coerceAtLeast(1)
    }

    fun newsThatFit(availableDp: Float): Int = thatFit(availableDp, NEWS_CARD_DP, NEWS_GAP_DP)

    fun songThatFit(availableDp: Float): Int = thatFit(availableDp, SONG_ROW_DP, SONG_GAP_DP)

    /**
     * List height as if the phone were portrait. Tablet two-pane uses the pane as-is.
     * Landscape on a phone is the short side; add (tall − short) so rotation does not
     * change how many cards fit.
     */
    fun normalListDp(
        boxMaxHeightDp: Float,
        screenWidthDp: Int,
        screenHeightDp: Int,
        twoPane: Boolean,
    ): Float {
        if (!boxMaxHeightDp.isFinite() || boxMaxHeightDp <= 0f) {
            return 0f
        }
        if (twoPane) {
            return boxMaxHeightDp
        }
        val tall = maxOf(screenWidthDp, screenHeightDp).toFloat()
        val short = minOf(screenWidthDp, screenHeightDp).toFloat()
        return if (screenWidthDp > screenHeightDp) {
            boxMaxHeightDp + (tall - short)
        } else {
            boxMaxHeightDp
        }
    }

    fun freezeVisible(locked: Int, measured: Int): Int =
        if (locked > 0) locked else measured.coerceAtLeast(1)

    fun lastPage(total: Int, visible: Int): Int {
        val vis = visible.coerceAtLeast(1)
        val n = total.coerceAtLeast(0)
        if (n <= 0) {
            return 1
        }
        return (n + vis - 1) / vis
    }

    /** Catalog size once the leftover last server page is loaded. Do not pad that page to 100. */
    fun favesCatalogSize(serverLast: Int, lastPageCount: Int): Int {
        val last = serverLast.coerceAtLeast(1)
        val leftover = lastPageCount.coerceAtLeast(0)
        return if (last == 1) leftover else (last - 1) * FAVES_PER_PAGE + leftover
    }

    fun startIndex(page: Int, visible: Int): Int =
        (page.coerceAtLeast(1) - 1) * visible.coerceAtLeast(1)

    fun <T> slice(rows: List<T>, page: Int, visible: Int): List<T> {
        val vis = visible.coerceAtLeast(1)
        val start = startIndex(page, vis)
        if (start >= rows.size) {
            return emptyList()
        }
        return rows.subList(start, minOf(start + vis, rows.size))
    }

    fun serverPage(index: Int, perServer: Int): Int =
        (index.coerceAtLeast(0) / perServer.coerceAtLeast(1)) + 1

    fun serverPages(start: Int, count: Int, perServer: Int, serverLast: Int): IntRange {
        if (count <= 0) {
            return IntRange.EMPTY
        }
        val per = perServer.coerceAtLeast(1)
        val last = serverLast.coerceAtLeast(1)
        val from = serverPage(start, per).coerceIn(1, last)
        val to = serverPage(start + count - 1, per).coerceIn(1, last)
        return from..to
    }

    /**
     * Slice a UI window out of concatenated server pages.
     * Null if a needed server page is missing (GET).
     */
    fun <T> window(
        pages: Map<Int, List<T>>,
        start: Int,
        count: Int,
        perServer: Int,
    ): List<T>? {
        val vis = count.coerceAtLeast(1)
        val per = perServer.coerceAtLeast(1)
        val needed = serverPages(start, vis, per, Int.MAX_VALUE / 4)
        if (needed.isEmpty()) {
            return emptyList()
        }
        val built = ArrayList<T>()
        for (server in needed) {
            val rows = pages[server] ?: return null
            built.addAll(rows)
        }
        val local = start - (needed.first - 1) * per
        if (local >= built.size) {
            return emptyList()
        }
        if (local < 0) {
            return null
        }
        return built.subList(local, minOf(local + vis, built.size))
    }
}
