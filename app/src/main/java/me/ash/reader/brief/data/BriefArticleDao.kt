package me.ash.reader.brief.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Date
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.db.AndroidDatabase

/**
 * Brief You's own article queries (kept out of Read You's ArticleDao): recent articles for the
 * filters, every article id, and the articles for an AI gist. Read You's database lists it with
 * one line (AndroidDatabase.briefArticleDao). Queries only: no new tables, so the database schema
 * is Read You's.
 */
@Dao
interface BriefArticleDao {

    /** Starred articles of an account, newest first, for their offline copies. */
    @Transaction
    @Query("SELECT * FROM article WHERE accountId = :accountId AND isStarred = 1 ORDER BY date DESC")
    suspend fun queryStarred(accountId: Int): List<ArticleWithFeed>

    /** The starred articles' ids as they change, to keep their offline copies in step. */
    @Query("SELECT id FROM article WHERE isStarred = 1")
    fun starredIdsFlow(): kotlinx.coroutines.flow.Flow<List<String>>

    /** Every starred article's id (all accounts), so copies of unstarred ones can go. */
    @Query("SELECT id FROM article WHERE isStarred = 1")
    suspend fun queryStarredIds(): List<String>

    /** Every article id, so the filters' log can drop entries whose article is gone. */
    @Query("SELECT id FROM article")
    suspend fun queryAllIds(): List<String>

    /** Recent articles for mute filters and duplicate detection. */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        AND date >= :since
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    suspend fun queryRecentForFilters(accountId: Int, since: Date, limit: Int): List<ArticleWithFeed>

    /** Articles for an AI gist of everything, matching the list the user is looking at. */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        AND (:unreadOnly = 0 OR isUnread = 1)
        AND (:starredOnly = 0 OR isStarred = 1)
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    suspend fun queryForGist(
        accountId: Int,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        limit: Int,
    ): List<ArticleWithFeed>

    /** Articles for an AI gist of a group. */
    @Transaction
    @Query(
        """
        SELECT a.* FROM article AS a
        LEFT JOIN feed AS f ON a.feedId = f.id
        WHERE a.accountId = :accountId
        AND f.groupId = :groupId
        AND (:unreadOnly = 0 OR a.isUnread = 1)
        AND (:starredOnly = 0 OR a.isStarred = 1)
        ORDER BY a.date DESC
        LIMIT :limit
        """
    )
    suspend fun queryForGistByGroupId(
        accountId: Int,
        groupId: String,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        limit: Int,
    ): List<ArticleWithFeed>

    /** Articles for an AI gist of a feed. */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        AND feedId = :feedId
        AND (:unreadOnly = 0 OR isUnread = 1)
        AND (:starredOnly = 0 OR isStarred = 1)
        ORDER BY date DESC
        LIMIT :limit
        """
    )
    suspend fun queryForGistByFeedId(
        accountId: Int,
        feedId: String,
        unreadOnly: Boolean,
        starredOnly: Boolean,
        limit: Int,
    ): List<ArticleWithFeed>

    /** Every unread article's title with its feed and group, as they change (watched counts at home). */
    @Query(
        """
        SELECT a.feedId AS feedId, f.groupId AS groupId, a.title AS title FROM article AS a
        LEFT JOIN feed AS f ON a.feedId = f.id
        WHERE a.accountId = :accountId AND a.isUnread = 1
        """
    )
    fun unreadTitles(accountId: Int): kotlinx.coroutines.flow.Flow<List<UnreadTitle>>
}

/** An unread article's title and where it is. */
data class UnreadTitle(val feedId: String, val groupId: String?, val title: String)

@Module
@InstallIn(SingletonComponent::class)
object BriefDataModule {
    @Provides
    fun provideBriefArticleDao(androidDatabase: AndroidDatabase): BriefArticleDao =
        androidDatabase.briefArticleDao()
}
