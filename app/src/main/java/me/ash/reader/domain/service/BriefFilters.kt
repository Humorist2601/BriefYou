package me.ash.reader.domain.service

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.repository.ArticleDao
import timber.log.Timber

/**
 * Brief You's filters, applied after every sync:
 * - **Mute keywords**: unread articles whose title or description contains a muted word are
 *   marked as read.
 * - **Duplicate stories**: when several feeds publish the same story (very similar titles within a
 *   few days), only the first copy stays unread; later copies are marked as read.
 *
 * Marking read goes through the same path as swiping an article, so it syncs to the account.
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

    private val _mutedKeywords =
        MutableStateFlow(prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty().sorted())
    val mutedKeywords: StateFlow<List<String>> = _mutedKeywords.asStateFlow()

    private val _hideDuplicates = MutableStateFlow(prefs.getBoolean(KEY_DUPES, false))
    val hideDuplicates: StateFlow<Boolean> = _hideDuplicates.asStateFlow()

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
            val keywords = _mutedKeywords.value
            val dedupe = _hideDuplicates.value
            if (keywords.isEmpty() && !dedupe) return@withContext 0

            val since = Date(System.currentTimeMillis() - WINDOW_MS)
            // Newest first from the database; oldest first for duplicate detection.
            val recent = articleDao.queryRecentForFilters(accountId, since, MAX_ARTICLES).asReversed()
            val toMark = HashSet<String>()

            if (keywords.isNotEmpty()) {
                val patterns = keywords.map { keywordRegex(it) }
                recent
                    .filter { it.article.isUnread }
                    .filter { a ->
                        val text = a.article.title + "\n" + a.article.shortDescription
                        patterns.any { it.containsMatchIn(text) }
                    }
                    .mapTo(toMark) { it.article.id }
            }

            if (dedupe) toMark += findDuplicates(recent)

            if (toMark.isNotEmpty()) {
                Timber.d("BriefFilters: marking ${toMark.size} articles as read")
                rssService.get().batchMarkAsRead(articleIds = toMark, isUnread = false)
            }
            toMark.size
        }

    /**
     * Articles are in date order. An unread article is a duplicate when an earlier article from a
     * different feed has a very similar title.
     */
    private fun findDuplicates(recent: List<ArticleWithFeed>): Set<String> {
        val seen = ArrayList<Pair<String, Set<String>>>() // feedId to title tokens
        val dupes = HashSet<String>()
        for (a in recent) {
            val tokens = tokens(a.article.title)
            if (tokens.size < MIN_TOKENS) {
                continue
            }
            val isDupe =
                seen.any { (feedId, other) ->
                    feedId != a.article.feedId && jaccard(tokens, other) >= SIMILARITY
                }
            if (isDupe && a.article.isUnread) dupes += a.article.id
            seen += a.article.feedId to tokens
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
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private companion object {
        const val KEY_MUTED = "muted_keywords"
        const val KEY_DUPES = "hide_duplicates"
        const val WINDOW_MS = 3L * 24 * 60 * 60 * 1000
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
