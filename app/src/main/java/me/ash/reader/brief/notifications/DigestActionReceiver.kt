package me.ash.reader.brief.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import timber.log.Timber

/**
 * The digest notification's Mark all read button: marks the articles the digest lists as read
 * (through the account, so a sync service hears about it too) and removes the notification,
 * without opening the app.
 */
@AndroidEntryPoint
class DigestActionReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var rssService: RssService
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_ALL_READ) return
        val pending = goAsync()
        applicationScope.launch {
            try {
                val ids = notificationHelper.digestArticleIds()
                if (ids.isNotEmpty()) rssService.get().batchMarkAsRead(ids, isUnread = false)
            } catch (e: Exception) {
                Timber.w(e, "digest: mark all read failed")
            } finally {
                notificationHelper.clearDigest()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_ALL_READ = "me.ash.reader.brief.digest.MARK_ALL_READ"
    }
}
