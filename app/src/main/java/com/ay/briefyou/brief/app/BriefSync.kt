package com.ay.briefyou.brief.app

import javax.inject.Inject
import javax.inject.Singleton
import com.ay.briefyou.brief.feeds.FeedIconOverrides
import com.ay.briefyou.brief.filters.BriefFilters
import com.ay.briefyou.brief.notifications.NotificationHelper
import com.ay.briefyou.domain.model.article.Article

/**
 * Brief You's additions to the SyncWorker, kept here so the original file only needs two
 * calls: one new-articles notification per sync, mute keywords and duplicate stories, and custom
 * feed icons kept through syncs.
 */
@Singleton
class BriefSync
@Inject
constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val briefFilters: BriefFilters,
    private val feedIconOverrides: FeedIconOverrides,
    private val notificationHelper: NotificationHelper,
    private val cleanup: BriefCleanup,
) {
    /** Runs a sync; the new-articles notification is posted once, when it's all done. */
    suspend fun <T> duringSync(block: suspend () -> T): T {
        notificationHelper.syncStarted()
        try {
            return block()
        } finally {
            // Also when the sync was cancelled: otherwise the digest would wait for it forever.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { notificationHelper.syncFinished() }
        }
    }

    /**
     * After a sync and the original clean-up of old articles ([removed]): the filters forget those,
     * mute keywords and duplicate stories are applied, and custom feed icons put back.
     */
    suspend fun afterSync(accountId: Int, removed: List<Article>) {
        runCatching { briefFilters.forget(removed.map { it.id }) }
        runCatching { briefFilters.apply(accountId) }
        runCatching { feedIconOverrides.reapply() }
        // The player's notes for articles that are gone: attachments, places and video shapes.
        runCatching {
            val ids = removed.map { it.id }
            com.ay.briefyou.brief.media.ArticleMedia.forget(ids)
            com.ay.briefyou.brief.player.BriefPlayer.forget(context, ids)
        }
        // The rest of Brief You's notes about things that are gone, now and then (see BriefCleanup).
        runCatching { cleanup.sweepIfDue() }
    }
}
