package me.ash.reader.domain.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.repository.ArticleDao
import timber.log.Timber

/** Why an article was filtered. */
@Serializable
enum class FilterReason {
    MutedKeyword,
    Duplicate,
}

/**
 * One filtered article, kept so the user can see what was hidden and why, and undo it.
 *
 * @param detail the matched keyword, or the title of the story it duplicated.
 * @param detailFeed for duplicates, the feed that published the story first.
 */
@Serializable
data class FilterLogEntry(
    val articleId: String,
    val title: String,
    val feedName: String,
    val reason: FilterReason,
    val detail: String,
    val detailFeed: String? = null,
    val at: Long,
)

/**
 * Brief You's filters, applied after every sync:
 * - **Mute keywords**: unread articles whose title or description contains a muted word are
 *   marked as read.
 * - **Duplicate stories**: when several feeds publish the same story (very similar titles, the
 *   copy published within 48 hours after the first), only the first copy stays unread; later
 *   copies are marked as read.
 *
 * Marking read goes through the same path as swiping an article, so it syncs to the account.
 * Every filtered article is recorded in [log] with its reason. [undo] brings one back as unread
 * and exempts it, so later syncs don't filter it again; [resetExemptions] lets the filters catch
 * those articles again. Log entries and exemptions are removed together with their articles
 * ([forget], [prune]).
 */
@Singleton
class BriefFilters
@Inject
constructor(
    @ApplicationContext context: Context,
    private val articleDao: ArticleDao,
    private val rssService: RssService,
) {
    private val prefs = context.getSharedPreferences("brief_filters", Context.MODE_PRIVATE)
    private val logFile = File(context.filesDir, "filter_log.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _mutedKeywords =
        MutableStateFlow(prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty().sorted())
    val mutedKeywords: StateFlow<List<String>> = _mutedKeywords.asStateFlow()

    private val _hideDuplicates = MutableStateFlow(prefs.getBoolean(KEY_DUPES, false))
    val hideDuplicates: StateFlow<Boolean> = _hideDuplicates.asStateFlow()

    private val _exemptCount =
        MutableStateFlow(prefs.getStringSet(KEY_EXEMPT, emptySet()).orEmpty().size)
    /** How many articles were brought back with Undo, which the filters now skip. */
    val exemptCount: StateFlow<Int> = _exemptCount.asStateFlow()

    private val _log = MutableStateFlow(readLog())
    /** Recently filtered articles, newest first. */
    val log: StateFlow<List<FilterLogEntry>> = _log.asStateFlow()

    fun addKeyword(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        save(_mutedKeywords.value.filterNot { it.equals(w, ignoreCase = true) } + w)
    }

    fun removeKeyword(word: String) = save(_mutedKeywords.value - word)

    fun setHideDuplicates(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DUPES, enabled) }
        _hideDuplicates.value = enabled
    }

    private fun save(list: List<String>) {
        val sorted = list.distinct().sortedBy { it.lowercase() }
        prefs.edit { putStringSet(KEY_MUTED, sorted.toSet()) }
        _mutedKeywords.value = sorted
    }

    /** Applies the filters to recent articles. Returns how many were marked read. */
    suspend fun apply(accountId: Int): Int =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val keywords = _mutedKeywords.value
                val dedupe = _hideDuplicates.value
                if (keywords.isEmpty() && !dedupe) return@withLock 0

                val exempt = prefs.getStringSet(KEY_EXEMPT, emptySet()).orEmpty()
                val since = Date(System.currentTimeMillis() - WINDOW_MS)
                // Newest first from the database; oldest first for duplicate detection.
                val recent =
                    articleDao.queryRecentForFilters(accountId, since, MAX_ARTICLES).asReversed()
                val now = System.currentTimeMillis()
                val found = LinkedHashMap<String, FilterLogEntry>()

                if (keywords.isNotEmpty()) {
                    val patterns = keywords.map { it to keywordRegex(it) }
                    for (a in recent) {
                        if (!a.article.isUnread || a.article.id in exempt) continue
                        val text = a.article.title + "\n" + a.article.shortDescription
                        val hit = patterns.firstOrNull { it.second.containsMatchIn(text) } ?: continue
                        found[a.article.id] =
                            FilterLogEntry(
                                articleId = a.article.id,
                                title = a.article.title,
                                feedName = a.feed.name,
                                reason = FilterReason.MutedKeyword,
                                detail = hit.first,
                                at = now,
                            )
                    }
                }

                if (dedupe) {
                    for ((dupe, original) in findDuplicates(recent)) {
                        if (dupe.article.id in exempt || dupe.article.id in found) continue
                        found[dupe.article.id] =
                            FilterLogEntry(
                                articleId = dupe.article.id,
                                title = dupe.article.title,
                                feedName = dupe.feed.name,
                                reason = FilterReason.Duplicate,
                                detail = original.article.title,
                                detailFeed = original.feed.name,
                                at = now,
                            )
                    }
                }

                if (found.isNotEmpty()) {
                    Timber.d("BriefFilters: marking ${found.size} articles as read")
                    rssService.get().batchMarkAsRead(articleIds = found.keys, isUnread = false)
                    val known = found.keys
                    writeLog(found.values.toList() + _log.value.filterNot { it.articleId in known })
                }
                found.size
            }
        }

    /** Brings a filtered article back as unread and stops it from being filtered again. */
    suspend fun undo(entry: FilterLogEntry) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                rssService.get().batchMarkAsRead(articleIds = setOf(entry.articleId), isUnread = true)
                val exempt = prefs.getStringSet(KEY_EXEMPT, emptySet()).orEmpty() + entry.articleId
                saveExempt(exempt)
                writeLog(_log.value.filterNot { it.articleId == entry.articleId })
            }
        }

    /**
     * Forgets log entries and Undo exemptions for these articles. Called when articles are
     * deleted (for example by "Keep archived articles"), so the log lives exactly as long as
     * the articles it lists, like saved summaries.
     */
    suspend fun forget(articleIds: Collection<String>) =
        withContext(Dispatchers.IO) {
            if (articleIds.isEmpty()) return@withContext
            val gone = articleIds.toHashSet()
            lock.withLock {
                val exempt = prefs.getStringSet(KEY_EXEMPT, emptySet()).orEmpty()
                if (exempt.any { it in gone }) {
                    saveExempt(exempt.filterNot { it in gone }.toSet())
                }
                if (_log.value.any { it.articleId in gone }) {
                    writeLog(_log.value.filterNot { it.articleId in gone })
                }
            }
        }

    @Volatile private var pruned = false

    /** Removes entries whose article no longer exists. Runs once per app start. */
    suspend fun prune() =
        withContext(Dispatchers.IO) {
            if (pruned) return@withContext
            pruned = true
            val exempt = prefs.getStringSet(KEY_EXEMPT, emptySet()).orEmpty()
            if (_log.value.isEmpty() && exempt.isEmpty()) return@withContext
            val live = articleDao.queryAllIds().toHashSet()
            forget((_log.value.map { it.articleId } + exempt).filterNot { it in live })
        }

    /** Lets the filters catch the articles brought back with Undo again (from the next run). */
    suspend fun resetExemptions() =
        withContext(Dispatchers.IO) { lock.withLock { saveExempt(emptySet()) } }

    private fun saveExempt(exempt: Set<String>) {
        prefs.edit { putStringSet(KEY_EXEMPT, exempt) }
        _exemptCount.value = exempt.size
    }

    suspend fun clearLog() =
        withContext(Dispatchers.IO) { lock.withLock { writeLog(emptyList()) } }

    /**
     * Articles are in date order. An unread article is a duplicate when an article from a
     * different feed with a very similar title was published up to 48 hours before it. Returns
     * (duplicate, the earlier original) pairs.
     */
    private fun findDuplicates(
        recent: List<ArticleWithFeed>
    ): List<Pair<ArticleWithFeed, ArticleWithFeed>> {
        val seen = ArrayList<Pair<ArticleWithFeed, Set<String>>>()
        val dupes = ArrayList<Pair<ArticleWithFeed, ArticleWithFeed>>()
        for (a in recent) {
            val tokens = tokens(a.article.title)
            if (tokens.size < MIN_TOKENS) continue
            val time = a.article.date.time
            val original =
                seen.firstOrNull { (other, otherTokens) ->
                    other.article.feedId != a.article.feedId &&
                        time - other.article.date.time <= DUPLICATE_WINDOW_MS &&
                        jaccard(tokens, otherTokens) >= SIMILARITY
                }
            if (original != null) {
                if (a.article.isUnread) dupes += a to original.first
            } else {
                seen += a to tokens
            }
        }
        return dupes
    }

    private fun tokens(title: String): Set<String> =
        Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOPWORDS }
            .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    private fun keywordRegex(word: String): Regex =
        Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )

    private fun readLog(): List<FilterLogEntry> =
        runCatching {
                if (logFile.isFile) json.decodeFromString(LOG_SERIALIZER, logFile.readText())
                else emptyList()
            }
            .getOrDefault(emptyList())

    private fun writeLog(entries: List<FilterLogEntry>) {
        runCatching { logFile.writeText(json.encodeToString(LOG_SERIALIZER, entries)) }
            .onFailure { Timber.w(it, "could not save filter log") }
        _log.value = entries
    }

    private companion object {
        val LOG_SERIALIZER = ListSerializer(FilterLogEntry.serializer())
        const val KEY_MUTED = "muted_keywords"
        const val KEY_DUPES = "hide_duplicates"
        const val KEY_EXEMPT = "exempt_ids"
        /** How far back the filters look (each run checks the articles of the last 3 days). */
        const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000
        /** A copy of a story counts as a duplicate up to 48 hours after the first. */
        const val DUPLICATE_WINDOW_MS = 48L * 60 * 60 * 1000
        const val MAX_ARTICLES = 1500
        const val MIN_TOKENS = 4
        const val SIMILARITY = 0.6
        val STOPWORDS =
            setOf(
                "the", "and", "for", "with", "from", "that", "this", "are", "was", "will", "has",
                "have", "its", "into", "after", "over", "about", "new", "says", "say", "how",
                "why", "what", "who", "you", "your", "not", "but", "out", "more",
            )
    }
}
