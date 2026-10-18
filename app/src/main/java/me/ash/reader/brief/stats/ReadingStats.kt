package me.ash.reader.brief.stats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One day of reading: seconds spent with an article open, and how many articles were opened. */
@Serializable
data class StatsDay(val seconds: Long = 0, val articles: Int = 0)

/** Everything Reading stats keeps. Days are ISO dates ("2026-10-18"). */
@Serializable
data class ReadingStatsData(
    val days: Map<String, StatsDay> = emptyMap(),
    /** Articles opened per feed (by name), all time. */
    val feeds: Map<String, Int> = emptyMap(),
    /** Times you reached inbox zero (mark all as read on Unread, or the end of Triage). */
    val inboxZero: Int = 0,
    /** Cards dealt with in Triage (read, saved or opened; skips don't count). */
    val triaged: Int = 0,
    /** When the stats started (epoch millis), for "since …". */
    val since: Long = 0,
)

/**
 * Reading stats, kept on the phone in Brief You's own storage (no database tables, so Read You's
 * database stays as it is). Updated from Brief You's reading page, list and Triage; shown on the
 * Reading stats page; included in the full backup.
 */
object ReadingStats {
    private const val PREFS = "reading_stats"
    private const val KEY = "data"
    /** Days kept: a bit over a year, so the file stays small. */
    private const val KEEP_DAYS = 400L
    /** Time is written every this many seconds while reading, and when the article closes. */
    const val FLUSH_SECONDS = 15

    private val json = Json { ignoreUnknownKeys = true }

    /** The current stats, for the page to show (updates as you read). */
    var data by mutableStateOf(ReadingStatsData())
        private set

    private var loaded = false

    /** Articles already counted today, so rebuilding the screen doesn't count one twice. */
    private val countedToday = mutableSetOf<String>()
    private var countedDay = ""

    @Synchronized
    fun load(context: Context): ReadingStatsData {
        if (!loaded) {
            val text = prefs(context).getString(KEY, null)
            data =
                text?.let { runCatching { json.decodeFromString<ReadingStatsData>(it) }.getOrNull() }
                    ?: ReadingStatsData(since = System.currentTimeMillis())
            loaded = true
        }
        return data
    }

    /** An article was opened in the reader. Each article counts once a day. */
    @Synchronized
    fun articleOpened(context: Context, articleId: String, feedName: String) {
        val today = today()
        if (countedDay != today) {
            countedDay = today
            countedToday.clear()
        }
        if (!countedToday.add(articleId)) return
        update(context) { d ->
            val day = d.days[today] ?: StatsDay()
            d.copy(
                days = d.days + (today to day.copy(articles = day.articles + 1)),
                feeds =
                    if (feedName.isBlank()) d.feeds
                    else d.feeds + (feedName to (d.feeds[feedName] ?: 0) + 1),
            )
        }
    }

    /** [seconds] more spent reading today. */
    @Synchronized
    fun addReadingTime(context: Context, seconds: Long) {
        if (seconds <= 0) return
        val today = today()
        update(context) { d ->
            val day = d.days[today] ?: StatsDay()
            d.copy(days = d.days + (today to day.copy(seconds = day.seconds + seconds)))
        }
    }

    @Synchronized
    fun inboxZero(context: Context) = update(context) { it.copy(inboxZero = it.inboxZero + 1) }

    /** A Triage card dealt with ([delta] 1), or brought back with Undo ([delta] -1). */
    @Synchronized
    fun triaged(context: Context, delta: Int) =
        update(context) { it.copy(triaged = (it.triaged + delta).coerceAtLeast(0)) }

    /** Starts over (from the Reading stats page). */
    @Synchronized
    fun reset(context: Context) {
        countedToday.clear()
        save(context, ReadingStatsData(since = System.currentTimeMillis()))
    }

    /** The stats as text, for the full backup. */
    @Synchronized
    fun export(context: Context): String = json.encodeToString(ReadingStatsData.serializer(), load(context))

    /** Restores stats from a backup, adding them to what's here (so nothing is lost either way). */
    @Synchronized
    fun restore(context: Context, text: String) {
        val incoming = runCatching { json.decodeFromString<ReadingStatsData>(text) }.getOrNull() ?: return
        update(context) { d ->
            val days = (d.days.keys + incoming.days.keys).associateWith { key ->
                val a = d.days[key]
                val b = incoming.days[key]
                // The same day on both: keep the bigger numbers, not their sum (it may be the
                // same reading, backed up and restored on this phone).
                StatsDay(
                    seconds = maxOf(a?.seconds ?: 0, b?.seconds ?: 0),
                    articles = maxOf(a?.articles ?: 0, b?.articles ?: 0),
                )
            }
            val feeds = (d.feeds.keys + incoming.feeds.keys).associateWith { key ->
                maxOf(d.feeds[key] ?: 0, incoming.feeds[key] ?: 0)
            }
            ReadingStatsData(
                days = days,
                feeds = feeds,
                inboxZero = maxOf(d.inboxZero, incoming.inboxZero),
                triaged = maxOf(d.triaged, incoming.triaged),
                since = listOf(d.since, incoming.since).filter { it > 0 }.minOrNull() ?: d.since,
            )
        }
    }

    private fun update(context: Context, change: (ReadingStatsData) -> ReadingStatsData) {
        save(context, change(load(context)))
    }

    private fun save(context: Context, new: ReadingStatsData) {
        val oldest = LocalDate.now().minusDays(KEEP_DAYS).toString()
        val trimmed = new.copy(days = new.days.filterKeys { it >= oldest })
        data = trimmed
        loaded = true
        prefs(context).edit().putString(KEY, json.encodeToString(ReadingStatsData.serializer(), trimmed)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun today(): String = LocalDate.now().toString()
}
