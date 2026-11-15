package me.ash.reader.brief.app

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.brief.data.BriefArticleDao
import me.ash.reader.brief.feeds.FeedIconOverrides
import me.ash.reader.brief.feeds.FeedPictures
import me.ash.reader.brief.feeds.GroupIcons
import me.ash.reader.brief.feeds.IconLookups
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * - icons (and pictures) of groups that are gone, and website-icon searches that failed over a
 *   week ago (they're searched again anyway),
 * - Read You's saved full-text pages of articles that are gone (it deletes them only when its
 *   clean-up clears old articles, not when a feed, group or account is deleted, or a sync service
 *   drops articles),
 * - what a deleted account left: its saved pages, Read You's list of its changes still to be
 *   sent, and its sort setting,
 * - once, after an update: the old feed-colour caches, and the cleared-article notes of
 *   accounts that aren't local (only local feeds read them; see ArchivedLinks).
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
        once()
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
        runCatching { GroupIcons.sweep(context, articles.allGroupIds()) }
        runCatching { IconLookups.sweep(context) }
        runCatching { sweepAccounts() }
    }

    /** What is cleared once, after an update (not waiting for the sweep). */
    private suspend fun once() {
        if (!prefs.getBoolean(KEY_OLD_CACHES, false)) {
            runCatching { OLD_CACHES.forEach { context.deleteSharedPreferences(it) } }
            prefs.edit { putBoolean(KEY_OLD_CACHES, true) }
        }
        if (!prefs.getBoolean(KEY_NOT_LOCAL_NOTES, false)) {
            runCatching {
                articles.forgetArchivedLinksNotLocal()
                prefs.edit { putBoolean(KEY_NOT_LOCAL_NOTES, true) }
            }
        }
    }

    /**
     * Saved full-text pages (Read You's ReaderCacheHelper: cache/readability/<account>/<SHA-256 of
     * the article id>.html) of articles that are gone, and whatever a deleted account left.
     */
    private suspend fun sweepAccounts() =
        withContext(Dispatchers.IO) {
            val accounts = articles.allAccountIds().map { it.toString() }.toHashSet()
            if (accounts.isEmpty()) return@withContext
            val pages = File(context.cacheDir, "readability")
            // Not a page written in the last hour (its article may have come after the list).
            val settled = System.currentTimeMillis() - HOUR_MS
            pages.listFiles()?.forEach { dir ->
                if (dir.name !in accounts) {
                    dir.deleteRecursively()
                    return@forEach
                }
                val id = dir.name.toIntOrNull() ?: return@forEach
                val sha = java.security.MessageDigest.getInstance("SHA-256")
                val keep = articles.articleIds(id).mapTo(HashSet()) { pageName(sha, it) }
                dir.listFiles()?.forEach { if (it.name !in keep && it.lastModified() < settled) it.delete() }
            }
            // Read You's changes still to be sent (DiffMapHolder), per account: only deleted ones'.
            File(context.cacheDir, "diff").listFiles()?.forEach { if (it.isDirectory && it.name !in accounts) it.deleteRecursively() }
            val order = context.getSharedPreferences("feed_order", Context.MODE_PRIVATE)
            val gone = order.all.keys.filter { it.startsWith("order.") && it.removePrefix("order.") !in accounts }
            if (gone.isNotEmpty()) order.edit { gone.forEach { remove(it) } }
        }

    /** The file name ReaderCacheHelper gives an article's page. */
    private fun pageName(sha: java.security.MessageDigest, articleId: String): String {
        val digest = sha.digest(articleId.toByteArray())
        val out = CharArray(digest.size * 2)
        digest.forEachIndexed { i, b ->
            out[i * 2] = HEX[(b.toInt() shr 4) and 0xF]
            out[i * 2 + 1] = HEX[b.toInt() and 0xF]
        }
        return String(out) + ".html"
    }

    private companion object {
        const val KEY_LAST = "last_sweep"
        const val KEY_OLD_CACHES = "old_caches_gone"
        const val KEY_NOT_LOCAL_NOTES = "not_local_notes_gone"
        const val HOUR_MS = 60L * 60 * 1000
        val HEX = "0123456789abcdef".toCharArray()
        const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
        /** Feed-colour caches from before feed_accent_v3. */
        val OLD_CACHES = listOf("feed_accent", "feed_accent_v1", "feed_accent_v2")
    }
}
