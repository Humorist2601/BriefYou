package me.ash.reader.brief.feeds

import android.content.Context
import androidx.core.content.edit
import com.rometools.modules.itunes.FeedInformation
import com.rometools.modules.itunes.ITunes
import com.rometools.rome.feed.synd.SyndFeed

/**
 * A feed's own picture (a podcast's cover, the feed's logo), kept by feed address, so the feed
 * can use it instead of its website's icon: chosen in Add a feed and in Change icon ("Feed's
 * picture" | "Website icon"). Read You always used the website's icon. Noted when a feed is found
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

    fun get(context: Context, feedUrl: String?): String? =
        feedUrl?.let { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(it, null) }
}
