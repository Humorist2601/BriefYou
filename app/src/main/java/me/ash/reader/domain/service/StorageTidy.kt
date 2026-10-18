package me.ash.reader.domain.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.db.AndroidDatabase
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import timber.log.Timber

/**
 * Keeps the app's storage small, after syncs. Read, unstarred articles older than the account's
 * "keep read" setting (and their saved full text) are already removed after every sync; this adds:
 * - once a day, saved full text of articles that no longer exist (an unsubscribed feed, articles
 *   the server removed), which nothing else removes;
 * - once a week, giving the space of removed articles back (the database file only grows
 *   otherwise).
 * Starred and unread articles are never touched.
 */
@Singleton
class StorageTidy
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val database: AndroidDatabase,
    private val readerCacheHelper: ReaderCacheHelper,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val prefs = context.getSharedPreferences("storage_tidy", Context.MODE_PRIVATE)

    suspend fun afterSync(accountId: Int) =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_FILES, 0L) >= DAY_MS) {
                prefs.edit { putLong(KEY_FILES, now) }
                runCatching {
                        val removed = readerCacheHelper.removeOrphans(articleDao.queryAllIds(accountId))
                        Timber.d("StorageTidy: removed $removed saved full texts")
                    }
                    .onFailure { Timber.w(it, "StorageTidy: files") }
            }
            if (now - prefs.getLong(KEY_VACUUM, 0L) >= WEEK_MS) {
                prefs.edit { putLong(KEY_VACUUM, now) }
                runCatching { database.openHelper.writableDatabase.execSQL("VACUUM") }
                    .onFailure { Timber.w(it, "StorageTidy: vacuum") }
            }
        }

    private companion object {
        const val KEY_FILES = "files_at"
        const val KEY_VACUUM = "vacuum_at"
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val WEEK_MS = 7 * DAY_MS
    }
}
