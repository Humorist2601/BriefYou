package me.ash.reader.brief.app

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.brief.data.BriefArticleDao
import me.ash.reader.brief.feeds.FeedIconOverrides
import me.ash.reader.brief.feeds.FeedPictures
import me.ash.reader.brief.media.ArticleMedia
import me.ash.reader.brief.player.BriefPlayer
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.preference.KeepArchivedPreference

/**
 * Brief You's tidy-up of its own notes about things that are gone. Most are cleared when the thing
 * goes (a sync's clean-up of old articles, deleting a feed); this sweep catches the rest (unsubscribing,
 * deleting a group or an account, clearing articles), as often as the account keeps old articles
 * (Keep archived articles; monthly when they're kept always):
 * - attachments, listening places and video shapes of articles that are gone,
 * - the last episode for Android's resume, when its article is gone,
 * - custom icons (and their image files) and noted pictures of feeds that are gone,
 * - the old feed-colour caches (once).
 * Brief You.
 */
@Singleton
class BriefCleanup
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val articles: BriefArticleDao,
    private val feedIconOverrides: FeedIconOverrides,
    private val accountService: AccountService,
) {
    private val prefs = context.getSharedPreferences("brief_cleanup", Context.MODE_PRIVATE)

    /** Sweeps when the last sweep is older than the account's keep period (after a sync). */
    suspend fun sweepIfDue() {
        val keep = runCatching { accountService.getCurrentAccount().keepArchived }.getOrNull()
        val every = keep?.value?.takeIf { keep != KeepArchivedPreference.Always && it > 0 } ?: MONTH_MS
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST, 0L) < every) return
        sweep()
        prefs.edit { putLong(KEY_LAST, now) }
    }

    private suspend fun sweep() {
        val exists: suspend (List<String>) -> Set<String> = { articles.existingIds(it).toSet() }
        runCatching { ArticleMedia.sweep() }
        runCatching { BriefPlayer.sweep(context, exists) }
        runCatching { feedIconOverrides.sweep(articles.allFeedIds()) }
        runCatching { FeedPictures.sweep(context, articles.allFeedUrls()) }
        if (!prefs.getBoolean(KEY_OLD_CACHES, false)) {
            runCatching { OLD_CACHES.forEach { context.deleteSharedPreferences(it) } }
            prefs.edit { putBoolean(KEY_OLD_CACHES, true) }
        }
    }

    private companion object {
        const val KEY_LAST = "last_sweep"
        const val KEY_OLD_CACHES = "old_caches_gone"
        const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        /** Feed-colour caches from before feed_accent_v3. */
        val OLD_CACHES = listOf("feed_accent", "feed_accent_v1", "feed_accent_v2")
    }
}
