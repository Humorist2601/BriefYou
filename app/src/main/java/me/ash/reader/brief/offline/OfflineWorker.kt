package me.ash.reader.brief.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.ash.reader.brief.data.BriefArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.rss.ReaderCacheHelper

/**
 * Keeps the offline copies in step with the stars (see [OfflineCopies]): downloads what starred
 * articles are missing, newest first, and removes copies of articles no longer starred. Tries
 * again later when something couldn't be fetched.
 */
@HiltWorker
class OfflineWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articles: BriefArticleDao,
    private val accountService: AccountService,
    private val cacheHelper: ReaderCacheHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val context = applicationContext
            val starred = articles.queryStarred(accountService.getCurrentAccountId())
            OfflineCopies.prune(context, articles.queryStarredIds())
            var complete = true
            for (item in starred) {
                ensureActive()
                val fullText =
                    if (item.feed.isFullContent) {
                        OfflineCopies.fullText(context, item.article.id)
                            ?: cacheHelper.readOrFetchFullContent(item.article).getOrNull()
                    } else null
                val ok = runCatching { OfflineCopies.download(context, item, fullText) }.getOrDefault(false)
                if (!ok) complete = false
            }
            // A few tries for what failed (a site down for a while); after that, the next star or
            // app start tries again.
            if (complete || runAttemptCount >= 3) Result.success() else Result.retry()
        }
}
