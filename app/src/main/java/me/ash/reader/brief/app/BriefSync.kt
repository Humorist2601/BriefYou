package me.ash.reader.brief.app

import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.brief.feeds.FeedIconOverrides
import me.ash.reader.brief.filters.BriefFilters
import me.ash.reader.brief.notifications.NotificationHelper
import me.ash.reader.domain.model.article.Article

/**
 * Brief You's additions to Read You's SyncWorker, kept here so Read You's file only needs two
 * calls: one new-articles notification per sync, mute keywords and duplicate stories, and custom
 * feed icons kept through syncs.
 */
@Singleton
class BriefSync
@Inject
constructor(
    private val briefFilters: BriefFilters,
    private val feedIconOverrides: FeedIconOverrides,
    private val notificationHelper: NotificationHelper,
) {
    /** Runs a sync; the new-articles notification is posted once, when it's all done. */
    suspend fun <T> duringSync(block: suspend () -> T): T {
        notificationHelper.syncStarted()
        try {
            return block()
        } finally {
            notificationHelper.syncFinished()
        }
    }

    /**
     * After a sync and Read You's clean-up of old articles ([removed]): the filters forget those,
     * mute keywords and duplicate stories are applied, and custom feed icons put back.
     */
    suspend fun afterSync(accountId: Int, removed: List<Article>) {
        runCatching { briefFilters.forget(removed.map { it.id }) }
        runCatching { briefFilters.apply(accountId) }
        runCatching { feedIconOverrides.reapply() }
    }
}
