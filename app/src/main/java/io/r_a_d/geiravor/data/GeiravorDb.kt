package io.r_a_d.geiravor.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import uniffi.geiravor_core.FavoriteRow

@Entity(tableName = "fave_membership", primaryKeys = ["nick", "meta"])
data class FaveMembershipEntity(
    val nick: String,
    val meta: String,
    val tracksId: Long?,
)

@Entity(tableName = "last_paint")
data class LastPaintEntity(
    @PrimaryKey val id: Int = 1,
    val blob: String,
)

@Entity(tableName = "news_page")
data class NewsPageEntity(
    @PrimaryKey val page: Int,
    val lastPage: Int,
    val ids: String,
)

@Entity(tableName = "news_article")
data class NewsArticleEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val header: String,
    val text: String,
    val updatedAt: String,
    val authorId: Long,
    val authorUser: String,
    val authorRole: String,
)

@Entity(tableName = "news_comment", primaryKeys = ["articleId", "commentId"])
data class NewsCommentEntity(
    val articleId: Long,
    val commentId: Long,
    val author: String,
    val postedAt: String,
    val body: String,
    val role: String,
)

@Entity(tableName = "staff_member", primaryKeys = ["role", "sortIndex"])
data class StaffMemberEntity(
    val role: String,
    val sortIndex: Int,
    val name: String,
    val image: String,
)

@Entity(tableName = "schedule_day", primaryKeys = ["weekday"])
data class ScheduleDayEntity(
    val weekday: String,
    val body: String,
    val ownerName: String,
    val ownerImage: String,
    val sortIndex: Int,
)

@Dao
interface FaveMembershipDao {
    @Query("SELECT * FROM fave_membership WHERE nick = :nick")
    suspend fun forNick(nick: String): List<FaveMembershipEntity>

    @Query("DELETE FROM fave_membership WHERE nick = :nick")
    suspend fun deleteNick(nick: String)

    @Query("DELETE FROM fave_membership WHERE nick != :nick")
    suspend fun deleteOtherNicks(nick: String)

    @Query("SELECT DISTINCT nick FROM fave_membership")
    suspend fun nicks(): List<String>

    @Query("DELETE FROM fave_membership WHERE nick NOT IN (:keep)")
    suspend fun deleteNicksNotIn(keep: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<FaveMembershipEntity>)

    @Query("DELETE FROM fave_membership")
    suspend fun deleteAll()
}

@Dao
interface LastPaintDao {
    @Query("SELECT * FROM last_paint WHERE id = 1")
    suspend fun get(): LastPaintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LastPaintEntity)
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM news_page WHERE page = :page")
    suspend fun page(page: Int): NewsPageEntity?

    @Query("SELECT * FROM news_page")
    suspend fun allPages(): List<NewsPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(row: NewsPageEntity)

    @Query("DELETE FROM news_page WHERE page NOT IN (:keep)")
    suspend fun deletePagesNotIn(keep: List<Int>)

    @Query("SELECT * FROM news_article WHERE id = :id")
    suspend fun article(id: Long): NewsArticleEntity?

    @Query("SELECT * FROM news_article WHERE id IN (:ids)")
    suspend fun articles(ids: List<Long>): List<NewsArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticle(row: NewsArticleEntity)

    @Query("DELETE FROM news_article WHERE id NOT IN (:keep)")
    suspend fun deleteArticlesNotIn(keep: List<Long>)

    @Query("SELECT * FROM news_comment WHERE articleId = :articleId ORDER BY commentId")
    suspend fun comments(articleId: Long): List<NewsCommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(rows: List<NewsCommentEntity>)

    @Query("DELETE FROM news_comment WHERE articleId = :articleId")
    suspend fun deleteComments(articleId: Long)

    @Query("DELETE FROM news_comment WHERE articleId NOT IN (:keep)")
    suspend fun deleteCommentsNotIn(keep: List<Long>)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff_member ORDER BY sortIndex")
    suspend fun all(): List<StaffMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<StaffMemberEntity>)

    @Query("DELETE FROM staff_member")
    suspend fun deleteAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_day ORDER BY sortIndex")
    suspend fun all(): List<ScheduleDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ScheduleDayEntity>)

    @Query("DELETE FROM schedule_day")
    suspend fun deleteAll()
}

@Database(
    entities = [
        FaveMembershipEntity::class,
        LastPaintEntity::class,
        NewsPageEntity::class,
        NewsArticleEntity::class,
        NewsCommentEntity::class,
        StaffMemberEntity::class,
        ScheduleDayEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class GeiravorDb : RoomDatabase() {
    abstract fun faves(): FaveMembershipDao
    abstract fun paint(): LastPaintDao
    abstract fun news(): NewsDao
    abstract fun staff(): StaffDao
    abstract fun schedule(): ScheduleDao

    companion object {
        @Volatile
        private var instance: GeiravorDb? = null

        fun get(context: Context): GeiravorDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GeiravorDb::class.java,
                    "geiravor.db",
                ).fallbackToDestructiveMigration(true).build().also { instance = it }
            }
    }
}

object MembershipStore {
    fun changed(
        existing: List<FaveMembershipEntity>,
        incoming: List<FaveMembershipEntity>,
    ): Boolean = DiskPolicy.changed(existing.toSet(), incoming.toSet())

    fun toRows(entities: List<FaveMembershipEntity>): List<FavoriteRow> =
        entities.map { entity ->
            val split = entity.meta.split(" - ", limit = 2)
            FavoriteRow(
                tracksId = entity.tracksId,
                meta = entity.meta,
                artist = if (split.size > 1) split[0] else "",
                title = if (split.size > 1) split[1] else entity.meta,
                lastRequested = null,
                lastPlayed = null,
                requestCount = null,
            )
        }

    fun fromRows(nick: String, rows: List<FavoriteRow>): List<FaveMembershipEntity> =
        rows.map { row ->
            FaveMembershipEntity(nick = nick, meta = row.meta, tracksId = row.tracksId)
        }
}
