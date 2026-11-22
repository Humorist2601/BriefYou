package com.ay.briefyou.domain.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import com.ay.briefyou.domain.model.account.Account
import com.ay.briefyou.domain.model.account.AccountType
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.di.ApplicationScope
import com.ay.briefyou.infrastructure.di.IODispatcher
import java.io.File
import javax.inject.Inject

private const val TAG = "DiffMapHolder"

@OptIn(FlowPreview::class)
class DiffMapHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val accountService: AccountService,
    private val rssService: RssService,
) {
    val diffMap = mutableStateMapOf<String, Diff>()

    private val pendingSyncDiffs = mutableStateMapOf<String, Diff>()
    private val syncedDiffs = mutableMapOf<String, Diff>()

    val diffMapSnapshotFlow = snapshotFlow { diffMap.toMap() }.stateIn(
        applicationScope, SharingStarted.Eagerly, emptyMap()
    )

    private val pendingSyncDiffsSnapshotFlow = snapshotFlow { pendingSyncDiffs.toMap() }.stateIn(
        applicationScope, SharingStarted.Eagerly, emptyMap()
    )

    val shouldSyncWithRemote get() = currentAccount?.type != AccountType.Local

    private val gson = Gson()

    private val cacheDir = context.cacheDir.resolve("diff")
    private var userCacheDir = cacheDir

    private var currentAccount: Account? = null

    private val cacheFile: File get() = userCacheDir.resolve("diff_map.json")

    var dbJob: Job? = null
    var remoteJob: Job? = null

    init {
        applicationScope.launch {
            accountService.currentAccountFlow.mapNotNull { it }.collect { account ->
                val previousAccount = currentAccount
                // Brief You: only a different account starts over. The account's own row changes
                // after every sync (its update time), and Read You then treated that as a new
                // account: it emptied the unsaved read/unread changes (the cache was written from
                // another coroutine after the clearing, so it was empty too) and read them back.
                // Everything read since the last save, the Triage run included, became unread when
                // a background sync finished.
                if (previousAccount?.id == account.id) {
                    currentAccount = account
                    return@collect
                }
                if (previousAccount != null) {
                    cleanup(previousAccount)
                }
                currentAccount = account
                init(account)
            }
        }
    }

    private fun init(account: Account) {
        userCacheDir = cacheDir.resolve(account.id.toString())
        commitDiffsFromCache()
        commitOnChange()
        if (account.type != AccountType.Local) {
            syncOnChange()
        }
    }

    private fun cleanup(account: Account) {
        dbJob?.cancel()
        remoteJob?.cancel()
        writeDiffsToCacheNow() // Brief You: before the map is emptied, into this account's own file
        diffMap.clear()
        pendingSyncDiffs.clear()
        syncedDiffs.clear()
    }

    private fun commitOnChange() {
        dbJob = applicationScope.launch(ioDispatcher) {
            // Brief You: 0.5 s (was 2 s); a quick close lost what was marked just before
            diffMapSnapshotFlow.debounce(500).collect {
                if (it.isNotEmpty()) {
                    writeDiffsToCache()
                }
            }
        }
    }

    private fun syncOnChange() {
        remoteJob = applicationScope.launch(ioDispatcher) {
            pendingSyncDiffsSnapshotFlow.debounce(2_000).collect {
                withContext(ioDispatcher) {
                    syncDiffsWithRemote(it)
                }
            }
        }
    }

    fun checkIfUnread(articleWithFeed: ArticleWithFeed): Boolean {
        return diffMap[articleWithFeed.article.id]?.isUnread ?: articleWithFeed.article.isUnread
    }

    /**
     * Updates the diff map with changes to an article's read/unread status.
     *
     * This function manages a map (`diffMap`) that tracks pending changes (diffs) to the
     * read/unread status of articles. These changes are not immediately applied to the
     * underlying data store but are held in `diffMap` until a later commit operation.
     *
     * The function supports three modes of updating:
     *
     * 1. **Toggle:** If `isUnread` is `null`, the function toggles the current read/unread
     *    status of the article.  If the article is currently unread, it will be marked as read,
     *    and vice-versa.
     * 2. **Mark as Unread:** If `isUnread` is `true`, the article will be marked as unread,
     *    regardless of its current status.
     * 3. **Mark as Read:** If `isUnread` is `false`, the article will be marked as read,
     *    regardless of its current status.
     *
     * The function determines if a change needs to be tracked based on the current status and desired status:
     *  - If the requested change matches the article's current status, the diff is removed from the map, if it exists. (No change is needed.)
     *  - Otherwise, the diff is added to or updated in the map.
     *
     * @param articleWithFeed The article and its associated feed data. This is used to identify the article
     *                        and access its current read/unread state.
     * @param isUnread An optional boolean indicating the desired read/unread status of the article.
     *                 - `null`: Toggles the current read/unread status.
     *                 - `true`: Marks the article as unread.
     *                 - `false`: Marks the article as read.
     *
     * @return A [Diff] object representing the changes made to the article.
     *
     * @see Diff
     */
    private fun updateDiffInternal(
        articleWithFeed: ArticleWithFeed, isUnread: Boolean? = null
    ): Diff? {
        val articleId = articleWithFeed.article.id

        val diff = diffMap[articleId]

        if (diff == null) {
            val isUnread = isUnread ?: !articleWithFeed.article.isUnread
            val diff = Diff(
                isUnread = isUnread, articleWithFeed = articleWithFeed
            )
            diffMap[articleId] = diff
            return diff
        } else {
            if (isUnread == null || diff.isUnread != isUnread) {
                val diff = diffMap.remove(articleId)
                return diff?.copy(isUnread = !diff.isUnread)
            }
        }
        return null
    }

    fun updateDiff(
        vararg articleWithFeed: ArticleWithFeed, isUnread: Boolean? = null
    ) {
        val appliedDiffs = articleWithFeed.mapNotNull {
            updateDiffInternal(it, isUnread)
        }
        if (shouldSyncWithRemote) {
            appliedDiffs.forEach {
                appendDiffToSync(it)
            }
        }
    }

    private fun appendDiffToSync(diff: Diff) {
        val syncedDiff = syncedDiffs[diff.articleId]
        if (syncedDiff == null || syncedDiff.isUnread != diff.isUnread) {
            pendingSyncDiffs[diff.articleId] = diff
        }
    }

    fun commitDiffsToDb() {
        applicationScope.launch(ioDispatcher) {
            // Brief You: write first, then forget only the changes that were written. Read You
            // cleared every change (in a separate coroutine) before writing, so anything marked
            // read or unread in between was dropped, and lists briefly showed the old state:
            // articles "went back" to unread (Read You #1254, #1285).
            val committed = diffMap.toMap()
            if (committed.isEmpty()) return@launch
            val markAsReadArticles = committed.filter { !it.value.isUnread }.map { it.key }.toSet()
            val markAsUnreadArticles = committed.filter { it.value.isUnread }.map { it.key }.toSet()
            rssService.get().batchMarkAsRead(articleIds = markAsReadArticles, isUnread = false)
            rssService.get().batchMarkAsRead(articleIds = markAsUnreadArticles, isUnread = true)
            committed.forEach { (id, diff) -> if (diffMap[id] == diff) diffMap.remove(id) }
            if (diffMap.isEmpty()) {
                if (cacheFile.exists() && cacheFile.canWrite()) cacheFile.delete()
            } else {
                writeDiffsToCache()
            }
        }
    }

    private fun writeDiffsToCache() {
        // Brief You: the map and the file are taken now, not inside the coroutine, where the map
        // may have been emptied and the account's folder changed by then.
        val json = gson.toJson(diffMap.toMap())
        val file = cacheFile
        applicationScope.launch(ioDispatcher) { writeCache(file, json) }
    }

    /** Writes the unsaved changes to the cache file before returning (a small file). */
    private fun writeDiffsToCacheNow() {
        writeCache(cacheFile, gson.toJson(diffMap.toMap()))
    }

    private fun writeCache(file: File, json: String) {
        try {
            file.parentFile?.mkdirs()
            file.createNewFile()
            if (file.exists() && file.canWrite()) {
                file.writeText(json)
            }
        } catch (_: Exception) {

        }
    }

    /**
     * Brief You: called when the app leaves the screen. Saves the unsaved read/unread changes to
     * the cache file at once (the app may be closed from the recent apps list right after, long
     * before the 0.5 s wait above), and then writes them to the database.
     */
    fun flush() {
        if (diffMap.isEmpty()) return
        writeDiffsToCacheNow()
    }

    private suspend fun syncDiffsWithRemote(diffs: Map<String, Diff>) {
        if (!shouldSyncWithRemote) return
        if (diffs.isEmpty()) return
        val toBeSync = diffs
        val markAsReadArticles =
            toBeSync.filter { !it.value.isUnread }.map { it.key }.toSet()
        val markAsUnreadArticles =
            toBeSync.filter { it.value.isUnread }.map { it.key }.toSet()

        val rssService = rssService.get()

        val synced = supervisorScope {
            val read = async {
                rssService.syncReadStatus(
                    articleIds = markAsReadArticles,
                    isUnread = false
                )
            }
            val unread = async {
                rssService.syncReadStatus(
                    articleIds = markAsUnreadArticles,
                    isUnread = true
                )
            }
            runCatching { read.await() }.getOrElse { emptySet() } +
                    runCatching { unread.await() }.getOrElse { emptySet() }
        }

        pendingSyncDiffs -= synced
        syncedDiffs += diffs.filter { synced.contains(it.key) }
    }

    private fun commitDiffsFromCache() {
        applicationScope.launch(ioDispatcher) {
            if (cacheFile.exists() && cacheFile.canRead()) {
                val tmpJson = cacheFile.readText()
                val mapType = object : TypeToken<Map<String, Diff>>() {}.type
                val diffMapFromCache = gson.fromJson<Map<String, Diff>>(
                    tmpJson, mapType
                )
                diffMapFromCache?.let {
                    diffMap.clear()
                    diffMap.putAll(it)
                }
            }
        }.invokeOnCompletion {
            commitDiffsToDb()
        }
    }

    private fun clearDiffs() {
        applicationScope.launch(ioDispatcher) {
            if (cacheFile.exists() && cacheFile.canWrite()) {
                cacheFile.delete()
            }
            diffMap.clear()
        }
    }
}

data class Diff(
    val isUnread: Boolean, val articleId: String, val feedId: String
) {
    constructor(isUnread: Boolean, articleWithFeed: ArticleWithFeed) : this(
        isUnread = isUnread,
        articleId = articleWithFeed.article.id,
        feedId = articleWithFeed.feed.id,
    )
}
