package me.ash.reader.brief.media

import com.rometools.modules.itunes.EntryInformation
import com.rometools.modules.itunes.ITunes
import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.MediaModule
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry

/**
 * What a feed item carries besides its text, read while the feed is read: its audio or video
 * attachment (an RSS enclosure, an Atom "enclosure" link or a Media RSS audio or video item),
 * a podcast episode's own picture and length, and its comments link. Brief You.
 */
object FeedMedia {

    fun fromSyndEntry(articleId: String, entry: SyndEntry): ArticleExtras {
        var url: String? = null
        var kind: String? = null
        var type: String? = null
        var durationMs: Long? = null

        // RSS <enclosure> (Rome also lists Atom's rel="enclosure" links here).
        entry.enclosures.orEmpty().firstNotNullOfOrNull { e ->
            MediaFiles.kindOf(e.url, e.type)?.let { Triple(e.url, it, e.type) }
        }?.let { (u, k, t) -> url = u; kind = k; type = t }

        // Media RSS: <media:content medium="audio|video">.
        val mediaRss = runCatching { entry.getModule(MediaModule.URI) as? MediaEntryModule }.getOrNull()
        if (url == null && mediaRss != null) {
            val contents = mediaRss.mediaContents.toList() + mediaRss.mediaGroups.flatMap { it.contents.toList() }
            contents.firstNotNullOfOrNull { c ->
                val u = (c.reference as? UrlReference)?.url?.toString()
                val k = when (c.medium) {
                    "audio" -> ArticleExtras.AUDIO
                    "video" -> ArticleExtras.VIDEO
                    else -> MediaFiles.kindOf(u, c.type)
                }
                if (u != null && k != null) Triple(u, k, c) else null
            }?.let { (u, k, c) ->
                url = u; kind = k; type = c.type
                durationMs = c.duration?.takeIf { it > 0 }?.times(1000)
            }
        }

        // Podcasts (iTunes tags): the episode's own picture and length.
        val itunes = runCatching { entry.getModule(ITunes.URI) as? EntryInformation }.getOrNull()
        itunes?.duration?.milliseconds?.takeIf { it > 0 }?.let { durationMs = it }
        val image = itunes?.imageUri?.takeIf { it.isNotBlank() } ?: itunes?.image?.toString()

        // Comments: RSS <comments>, or Atom's rel="replies" web page.
        val comments =
            entry.comments?.takeIf { it.isNotBlank() }
                ?: entry.links.orEmpty().firstOrNull { it.rel == "replies" && (it.type == null || it.type.contains("html")) }?.href

        val link = entry.link
        return ArticleExtras(
            articleId = articleId,
            mediaUrl = url?.trim()?.let { absoluteUrl(it, link) },
            mediaKind = kind,
            mediaType = type,
            durationMs = durationMs,
            image = image?.trim()?.let { absoluteUrl(it, link) },
            comments = comments?.trim()?.let { absoluteUrl(it, link) },
        )
    }

    /** An attachment sent by a sync service (the Google Reader protocol's "enclosure" list). */
    fun fromEnclosures(articleId: String, enclosures: List<Pair<String?, String?>>, link: String?): ArticleExtras? {
        val (u, t) = enclosures.firstOrNull { (u, t) -> u != null && MediaFiles.kindOf(u, t) != null } ?: return null
        return ArticleExtras(
            articleId = articleId,
            mediaUrl = absoluteUrl(u!!.trim(), link),
            mediaKind = MediaFiles.kindOf(u, t),
            mediaType = t,
        )
    }

    private fun absoluteUrl(url: String, base: String?): String =
        me.ash.reader.brief.reading.FeedContent.absoluteUrl(url, base) ?: url
}
