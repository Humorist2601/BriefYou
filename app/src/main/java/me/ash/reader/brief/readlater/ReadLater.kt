package me.ash.reader.brief.readlater

import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.brief.reading.FeedContent
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.html.Readability
import me.ash.reader.ui.ext.getDefaultGroupId
import me.ash.reader.ui.ext.spacerDollar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

/**
 * Read later: a web page shared to Brief You from another app is saved as a starred article, with
 * its full text, so it reads offline like any other article.
 *
 * Saved pages belong to a feed of their own, "Saved Articles" (with a bookmark icon), that is
 * never synced (it has no real address) and lives in the default group. Only local accounts can save: a sync service
 * (FreshRSS, Fever) would drop articles it doesn't know about at the next sync.
 */
@Singleton
class ReadLater
@Inject
constructor(
    private val accountService: AccountService,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val okHttpClient: OkHttpClient,
    @param:IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** A saved page, for the confirmation sheet. */
    data class Saved(
        val articleId: String,
        val feedId: String,
        val title: String,
        val site: String,
        val image: String?,
        val minutes: Int?,
        val alreadySaved: Boolean,
        /** False when the page couldn't be loaded now and only the link was saved. */
        val fetched: Boolean = true,
    )

    class NotLocalAccount : Exception()

    /** Whether the current account can save pages (only local accounts). */
    suspend fun canSave(): Boolean =
        withContext(ioDispatcher) { accountService.getCurrentAccount().type.id == AccountType.Local.id }

    /**
     * Saves the page at [url] (fetched now, with its full text) as a starred article. When the
     * page can't be loaded (offline, blocked, or not a web page, like a PDF), the link is saved
     * anyway, titled [sharedTitle] when the sharing app gave one, so nothing shared is lost.
     */
    suspend fun save(url: String, sharedTitle: String? = null): Saved =
        withContext(ioDispatcher) {
            val account = accountService.getCurrentAccount()
            if (account.type.id != AccountType.Local.id) throw NotLocalAccount()
            val accountId = account.id!!
            val feed = savedFeed(accountId)

            // Saved before: starred (again) and shown as it is.
            articleDao.queryArticlesByLinks(listOf(url), feed.id, accountId).firstOrNull()?.let {
                if (!it.isStarred) articleDao.update(it.copy(isStarred = true))
                return@withContext Saved(
                    articleId = it.id,
                    feedId = feed.id,
                    title = it.title,
                    site = siteName(null, url),
                    image = it.img,
                    minutes = me.ash.reader.brief.reading.estimateReadingMinutes(it.rawDescription),
                    alreadySaved = true,
                )
            }

            // A web page, read up to a size limit (a shared link to a big file can't run the app
            // out of memory); anything else saves the link only.
            val html =
                runCatching {
                        okHttpClient.newCall(Request.Builder().url(url).build()).executeAsync().use {
                            if (!it.isSuccessful) throw IOException(it.message)
                            val type = it.body.contentType()
                            if (type != null && type.subtype != "html" && type.subtype != "xhtml+xml") {
                                throw IOException("Not a web page: $type")
                            }
                            it.peekBody(MAX_PAGE_BYTES).string()
                        }
                    }
                    .getOrNull()
                    ?: return@withContext saveLinkOnly(url, sharedTitle, accountId, feed)
            val page = Jsoup.parse(html, url)
            val title = meta(page, "og:title") ?: page.title().takeIf { it.isNotBlank() } ?: url
            val description = meta(page, "og:description") ?: meta(page, "description")
            val image = FeedContent.listImage(meta(page, "og:image"), url)
            val content =
                Readability.parseToElement(html, url)
                    ?.apply { selectFirst("h1")?.takeIf { it.text() == title }?.remove() }
                    ?.toString()
                    ?: description?.let { "<p>${Entities.escape(it)}</p>" }
                    ?: ""
            val now = Date()
            val article =
                Article(
                    id = accountId.spacerDollar(UUID.randomUUID().toString()),
                    accountId = accountId,
                    feedId = feed.id,
                    date = now,
                    title = title,
                    author = meta(page, "author") ?: meta(page, "article:author"),
                    rawDescription = content,
                    shortDescription =
                        (description ?: Readability.parseToText(content, url)).take(280),
                    img = image,
                    link = url,
                    isUnread = true,
                    isStarred = true,
                    updateAt = now,
                )
            articleDao.insert(article)
            Saved(
                articleId = article.id,
                feedId = feed.id,
                title = title,
                site = siteName(page, url),
                image = image,
                minutes = me.ash.reader.brief.reading.estimateReadingMinutes(content),
                alreadySaved = false,
            )
        }

    /** Saves only the link (the page couldn't be loaded): open it later to read it. */
    private suspend fun saveLinkOnly(url: String, sharedTitle: String?, accountId: Int, feed: Feed): Saved {
        val title =
            sharedTitle?.trim()?.takeIf { it.isNotEmpty() && it != url }
                ?: runCatching { java.net.URI(url).let { it.host.removePrefix("www.") + it.path.trimEnd('/') } }.getOrNull()
                ?: url
        val now = Date()
        val article =
            Article(
                id = accountId.spacerDollar(UUID.randomUUID().toString()),
                accountId = accountId,
                feedId = feed.id,
                date = now,
                title = title,
                rawDescription = "<p><a href=\"${Entities.escape(url)}\">${Entities.escape(url)}</a></p>",
                shortDescription = url,
                link = url,
                isUnread = true,
                isStarred = true,
                updateAt = now,
            )
        articleDao.insert(article)
        return Saved(
            articleId = article.id,
            feedId = feed.id,
            title = title,
            site = siteName(null, url),
            image = null,
            minutes = null,
            alreadySaved = false,
            fetched = false,
        )
    }

    /** The "Saved Articles" feed of [accountId], for restoring a backup's saved pages. */
    suspend fun savedFeedFor(accountId: Int): Feed = withContext(ioDispatcher) { savedFeed(accountId) }

    /** The "Saved Articles" feed of [accountId], made the first time something is saved. */
    private suspend fun savedFeed(accountId: Int): Feed {
        val id = accountId.spacerDollar(SAVED_FEED)
        feedDao.queryById(id)?.let { return upToDate(it) }
        val feed =
            Feed(
                id = id,
                name = SAVED_FEED_NAME,
                icon = SAVED_FEED_ICON,
                url = SAVED_FEED_URL,
                groupId = accountId.getDefaultGroupId(),
                accountId = accountId,
            )
        feedDao.insert(feed)
        return feed
    }

    /**
     * Gives an existing saved feed of the current account its current name and icon (it was
     * called "Saved for later", without an icon, in a test build). A custom icon you set is kept.
     */
    suspend fun refreshSavedFeed() =
        withContext(ioDispatcher) {
            runCatching {
                val accountId = accountService.getCurrentAccountId()
                feedDao.queryById(accountId.spacerDollar(SAVED_FEED))?.let { upToDate(it) }
            }
        }

    private suspend fun upToDate(feed: Feed): Feed {
        val icon = feed.icon?.takeIf { it != OLD_SAVED_FEED_ICON } ?: SAVED_FEED_ICON
        if (feed.name == SAVED_FEED_NAME && feed.icon == icon) return feed
        return feed.copy(name = SAVED_FEED_NAME, icon = icon).also { feedDao.update(it) }
    }

    private fun meta(page: Document, name: String): String? =
        (page.selectFirst("meta[property=\"$name\"]") ?: page.selectFirst("meta[name=\"$name\"]"))
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun siteName(page: Document?, url: String): String =
        page?.let { meta(it, "og:site_name") }
            ?: runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull()
            ?: url

    companion object {
        private const val SAVED_FEED = "brief_you_saved_for_later"
        /** Pages are read up to this size. */
        private const val MAX_PAGE_BYTES = 5L * 1024 * 1024
        private const val SAVED_FEED_NAME = "Saved Articles"
        /** A bookmark icon, drawn by FeedIcon. */
        private const val SAVED_FEED_ICON = me.ash.reader.ui.component.SAVED_ARTICLES_ICON

        /** The emoji icon the feed had in a test build, replaced by the bookmark icon. */
        private const val OLD_SAVED_FEED_ICON = "emoji:\uD83D\uDD16"

        /** Not a real address: the saved feed is never fetched (see [isSavedFeed]). */
        const val SAVED_FEED_URL = "briefyou://saved-for-later"

        /** Whether [feed] is the saved pages' feed, which syncing and OPML exports skip. */
        fun isSavedFeed(feed: Feed): Boolean = feed.url == SAVED_FEED_URL

        /** The web address in text shared from another app ("Title https://…" counts too). */
        fun findUrl(text: String?): String? =
            text?.let { Regex("""https?://\S+""").find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }
    }
}
