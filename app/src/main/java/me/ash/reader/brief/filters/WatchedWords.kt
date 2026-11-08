package me.ash.reader.brief.filters

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** An article a watched word matched, for Recently watched on the Filters page. */
@Serializable
data class WatchedEntry(
    val articleId: String,
    val title: String,
    val feedName: String,
    val word: String,
    val at: Long,
)

/**
 * Watched words, the opposite of muted keywords: an unread article whose title has one of them
 * gets a small chip with the word in lists, comes first in Triage and in the digest, is counted
 * on its feed and group at home, and is never muted. Whole words, any case, like muted keywords.
 * Stored next to the muted keywords; an object so lists, Triage and the digest can ask quickly.
 */
object WatchedWords {

    var words by mutableStateOf(emptyList<String>())
        private set

    /** Unread articles with a watched word, per feed and per group (shown at home). */
    var feedCounts by mutableStateOf(emptyMap<String, Int>())
        private set

    var groupCounts by mutableStateOf(emptyMap<String, Int>())
        private set

    /** Counts [titles] (the unread articles) again; called when they or the words change. */
    fun count(titles: List<me.ash.reader.brief.data.UnreadTitle>) {
        if (patterns.isEmpty()) {
            feedCounts = emptyMap()
            groupCounts = emptyMap()
            return
        }
        val hits = titles.filter { match(it.title) != null }
        feedCounts = hits.groupingBy { it.feedId }.eachCount()
        groupCounts = hits.mapNotNull { it.groupId }.groupingBy { it }.eachCount()
    }

    /** Recently watched: articles the words matched as they arrived, newest first. */
    var log by mutableStateOf(emptyList<WatchedEntry>())
        private set

    private var patterns: List<Pair<String, Regex>> = emptyList()
    private var loaded = false
    private lateinit var logFile: File
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(WatchedEntry.serializer())

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        set(prefs(context).getStringSet(KEY, emptySet()).orEmpty().toList())
        logFile = File(context.applicationContext.filesDir, "watched_log.json")
        log = runCatching { json.decodeFromString(serializer, logFile.readText()) }.getOrDefault(emptyList())
        loaded = true
    }

    /** Adds matches to Recently watched (an article once); keeps 30 days, 500 at most. */
    @Synchronized
    fun record(entries: List<WatchedEntry>) {
        if (!loaded || entries.isEmpty()) return
        val known = log.map { it.articleId }.toHashSet()
        val fresh = entries.filter { it.articleId !in known }
        if (fresh.isEmpty()) return
        val since = System.currentTimeMillis() - LOG_DAYS_MS
        writeLog((fresh + log).filter { it.at >= since }.sortedByDescending { it.at }.take(MAX_LOG))
    }

    /** Forgets deleted articles. */
    @Synchronized
    fun forget(articleIds: Set<String>) {
        if (!loaded || log.none { it.articleId in articleIds }) return
        writeLog(log.filterNot { it.articleId in articleIds })
    }

    @Synchronized
    fun clearLog() {
        if (loaded) writeLog(emptyList())
    }

    private fun writeLog(entries: List<WatchedEntry>) {
        log = entries
        runCatching { logFile.writeText(json.encodeToString(serializer, entries)) }
    }

    fun add(context: Context, word: String) {
        load(context)
        val w = word.trim()
        if (w.isEmpty()) return
        save(context, words.filterNot { it.equals(w, ignoreCase = true) } + w)
    }

    fun remove(context: Context, word: String) {
        load(context)
        save(context, words - word)
    }

    /** The watched word in [title], or null. */
    fun match(title: String?): String? {
        if (title.isNullOrEmpty()) return null
        return patterns.firstOrNull { it.second.containsMatchIn(title) }?.first
    }

    /** [items] with the ones whose title has a watched word first (otherwise in the same order). */
    fun <T> watchedFirst(items: List<T>, title: (T) -> String?): List<T> {
        if (patterns.isEmpty()) return items
        val (watched, rest) = items.partition { match(title(it)) != null }
        return watched + rest
    }

    private fun save(context: Context, list: List<String>) {
        set(list)
        prefs(context).edit { putStringSet(KEY, words.toSet()) }
    }

    private fun set(list: List<String>) {
        val sorted = list.distinct().sortedBy { it.lowercase() }
        patterns = sorted.map { it to keywordRegex(it) }
        words = sorted
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("brief_filters", Context.MODE_PRIVATE)

    /** A whole word or phrase, any case (the same as muted keywords). */
    fun keywordRegex(word: String): Regex =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private const val KEY = "watched_words"
    private const val LOG_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_LOG = 500
}
