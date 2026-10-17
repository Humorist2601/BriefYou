package me.ash.reader.infrastructure.rss

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Tidies what feeds put in their articles, so it shows well on a phone.
 *
 * For every feed:
 * - Image and link addresses are made complete. Many feeds write them relative to their website
 *   (`/wiki/Page`) or without the scheme (`//upload.wikimedia.org/…`), which neither the image
 *   loader nor the reader can open on their own.
 *
 * For Wikipedia's featured feeds (featured article, on this day, picture of the day), which hold
 * a piece of Wikipedia's desktop Main Page:
 * - The side-by-side layout tables are unwrapped, so the picture and the text stack.
 * - Floats and fixed widths are removed; the main picture is shown across the page, in a sharper
 *   size, with its caption under it.
 * - The Main Page footers are removed (Archive · By email · More featured articles, Recently
 *   featured…).
 *
 * For xkcd:
 * - The comic is shown in xkcd's double-resolution version (falling back to the normal one for the
 *   few comics that don't have it).
 * - The hover text, which is the comic's last joke and can't be hovered on a phone, is shown under
 *   the comic.
 */
object FeedContent {

    /** A complete address for [url], read against the article's [base] address. */
    fun absoluteUrl(url: String?, base: String?): String? {
        if (url.isNullOrBlank()) return url
        val u = url.trim()
        if (u.startsWith("//")) return "https:$u"
        if (u.startsWith("data:") || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(u)) {
            return u
        }
        if (base.isNullOrBlank()) return u
        return runCatching { URI(base.trim()).resolve(u).toString() }.getOrDefault(u)
    }

    /** The picture for the article list (complete, and from the main picture for Wikipedia). */
    fun listImage(img: String?, link: String?): String? = absoluteUrl(img, link)

    /** The article's HTML, tidied for the reader. [link] is the article's address. */
    fun forReader(html: String, link: String?): String {
        if (html.isBlank()) return html
        return runCatching {
                val doc = Jsoup.parseBodyFragment(html, link ?: "")
                makeAddressesComplete(doc, link)
                tidyGeneral(doc)
                val host = link?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
                when {
                    host.endsWith("wikipedia.org") -> tidyWikipedia(doc)
                    host.endsWith("xkcd.com") -> tidyXkcd(doc)
                }
                doc.body().html()
            }
            .getOrDefault(html)
    }

    /**
     * The article's lead picture, when the article starts with one (before any real text): it is
     * taken out of the article so the reading page can show it as a large picture above the
     * title. Returns the picture's address (or null) and the article without it.
     */
    fun extractHero(html: String): Pair<String?, String> {
        if (html.isBlank()) return null to html
        return runCatching {
                val doc = Jsoup.parseBodyFragment(html)
                var textBefore = 0
                var hero: Element? = null
                for (node in doc.body().allElements) {
                    if (node.tagName() == "img") {
                        hero = node
                        break
                    }
                    textBefore += node.ownText().length
                    if (textBefore > 120) break
                }
                val img = hero ?: return@runCatching null to html
                val src = img.attr("src")
                val width = img.attr("width").toIntOrNull()
                // A picture with a caption (a comic, a captioned photo) stays with its caption.
                val captioned = img.parents().any { it.tagName() == "figure" && it.selectFirst("figcaption") != null }
                if (src.isBlank() || src.startsWith("data:") || (width != null && width < 300) || captioned) {
                    return@runCatching null to html
                }
                // Take the picture out, with any wrapper it leaves empty (a link, a figure, a
                // paragraph).
                var parent = img.parent()
                img.remove()
                while (parent != null && parent != doc.body() && parent.text().isBlank() &&
                    parent.select("img, video, iframe").isEmpty()
                ) {
                    val next = parent.parent()
                    parent.remove()
                    parent = next
                }
                src to doc.body().html()
            }
            .getOrDefault(null to html)
    }

    /**
     * For every feed: tracking pixels, share and "related posts" widgets, "Read more" links and
     * "The post … appeared first on …" lines are removed; tables get a box that scrolls sideways.
     */
    private fun tidyGeneral(doc: Document) {
        // Tracking pixels: tiny or hidden images, and feed tracker images.
        for (img in doc.select("img")) {
            val w = img.attr("width").toIntOrNull()
            val h = img.attr("height").toIntOrNull()
            val src = img.attr("src")
            val tiny = (w != null && w <= 2) || (h != null && h <= 2)
            val tracker = src.contains("feeds.feedburner.com/~r") || src.contains("/~ff/") ||
                src.contains("pixel.wp.com") || src.contains("stats.wordpress.com")
            val hidden = img.attr("style").replace(" ", "").contains("display:none")
            if (tiny || tracker || hidden) img.remove()
        }
        // Share buttons, social links and related-post lists that some feeds include.
        doc.select(
                ".sharedaddy, .jp-relatedposts, .addtoany_share_save_container, .feedflare, " +
                    ".share-buttons, .social-share, .sharing, [class*=share-links], [class*=social-links]"
            )
            .remove()
        // "The post … appeared first on …" (WordPress) and similar footers.
        val footer = Regex("(?i)^\\s*the post .+ appeared first on .+$")
        doc.select("p").filter { footer.matches(it.text()) }.forEach { it.remove() }
        // "Read more" / "Continue reading" links (the reading page has its own way to the site).
        val readMore =
            Regex("(?i)^\\s*(read more|continue reading|keep reading|read the (full|rest of the) (story|article|post)|weiterlesen)\\b.*")
        for (a in doc.select("a")) {
            if (!readMore.matches(a.text())) continue
            val parent = a.parent()
            a.remove()
            if (parent != null && parent.tagName() == "p" && parent.text().isBlank() &&
                parent.select("img").isEmpty()
            ) {
                parent.remove()
            }
        }
        // Wide tables scroll inside their own box instead of spilling off the page.
        for (table in doc.select("table")) {
            if (table.parent()?.hasClass("brief-table") == true) continue
            table.wrap("<div class=\"brief-table\"></div>")
        }
    }

    private fun makeAddressesComplete(doc: Document, link: String?) {
        for (el in doc.select("img[src], source[src], video[src], audio[src], iframe[src]")) {
            el.attr("src", absoluteUrl(el.attr("src"), link) ?: continue)
        }
        for (el in doc.select("img[srcset], source[srcset]")) {
            val candidates = parseSrcset(el.attr("srcset"))
            // Leave it exactly as it was when every address is already complete (Substack's
            // addresses have commas in them, so rewriting is only done when needed).
            if (candidates.all { (url, _) -> absoluteUrl(url, link) == url }) continue
            el.attr(
                "srcset",
                candidates.joinToString(", ") { (url, descriptor) ->
                    val full = absoluteUrl(url, link) ?: url
                    if (descriptor.isNullOrEmpty()) full else "$full $descriptor"
                },
            )
        }
        for (el in doc.select("a[href]")) {
            val href = el.attr("href")
            if (href.startsWith("#")) continue
            el.attr("href", absoluteUrl(href, link) ?: continue)
        }
    }

    /**
     * The (address, size) pairs of a `srcset`, read the way browsers do: an address runs until a
     * space, so it can contain commas (Substack's do); a comma only ends a candidate after its
     * size, or when it ends the address itself.
     */
    internal fun parseSrcset(srcset: String): List<Pair<String, String?>> {
        val out = ArrayList<Pair<String, String?>>()
        var i = 0
        val n = srcset.length
        while (i < n) {
            while (i < n && (srcset[i].isWhitespace() || srcset[i] == ',')) i++
            if (i >= n) break
            val urlStart = i
            while (i < n && !srcset[i].isWhitespace()) i++
            var url = srcset.substring(urlStart, i)
            if (url.endsWith(",")) {
                url = url.trimEnd(',')
                if (url.isNotEmpty()) out += url to null
                continue
            }
            val descStart = i
            var depth = 0
            while (i < n) {
                val c = srcset[i]
                if (c == '(') depth++ else if (c == ')') depth-- else if (c == ',' && depth <= 0) break
                i++
            }
            val descriptor = srcset.substring(descStart, i).trim().ifEmpty { null }
            if (i < n) i++ // the comma
            if (url.isNotEmpty()) out += url to descriptor
        }
        return out
    }

    // ---- Wikipedia ----

    private fun tidyWikipedia(doc: Document) {
        // Wikipedia's own style sheets come along inside the text; the reader has its own styles.
        doc.select("style, link").remove()
        // Main Page footers and "Recently featured" lines.
        doc.select(
                ".tfa-footer, .otd-footer, .potd-footer, .tfa-recent, .potd-recent, .noprint, " +
                    ".mw-editsection, .mw-empty-elt"
            )
            .remove()

        // Layout tables (picture in one cell, text in another): stack the cells instead.
        for (table in doc.select("table").reversed()) {
            val layout =
                table.attr("role") == "presentation" ||
                    table.select("img").any { it.attr("width").toIntOrNull()?.let { w -> w >= 100 } == true }
            if (!layout) continue
            val cells = table.select("td, th")
            val stack = Element("div")
            cells.forEach { cell -> stack.appendChild(Element("div").apply { cell.childNodes().toList().forEach { appendChild(it) } }) }
            table.replaceWith(stack)
        }

        // Pictures: the bigger ones become full-width figures with their caption under them.
        for (img in doc.select("img")) {
            val width = img.attr("width").toIntOrNull() ?: 0
            if (width in 1..60) continue // icons and flags stay inline
            val original = img.attr("src")
            val sharp = sharperWikimedia(img)
            img.attr("src", sharp)
            if (sharp != original) {
                // If the sharper size can't be loaded, the feed's own one is used.
                img.attr("onerror", "this.onerror=null;this.src='" + original.replace("'", "%27") + "'")
            }
            img.removeAttr("srcset")
            img.removeAttr("width")
            img.removeAttr("height")
            img.removeAttr("style")
            img.addClass("brief-full")

            // The box around the picture (floats and fixed widths), and its caption.
            val box = img.parents().firstOrNull { it.hasClass("thumbinner") || it.hasClass("thumb") || it.id().endsWith("-img") }
            val caption = box?.selectFirst(".thumbcaption")?.html()
            val figure = Element("figure").addClass("brief-figure")
            val anchor = img.parent()?.takeIf { it.tagName() == "a" }
            val picture = anchor ?: img
            if (box != null) {
                picture.remove()
                figure.appendChild(picture)
                if (!caption.isNullOrBlank()) figure.appendElement("figcaption").html(caption)
                box.replaceWith(figure)
            }
        }
        // Wherever a picture box was kept, its own floats and widths go.
        for (el in doc.select("[style]")) {
            val style = el.attr("style")
            if (style.contains("float") || style.contains("width")) el.removeAttr("style")
        }
        // Empty wrappers left behind.
        doc.select("div:empty, span:empty:not(br)").remove()
    }

    /**
     * A sharper version of a Wikimedia thumbnail, for a picture shown across the page: the largest
     * of the sizes the feed offers (src and srcset) and one of Wikimedia's standard thumbnail sizes
     * (330, 500 or 960 px wide, never wider than the original). Standard sizes are served quickly.
     */
    private fun sharperWikimedia(img: Element): String {
        val candidates = mutableListOf<String>()
        candidates += img.attr("src")
        img.attr("srcset").split(",").forEach { part ->
            part.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }?.let(candidates::add)
        }
        val fileWidth = img.attr("data-file-width").toIntOrNull()
        val thumb = THUMB.find(img.attr("src"))
        if (thumb != null && fileWidth != null) {
            STANDARD_WIDTHS.lastOrNull { it <= fileWidth }?.let { width ->
                candidates += thumb.groupValues[1] + width + "px-" + thumb.groupValues[3] + thumb.groupValues[4]
            }
        }
        return candidates.maxByOrNull { url -> THUMB.find(url)?.groupValues?.get(2)?.toIntOrNull() ?: 0 }
            ?: img.attr("src")
    }

    /** A Wikimedia thumbnail address: (start)(width)px-(file name)(query). */
    private val THUMB =
        Regex("^(https://(?:upload|thumb)\\.wikimedia\\.org/.+/thumb/.+/)(\\d+)px-([^/?]+)(\\?.*)?$")

    private val STANDARD_WIDTHS = listOf(330, 500, 960)

    // ---- xkcd ----

    private fun tidyXkcd(doc: Document) {
        for (img in doc.select("img[src*=imgs.xkcd.com/comics/]")) {
            val src = img.attr("src")
            val sharp = Regex("^(.+/comics/[^/]+?)(?<!_2x)\\.png$").find(src)
            if (sharp != null) {
                img.attr("src", sharp.groupValues[1] + "_2x.png")
                // A few comics have no double-size version: fall back to the normal one.
                img.attr("onerror", "this.onerror=null;this.src='" + src.replace("'", "%27") + "'")
            }
            img.addClass("brief-full")
            val hover = img.attr("title").ifBlank { img.attr("alt") }
            if (hover.isNotBlank() && img.attr("title").isNotBlank()) {
                val figure = Element("figure").addClass("brief-figure")
                img.replaceWith(figure)
                figure.appendChild(img)
                figure.appendElement("figcaption").text(hover)
            }
        }
    }
}
