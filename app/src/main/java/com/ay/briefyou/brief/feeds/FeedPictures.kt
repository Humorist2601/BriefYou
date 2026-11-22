package com.ay.briefyou.brief.feeds

import android.content.Context
import androidx.core.content.edit
import com.rometools.modules.itunes.FeedInformation
import com.rometools.modules.itunes.ITunes
import com.rometools.rome.feed.synd.SyndFeed

/**
 * A feed's own picture (a podcast's cover, the feed's logo), kept by feed address, so the feed
 * can use it instead of its website's icon: chosen in Add a feed and in Change icon ("Feed's
 * picture" | "Website icon"). The original app always used the website's icon. Noted when a feed is found
 * and on each sync of a local feed. Brief You.
 */
object FeedPictures {
    private const val PREFS = "brief_feed_pictures"

    /** The feed's own picture: its image (RSS image, Atom logo), its podcast cover, or its Atom icon. */
    fun of(feed: SyndFeed): String? =
        listOfNotNull(
                feed.image?.url,
                runCatching { (feed.getModule(ITunes.URI) as? FeedInformation)?.imageUri }.getOrNull(),
                feed.icon?.url,
            )
            .map { it.trim() }
            .firstOrNull { it.startsWith("http", ignoreCase = true) }

    /** Notes the picture of the feed at [feedUrl] (or forgets it when it has none). */
    fun found(context: Context, feedUrl: String, feed: SyndFeed) {
        val picture = runCatching { of(feed) }.getOrNull()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(feedUrl, null) == picture) return
        prefs.edit { if (picture == null) remove(feedUrl) else putString(feedUrl, picture) }
    }

    /** Feed address → when Add a feed chose its picture. */
    private const val CHOSEN = "brief_feed_pictures_chosen"
    private const val KEEP_CHOSEN_MS = 7 * 24 * 60 * 60 * 1000L

    private fun chosenPrefs(context: Context) = context.getSharedPreferences(CHOSEN, Context.MODE_PRIVATE)

    /** Add a feed chose the feed's picture: kept as its icon once the feed is there (see FeedIconOverrides.reapply). */
    fun choose(context: Context, feedUrl: String) = chosenPrefs(context).edit { putLong(feedUrl, System.currentTimeMillis()) }

    /** The addresses still waiting for their feed; ones older than a week (a subscribe that failed) are dropped. */
    fun chosen(context: Context): Set<String> {
        val prefs = chosenPrefs(context)
        val old = System.currentTimeMillis() - KEEP_CHOSEN_MS
        val (keep, drop) = prefs.all.entries.partition { (it.value as? Long ?: 0L) > old }
        if (drop.isNotEmpty()) prefs.edit { drop.forEach { remove(it.key) } }
        return keep.map { it.key }.toSet()
    }

    fun unchoose(context: Context, feedUrl: String) = chosenPrefs(context).edit { remove(feedUrl) }

    /**
     * The ways a server may write the same address: http or https, with or without "www." and a
     * trailing slash.
     */
    fun variants(url: String): List<String> {
        val bare = url.trim().removeSuffix("/").replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "").removePrefix("www.")
        return listOf("https://", "http://").flatMap { scheme ->
            listOf(bare, "www.$bare").flatMap { listOf(scheme + it, "$scheme$it/") }
        }
    }

    /** Found by Add a feed: kept in memory until the feed is subscribed to ([keep]). */
    private val found = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Add a feed found the feed at [feedUrl]: its picture is offered, but only kept on subscribe. */
    fun foundForAdding(feedUrl: String, feed: SyndFeed) {
        runCatching { of(feed) }.getOrNull()?.let { found[feedUrl] = it } ?: found.remove(feedUrl)
    }

    /** Subscribed: the picture found for it is kept. */
    fun keep(context: Context, feedUrl: String) {
        val picture = found.remove(feedUrl) ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(feedUrl, picture) }
    }

    /** The feed was deleted: its picture is forgotten. */
    fun forget(context: Context, feedUrl: String?) {
        feedUrl ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(feedUrl) }
    }

    /** Forgets the pictures of feeds that no longer exist ([feedUrls]: every feed's address). */
    fun sweep(context: Context, feedUrls: Collection<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = feedUrls.toHashSet()
        val gone = prefs.all.keys.filterNot { it in known }
        if (gone.isNotEmpty()) prefs.edit { gone.forEach { remove(it) } }
    }

    fun get(context: Context, feedUrl: String?): String? =
        feedUrl?.let { found[it] ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(it, null) }
}
