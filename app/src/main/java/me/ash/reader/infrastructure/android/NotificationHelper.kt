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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * notification id. Posting is debounced ([POST_DELAY_MS]) so a whole sync shows up as ONE complete
 * notification instead of a growing series of updates (Android also drops updates posted too
 * quickly). The digest shows one line per feed so no feed gets pushed out by busier ones. It resets
 * when the user dismisses it or opens the app ([clearDigest]).
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
    private var postJob: Job? = null

    fun notify(feed: Feed, articles: List<Article>) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (articles.isEmpty()) return
        if (!feed.isNotification) return
        coroutineScope.launch {
            mutex.withLock {
                Timber.d("digest: +${articles.size} from ${feed.name}")
                // Start fresh only if the user swiped the digest away AND no post is pending.
                if (postJob?.isActive != true && !isDigestShowing()) resetStoredDigest()

                val total = prefs.getInt(KEY_COUNT, 0) + articles.size
                val newLines =
                    articles.map {
                        listOf(feed.name, it.title, it.id, it.date.time).joinToString(FIELD_SEP)
                    }
                val lines =
                    (newLines + prefs.getString(KEY_LINES, null).splitLines()).take(MAX_STORED)

                prefs.edit {
                    putInt(KEY_COUNT, total)
                    putString(KEY_LINES, lines.joinToString(LINE_SEP))
                }

                // Wait for the other feeds to finish, then post once.
                postJob?.cancel()
                postJob =
                    coroutineScope.launch {
                        delay(POST_DELAY_MS)
                        mutex.withLock { postStored() }
                    }
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
                postJob?.cancel()
                notificationManager.cancel(DIGEST_ID)
                resetStoredDigest()
            }
        }
    }

    private class Entry(val feed: String, val title: String, val id: String, val date: Long)

    private fun postStored() {
        val total = prefs.getInt(KEY_COUNT, 0)
        val entries =
            prefs.getString(KEY_LINES, null).splitLines().mapNotNull { line ->
                val f = line.split(FIELD_SEP)
                if (f.size < 4) null else Entry(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0L)
            }
        if (total == 0 || entries.isEmpty()) return

        // One line per feed, most recently updated feed first.
        val groups =
            entries.groupBy { it.feed }.values.sortedByDescending { g -> g.maxOf { it.date } }
        val title = context.resources.getQuantityString(R.plurals.digest_title, total, total)
        val subtitle =
            context.resources.getQuantityString(
                R.plurals.digest_from_feeds,
                groups.size,
                groups.size,
            )

        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        val visible = groups.take(MAX_LINES)
        visible.forEach { g ->
            val latest = g.maxBy { it.date }
            inbox.addLine(
                if (g.size == 1) "${latest.feed} · ${latest.title}"
                else "${latest.feed} (${g.size}) · ${latest.title}"
            )
        }
        if (groups.size > visible.size) {
            inbox.setSummaryText(
                context.getString(R.string.digest_and_more, groups.size - visible.size)
            )
        }

        // A single new article opens directly; otherwise open the app's article list.
        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (total == 1) putExtra(ExtraName.ARTICLE_ID, entries.first().id)
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
        const val MAX_LINES = 8
        const val MAX_STORED = 300
        const val POST_DELAY_MS = 2_000L
        const val LINE_SEP = "\u001E"
        const val FIELD_SEP = "\u001F"
    }
}
