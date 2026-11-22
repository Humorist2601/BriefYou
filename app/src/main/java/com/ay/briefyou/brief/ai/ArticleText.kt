package com.ay.briefyou.brief.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.ay.briefyou.domain.model.article.Article
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.rss.ReaderCacheHelper
import org.jsoup.Jsoup

/**
 * The text the AI works on. Many feeds only send a teaser of two or three sentences, and a summary
 * of a teaser is thin or guessed. So when everything at hand is short, the full article is
 * fetched (with the original full-content reader, and kept in its cache, so opening the article
 * later is instant). Otherwise, or when fetching fails, the longest text at hand is used.
 */
@Singleton
class ArticleText
@Inject
constructor(
    private val readerCache: ReaderCacheHelper,
    private val rssService: RssService,
) {
    /** [shown] is what the reading page shows, if any (full content when that's on). */
    suspend fun best(article: Article, shown: String? = null): String =
        withContext(Dispatchers.IO) {
            val text = bestRaw(article, shown)
            // A newsletter's email: its text without footers, Outlook settings and hidden preview.
            if (com.ay.briefyou.brief.reading.EmailContent.isEmail(text, article.link)) {
                com.ay.briefyou.brief.reading.FeedContent.forReader(text, article.link)
            } else {
                text
            }
        }

    /**
     * [article] with its text: list rows and Triage cards leave the text out (so they load
     * quickly), so it is read from the database by id when it is needed.
     */
    private suspend fun withText(article: Article): Article =
        if (article.rawDescription.isNotEmpty()) article
        else runCatching { rssService.get().findArticleById(article.id)?.article }.getOrNull() ?: article

    /** [item] with its article's text (see [withText]). */
    suspend fun withTextOf(item: ArticleWithFeed): ArticleWithFeed =
        withContext(Dispatchers.IO) {
            if (item.article.rawDescription.isNotEmpty()) item else item.copy(article = withText(item.article))
        }

    private suspend fun bestRaw(listArticle: Article, shown: String?): String =
        withContext(Dispatchers.IO) {
            val article = withText(listArticle)
            val candidates = listOfNotNull(shown, article.rawDescription).filter { it.isNotBlank() }
            // Each text is read once (it used to be read again for every comparison).
            val measured = candidates.map { it to textLength(it) }
            val best = measured.maxByOrNull { it.second }
            val longest = best?.first.orEmpty()
            val longestLength = best?.second ?: 0
            if (longestLength >= TEASER_CHARS || !article.link.startsWith("http")) return@withContext longest
            val full =
                withTimeoutOrNull(FETCH_TIMEOUT_MS) { readerCache.readOrFetchFullContent(article).getOrNull() }
            if (full != null && textLength(full) > longestLength) full else longest
        }

    /** Whether [best] would fetch the full article (all the text at hand is a teaser). */
    suspend fun needsFullText(listArticle: Article, shown: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val article = withText(listArticle)
            article.link.startsWith("http") &&
                listOfNotNull(shown, article.rawDescription).maxOfOrNull { textLength(it) }.let { (it ?: 0) < TEASER_CHARS }
        }

    /** The same, for an article known by its id (the reading page's summary sheet). */
    suspend fun best(articleId: String, shown: String): String {
        val article = runCatching { rssService.get().findArticleById(articleId)?.article }.getOrNull() ?: return shown
        return best(article, shown)
    }

    private fun textLength(html: String): Int =
        runCatching { Jsoup.parse(html).apply { select("script, style, figure, img").remove() }.text().length }
            .getOrDefault(html.length)

    private companion object {
        /** About 250 words: less than this is a teaser, not an article. */
        const val TEASER_CHARS = 1_500
        const val FETCH_TIMEOUT_MS = 20_000L
    }
}
