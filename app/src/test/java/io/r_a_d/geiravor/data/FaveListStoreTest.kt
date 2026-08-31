package io.r_a_d.geiravor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.FavoriteRow
import uniffi.geiravor_core.FavoritesPage

class FaveListStoreTest {
    @Test
    fun pageRoundTripKeepsOrderAndFields() {
        val page = FavoritesPage(
            currentPage = 8,
            lastPage = 8,
            data = listOf(
                FavoriteRow(1L, "A - One", "A", "One", 10L, 20L, 3L),
                FavoriteRow(2L, "B - Two", "B", "Two", null, null, null),
            ),
        )
        val rows = FaveListStore.fromPage("Same", page)
        assertEquals(2, rows.size)
        assertEquals(0, rows[0].sortIndex)
        assertEquals(8, rows[0].lastPage)
        val back = FaveListStore.toPage("Same", 8, 8, rows.reversed())
        assertEquals(page, back)
    }

    @Test
    fun equalPageIsNotAChange() {
        val page = FavoritesPage(
            currentPage = 1,
            lastPage = 1,
            data = listOf(FavoriteRow(1L, "A - One", "A", "One", null, null, null)),
        )
        val rows = FaveListStore.fromPage("Alice", page)
        assertFalse(FaveListStore.pageChanged(rows, FaveListStore.fromPage("Alice", page)))
        assertTrue(
            FaveListStore.pageChanged(
                rows,
                FaveListStore.fromPage(
                    "Alice",
                    page.copy(
                        data = page.data + FavoriteRow(2L, "B - Two", "B", "Two", null, null, null),
                    ),
                ),
            ),
        )
    }
}
