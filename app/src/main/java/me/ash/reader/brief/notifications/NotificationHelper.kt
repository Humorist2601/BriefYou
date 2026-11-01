package me.ash.reader.brief.notifications

import me.ash.reader.infrastructure.android.MainActivity // Brief You

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
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
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val coroutineScope: CoroutineScope,
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

    /** Syncs running now; while any runs, new articles are collected but nothing is posted. */
    private var runningSyncs = 0

    /** Articles were collected during a sync and are waiting for it to finish. */
    private var pendingPost = false

    fun notify(feed: Feed, articles: List<Article>) {
        if (!notificationManager.areNotificationsEnabled()) return
        if (articles.isEmpty()) return
        if (!feed.isNotification) return
        coroutineScope.launch {
            mutex.withLock {
                Timber.d("digest: +${articles.size} from ${feed.name}")
                // Start fresh only if the user swiped the digest away AND no post is pending.
                if (postJob?.isActive != true && !pendingPost && !isDigestShowing()) {
                    resetStoredDigest()
                }

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

                if (runningSyncs > 0) {
                    // Brief You: during a sync, post only once it has finished (syncFinished).
                    pendingPost = true
                } else {
                    // Outside a sync: wait for any other feeds, then post once.
                    postJob?.cancel()
                    postJob =
                        coroutineScope.launch {
                            delay(POST_DELAY_MS)
                            mutex.withLock { postStored() }
                        }
                }
            }
        }
    }

    /** A sync is starting: hold any notification until it has finished. */
    suspend fun syncStarted() {
        mutex.withLock {
            runningSyncs++
            postJob?.cancel()
        }
    }

    /**
     * A sync (and everything after it, like the filters) has finished: post what it found, once.
     * Articles reported a moment later still get posted, shortly after.
     */
    suspend fun syncFinished() {
        mutex.withLock {
            runningSyncs = (runningSyncs - 1).coerceAtLeast(0)
            if (runningSyncs == 0 && pendingPost) {
                pendingPost = false
                postStored()
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

    /** The ids of the articles in the digest (for Mark all read). */
    suspend fun digestArticleIds(): Set<String> =
        mutex.withLock {
            prefs.getString(KEY_LINES, null).splitLines().mapNotNull { line ->
                line.split(FIELD_SEP).getOrNull(2)
            }.toSet()
        }

    private fun postStored() {
        val total = prefs.getInt(KEY_COUNT, 0)
        val entries =
            prefs.getString(KEY_LINES, null).splitLines().mapNotNull { line ->
                val f = line.split(FIELD_SEP)
                if (f.size < 4) null else Entry(f[0], f[1], f[2], f[3].toLongOrNull() ?: 0L)
            }
        if (total == 0 || entries.isEmpty()) return
        post(total, entries, conversation = true)
    }

    /**
     * Posts a made-up digest (8 feeds, 21 articles), to see how it looks; Brief You Nightly only
     * (see DigestPreviewReceiver). [conversation] picks the style.
     */
    fun postSample(conversation: Boolean) {
        val now = System.currentTimeMillis()
        val sample =
            listOf(
                "Variety" to "Godzilla Minus Zero cast got a standing ovation at the Tokyo festival",
                "Kagi News" to "World leaders open the climate summit as talks stall on funding",
                "Kotaku" to "Valve's cancelled Portal prequel F-Stop has leaked",
                "Daily Briefs" to "Daily Hacker News for 2026-10-31",
                "Rock Paper Shotgun" to "The Sunday Papers",
                "Wikipedia" to "November 1 Wikipedia featured article",
                "XKCD" to "Earth Sized Planets",
                "Reddit Comics" to "Happy Halloween 2026",
            )
        val counts = listOf(5, 6, 3, 2, 1, 1, 1, 2)
        val entries =
            sample.flatMapIndexed { i, (feed, title) ->
                List(counts[i]) { n -> Entry(feed, title, "sample-$i-$n", now - i * 60_000L - n) }
            }
        post(entries.size, entries, conversation)
    }

    private fun post(total: Int, entries: List<Entry>, conversation: Boolean) {
        // One line per feed, most recently updated feed first.
        val groups =
            entries.groupBy { it.feed }.values.sortedByDescending { g -> g.maxOf { it.date } }
        val feedNames = groups.map { it.first().feed }
        val title = context.resources.getQuantityString(R.plurals.digest_title, total, total)
        // Collapsed: which feeds, not just how many.
        val collapsed =
            feedNames.take(COLLAPSED_FEEDS).joinToString(", ") +
                if (feedNames.size > COLLAPSED_FEEDS) " +${feedNames.size - COLLAPSED_FEEDS}" else ""

        val style = if (conversation) conversationStyle(title, groups, feedNames) else listStyle(title, groups, feedNames)

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
                .setContentText(collapsed)
                .setNumber(total)
                .setStyle(style)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .addAction(
                    R.drawable.ic_notification,
                    context.getString(R.string.digest_mark_all_read),
                    PendingIntent.getBroadcast(
                        context,
                        DIGEST_ID,
                        Intent(context, DigestActionReceiver::class.java)
                            .setAction(DigestActionReceiver.ACTION_MARK_ALL_READ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .addAction(
                    R.drawable.ic_shortcut_triage,
                    context.getString(R.string.shortcut_triage),
                    PendingIntent.getActivity(
                        context,
                        DIGEST_ID + 1,
                        me.ash.reader.brief.app.BriefShortcuts.intent(
                            context,
                            me.ash.reader.brief.app.BriefShortcuts.Action.Triage,
                        ),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
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

    /**
     * The list: one line per feed, its name in bold (some Androids, like GrapheneOS, drop the
     * bold here). Android shows at most 7 lines, so 6 feeds and a 7th line naming the rest.
     */
    private fun listStyle(
        title: String,
        groups: List<List<Entry>>,
        feedNames: List<String>,
    ): NotificationCompat.Style {
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        val shown = if (groups.size > MAX_LINES + 1) groups.take(MAX_LINES) else groups
        shown.forEach { g ->
            val latest = g.maxBy { it.date }
            inbox.addLine(
                SpannableStringBuilder()
                    .append(latest.feed, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    .append(if (g.size > 1) " (${g.size})" else "")
                    .append(" · ")
                    .append(latest.title)
            )
        }
        if (groups.size > shown.size) {
            val rest = feedNames.drop(shown.size)
            inbox.addLine(
                context.getString(R.string.digest_more_feeds, rest.size, rest.joinToString(", "))
            )
        }

        return inbox
    }

    /**
     * Like a conversation: each feed is a "sender", which Android itself draws in bold with a
     * round letter icon, so it looks the same on every phone. The newest feed comes last (where
     * the collapsed notification shows it); about 4 feeds fit, after a line naming the rest.
     */
    private fun conversationStyle(
        title: String,
        groups: List<List<Entry>>,
        feedNames: List<String>,
    ): NotificationCompat.Style {
        val me = Person.Builder().setName(context.getString(R.string.app_name)).setKey("me").build()
        val style =
            NotificationCompat.MessagingStyle(me)
                .setConversationTitle(title)
                .setGroupConversation(true)
        val shown = if (groups.size > CONVERSATION_FEEDS + 1) groups.take(CONVERSATION_FEEDS) else groups
        val oldest = groups.minOf { g -> g.minOf { it.date } }
        if (groups.size > shown.size) {
            val rest = feedNames.drop(shown.size)
            style.addMessage(
                rest.joinToString(", "),
                oldest - 1,
                Person.Builder()
                    .setName(context.getString(R.string.digest_more_feeds_sender, rest.size))
                    .setKey("more")
                    .setIcon(IconCompat.createWithBitmap(letterIcon("+", MORE_COLOR)))
                    .build(),
            )
        }
        shown.reversed().forEach { g ->
            val latest = g.maxBy { it.date }
            style.addMessage(
                latest.title,
                latest.date,
                Person.Builder()
                    .setName(if (g.size > 1) "${latest.feed} · ${g.size}" else latest.feed)
                    .setKey(latest.feed)
                    .setIcon(IconCompat.createWithBitmap(letterIcon(latest.feed.take(1), colorFor(latest.feed))))
                    .build(),
            )
        }
        return style
    }

    /** A round icon with [letter] on [color], like Brief You's letter feed icons. */
    private fun letterIcon(letter: String, color: Int): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = 0xFF1D1B20.toInt()
        paint.textSize = size * 0.46f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter.uppercase(), size / 2f, y, paint)
        return bitmap
    }

    /** A soft colour for a feed, the same every time. */
    private fun colorFor(feed: String): Int = LETTER_COLORS[(feed.hashCode() and 0x7FFFFFFF) % LETTER_COLORS.size]

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
        const val KEY_LINES = "lines"
        /** Feed lines in the expanded digest; with the "+N more" line, Android's 7 at most. */
        const val MAX_LINES = 6
        /** Feeds shown as conversation lines, before the line naming the rest. */
        const val CONVERSATION_FEEDS = 4
        const val MORE_COLOR = 0xFFCAC4D0.toInt()
        /** Pastel tones (Material's container colours) for the letter icons. */
        val LETTER_COLORS =
            intArrayOf(
                0xFFF2B8B5.toInt(), 0xFFA8C7FA.toInt(), 0xFFFFD8A8.toInt(), 0xFFB7E4B0.toInt(),
                0xFFD0BCFF.toInt(), 0xFFFFD8E4.toInt(), 0xFF9EEFFD.toInt(), 0xFFE6E0A0.toInt(),
            )
        /** Feeds named in the collapsed digest. */
        const val COLLAPSED_FEEDS = 4
        const val MAX_STORED = 300
        const val POST_DELAY_MS = 2_000L
        const val LINE_SEP = "\u001E"
        const val FIELD_SEP = "\u001F"
    }
}
