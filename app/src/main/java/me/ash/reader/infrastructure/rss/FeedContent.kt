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
                val host = link?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
                when {
                    host.endsWith("wikipedia.org") -> tidyWikipedia(doc)
                    host.endsWith("xkcd.com") -> tidyXkcd(doc)
                }
                doc.body().html()
            }
            .getOrDefault(html)
    }

    private fun makeAddressesComplete(doc: Document, link: String?) {
        for (el in doc.select("img[src], source[src], video[src], audio[src], iframe[src]")) {
            el.attr("src", absoluteUrl(el.attr("src"), link) ?: continue)
        }
        for (el in doc.select("img[srcset], source[srcset]")) {
            el.attr(
                "srcset",
                el.attr("srcset").split(",").joinToString(", ") { part ->
                    val bits = part.trim().split(Regex("\\s+"), limit = 2)
                    val url = absoluteUrl(bits.first(), link) ?: bits.first()
                    if (bits.size > 1) "$url ${bits[1]}" else url
                },
            )
        }
        for (el in doc.select("a[href]")) {
            val href = el.attr("href")
            if (href.startsWith("#")) continue
            el.attr("href", absoluteUrl(href, link) ?: continue)
        }
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
