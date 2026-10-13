package me.ash.reader.infrastructure.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.ash.reader.R
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.feed.FeedWithArticle
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.ui.page.common.ExtraName
import me.ash.reader.ui.page.common.NotificationGroupName
import timber.log.Timber

/**
 * Posts ONE digest notification for all new articles, instead of one notification per article
 * grouped by feed.
 *
 * Every sync calls [notify] once per feed. Each call merges its articles into a running digest
 * (persisted in SharedPreferences so it survives process death) and re-posts the same
 * notification id. `setOnlyAlertOnce` means the phone only buzzes when the digest first appears,
 * not for every feed that is merged in afterwards. The digest resets when the user dismisses it
 * or opens the app ([clearDigest]).
 */
@Singleton
class NotificationHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val coroutineScope: CoroutineScope,
) {

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context).apply {
            createNotificationChannel(
                NotificationChannel(
                    NotificationGroupName.ARTICLE_UPDATE,
                    context.getString(R.string.notification_channel_new_articles),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    fun notify(feed: Feed, articles: List<Article>) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (articles.isEmpty()) return
        if (!feed.isNotification) return
        coroutineScope.launch {
            mutex.withLock {
                Timber.d("digest: +${articles.size} from ${feed.name}")
                // If the user swiped the digest away, start a fresh one.
                if (!isDigestShowing()) resetStoredDigest()

                val total = prefs.getInt(KEY_COUNT, 0) + articles.size
                val feeds = prefs.getStringSet(KEY_FEEDS, emptySet()).orEmpty() + feed.name
                val newLines =
                    articles
                        .sortedByDescending { it.date }
                        .map { "${feed.name}${FIELD_SEP}${it.title}${FIELD_SEP}${it.id}" }
                val lines =
                    (newLines + prefs.getString(KEY_LINES, null).splitLines()).take(MAX_LINES)

                prefs.edit {
                    putInt(KEY_COUNT, total)
                    putStringSet(KEY_FEEDS, feeds)
                    putString(KEY_LINES, lines.joinToString(LINE_SEP))
                }
                post(total, feeds.size, lines)
            }
        }
    }

    fun notify(feedWithArticle: FeedWithArticle) {
        notify(feedWithArticle.feed, feedWithArticle.articles)
    }

    /** Called when the app comes to the foreground: the user is now reading, so drop the digest. */
    fun clearDigest() {
        coroutineScope.launch {
            mutex.withLock {
                notificationManager.cancel(DIGEST_ID)
                resetStoredDigest()
            }
        }
    }

    private fun post(total: Int, feedCount: Int, lines: List<String>) {
        val title = context.resources.getQuantityString(R.plurals.digest_title, total, total)
        val subtitle =
            context.resources.getQuantityString(R.plurals.digest_from_feeds, feedCount, feedCount)

        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.forEach { line ->
            val (feedName, articleTitle) = line.split(FIELD_SEP).let { it[0] to it.getOrElse(1) { "" } }
            inbox.addLine("$feedName · $articleTitle")
        }
        if (total > lines.size) {
            inbox.setSummaryText(
                context.getString(R.string.digest_and_more, total - lines.size)
            )
        }

        // A single new article opens directly; otherwise open the app's article list.
        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (total == 1) {
                    lines.firstOrNull()?.split(FIELD_SEP)?.getOrNull(2)?.let {
                        putExtra(ExtraName.ARTICLE_ID, it)
                    }
                }
            }

        val notification =
            NotificationCompat.Builder(context, NotificationGroupName.ARTICLE_UPDATE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setNumber(total)
                .setStyle(inbox)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        DIGEST_ID,
                        openIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .build()

        try {
            notificationManager.notify(DIGEST_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "POST_NOTIFICATIONS not granted")
        }
    }

    private fun isDigestShowing(): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.activeNotifications.any { it.id == DIGEST_ID }
    }

    private fun resetStoredDigest() {
        prefs.edit { clear() }
    }

    private fun String?.splitLines(): List<String> =
        if (isNullOrEmpty()) emptyList() else split(LINE_SEP)

    private companion object {
        const val DIGEST_ID = 0x5EAD
        const val PREFS = "notification_digest"
        const val KEY_COUNT = "count"
        const val KEY_FEEDS = "feeds"
        const val KEY_LINES = "lines"
        const val MAX_LINES = 7
        const val LINE_SEP = "\u001E"
        const val FIELD_SEP = "\u001F"
    }
}
