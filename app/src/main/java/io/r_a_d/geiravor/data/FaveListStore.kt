package io.r_a_d.geiravor.data

import io.r_a_d.geiravor.radio.ListingCache
import uniffi.geiravor_core.FavoriteRow
import uniffi.geiravor_core.FavoritesPage

object FaveListStore {
    fun fromPage(nick: String, page: FavoritesPage): List<FaveListRowEntity> =
        page.data.mapIndexed { index, row ->
            FaveListRowEntity(
                nick = nick,
                page = page.currentPage,
                sortIndex = index,
                lastPage = page.lastPage,
                tracksId = row.tracksId,
                meta = row.meta,
                artist = row.artist,
                title = row.title,
                lastRequested = row.lastRequested,
                lastPlayed = row.lastPlayed,
                requestCount = row.requestCount,
            )
        }

    fun toPage(nick: String, page: Int, lastPage: Int, rows: List<FaveListRowEntity>): FavoritesPage =
        FavoritesPage(
            currentPage = page,
            lastPage = lastPage,
            data = rows.sortedBy { it.sortIndex }.map(::toRow),
        )

    fun toRow(entity: FaveListRowEntity): FavoriteRow = FavoriteRow(
        tracksId = entity.tracksId,
        meta = entity.meta,
        artist = entity.artist,
        title = entity.title,
        lastRequested = entity.lastRequested,
        lastPlayed = entity.lastPlayed,
        requestCount = entity.requestCount,
    )

    fun pageChanged(
        existing: List<FaveListRowEntity>,
        incoming: List<FaveListRowEntity>,
    ): Boolean = DiskPolicy.changed(existing, incoming)

    suspend fun hydrate(db: GeiravorDb, nicks: List<String>) {
        nicks.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { nick ->
            hydrateNick(db, nick)
        }
    }

    suspend fun hydrateNick(db: GeiravorDb, nick: String) {
        val key = nick.trim()
        if (key.isEmpty()) {
            return
        }
        val rows = db.faveList().forNick(key)
        val meta = db.faveList().meta(key)
        if (rows.isEmpty() && meta == null) {
            return
        }
        val last = meta?.lastPage ?: rows.maxOfOrNull { maxOf(it.lastPage, it.page) } ?: 1
        rows.groupBy { it.page }.toSortedMap().forEach { (page, pageRows) ->
            ListingCache.putFaves(key, toPage(key, page, last, pageRows))
        }
        if (rows.none { it.page == last } && last > 1) {
            return
        }
        if (rows.isEmpty() && last == 1) {
            ListingCache.putFaves(key, FavoritesPage(currentPage = 1, lastPage = 1, data = emptyList()))
        }
    }

    suspend fun savePage(db: GeiravorDb, nick: String, page: FavoritesPage) {
        val key = nick.trim()
        if (key.isEmpty()) {
            return
        }
        val dao = db.faveList()
        if (page.currentPage > page.lastPage) {
            dao.deletePage(key, page.currentPage)
            dao.deletePagesAbove(key, page.lastPage)
            val meta = FaveListMetaEntity(key, page.lastPage)
            if (DiskPolicy.changed(dao.meta(key), meta)) {
                dao.upsertMeta(meta)
            }
            return
        }
        val incoming = fromPage(key, page)
        val existing = dao.forPage(key, page.currentPage)
        if (pageChanged(existing, incoming)) {
            dao.deletePage(key, page.currentPage)
            if (incoming.isNotEmpty()) {
                dao.insertRows(incoming)
            }
        }
        if (page.currentPage == 1 || page.currentPage >= page.lastPage) {
            val meta = FaveListMetaEntity(key, page.lastPage)
            if (DiskPolicy.changed(dao.meta(key), meta)) {
                dao.upsertMeta(meta)
            }
        }
        dao.deletePagesAbove(key, page.lastPage)
    }

    suspend fun keepNicks(db: GeiravorDb, nicks: List<String>) {
        val keep = nicks.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val dao = db.faveList()
        if (keep.isEmpty()) {
            dao.deleteAllRows()
            dao.deleteAllMeta()
            return
        }
        dao.deleteRowsNicksNotIn(keep)
        dao.deleteMetaNicksNotIn(keep)
    }
}
