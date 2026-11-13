package me.ash.reader.brief.reading

import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Newsletters read as feeds (through an email-to-feed service like Kill the Newsletter): their
 * articles are whole emails, built for a desktop inbox (layout tables, fixed widths, white boxes,
 * hidden preview text, tracking pixels, social icons, "Manage your preferences" footers). This
 * turns one into a plain article: the text, headings and pictures, in the reader's own style.
 */
object EmailContent {

    /** Whether [html] is an email rather than an ordinary article. [link] is the article's address. */
    fun isEmail(html: String, link: String?): Boolean {
        val host = link?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
        if (host.endsWith("kill-the-newsletter.com")) return true
        if (html.contains("<!--[if mso") || html.contains("x-apple-data-detectors")) return true
        return PRESENTATION.findAll(html).take(3).count() >= 3
    }

    /** Marks content tidied as an email (the first thing in it), so no lead picture is split off. */
    const val MARK = "<!--brief-email-->"

    private const val PREFS = "newsletters"
    private const val KEY_TIDY_MORE = "tidy_more"

    /**
     * The "Tidy newsletters" setting (Reading page): on, emails also get outlined buttons, boxed
     * notes, divider lines, and lose the sender's logo and the headline the article already shows;
     * off, they're only turned into a plain article. Loaded when the app starts.
     */
    @Volatile var tidyMore: Boolean = true
        private set

    fun load(context: android.content.Context) {
        tidyMore = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_TIDY_MORE, true)
    }

    fun setTidyMore(context: android.content.Context, value: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY_TIDY_MORE, value).apply()
        tidyMore = value
    }

    /** The email in [doc] as a plain article. */
    fun tidy(doc: Document, title: String? = null) {
        val more = tidyMore
        val body = doc.body()
        // What isn't shown in any inbox: Outlook's settings, styles, the head.
        body.select("head, style, script, title, meta, link, xml, noscript").remove()
        removeComments(body)
        // The phone layout, not the desktop one: desktop-only parts go; phone-only ones stay.
        body.select(".mobile_hide").remove()
        body.select(".desktop_hide").forEach { it.removeAttr("style") }
        // Hidden parts: the preview text shown only in the inbox list, tracking images.
        body.select("[style]").filter { isHidden(it.attr("style")) }.forEach { it.drop() }
        body.select("[class*=preheader], [class*=preview-text], [hidden]").remove()
        for (img in body.select("img")) {
            val w = img.attr("width").toFloatOrNull()
            val h = img.attr("height").toFloatOrNull()
            val src = img.attr("src")
            val social = SOCIAL.containsMatchIn(img.attr("alt")) || src.contains("social", ignoreCase = true)
            // Tracking pixels and spacers, social icons and tiny badges.
            if ((w != null && w <= 40) || (h != null && h <= 40) || src.isBlank() || social) {
                val link = img.parent()?.takeIf { it.tagName() == "a" && it.text().isBlank() }
                (link ?: img).drop()
            }
        }
        // What the email draws with styles, noted before they're stripped: buttons (dark pills),
        // boxes (a bordered callout) and divider lines.
        val buttons = if (more) body.select("a[href]").filter { isButton(it) } else emptyList()
        val boxes = if (more) body.select("td, div").filter { isBox(it) } else emptyList()
        if (more) body.select("td, div").filter { isDividerLine(it) }.forEach { it.empty(); it.tagName("hr") }
        // The footer's own links (preferences, unsubscribe, the feed service's settings) are
        // kept, as one short line at the very end.
        val keep = footerLinks(body)
        removeFooters(body)
        removeSocialLinks(body)
        removeMastheadLogo(body, more)
        // Headings: emails draw them as big text, not as headings.
        markHeadings(body)
        if (more) dropRepeatedHeadline(body, title)
        // Layout tables become plain blocks, so the text flows down the page.
        for (table in body.select("table").reversed()) {
            if (isDataTable(table)) continue
            table.select("td, th").filter { owningTable(it) == table }.forEach { it.tagName("div") }
            table.select("tbody, thead, tfoot, tr").filter { owningTable(it) == table }.forEach { it.unwrap() }
            table.tagName("div")
        }
        // The reader's own style: no fixed sizes, colours, fonts or alignment.
        for (el in body.allElements) {
            if (el == body) continue
            val keep =
                when (el.tagName()) {
                    "a" -> setOf("href")
                    "img" -> setOf("src", "alt")
                    "td", "th" -> setOf("colspan", "rowspan")
                    else -> emptySet()
                }
            el.attributes().asList().map { it.key }.filterNot { it in keep }.forEach { el.removeAttr(it) }
        }
        buttons.forEach { if (it.parent() != null) it.addClass("brief-button") }
        boxes.forEach { if (it.parent() != null) it.addClass("brief-note") }
        body.select("font, center, span").forEach { it.unwrap() }
        // Blocks left empty (spacers, removed icons) and runs of empty lines.
        removeEmpty(body)
        trimSignOff(body)
        if (keep.isNotEmpty()) {
            body.appendElement("hr")
            val line = body.appendElement("p")
            keep.entries.forEachIndexed { i, (label, href) ->
                if (i > 0) line.appendText(" · ")
                line.appendElement("a").attr("href", href).text(label)
            }
        }
        body.prepend(MARK)
    }

    /** A link drawn as a button: a coloured pill (background and rounded corners or padding) or a "button" class. */
    private fun isButton(a: Element): Boolean {
        val styled =
            listOf(a) + a.select("[style], [class]")
        return styled.any { el ->
            val st = el.attr("style").lowercase().replace(" ", "")
            val bg = BACKGROUND.find(st)?.groupValues?.get(3).orEmpty()
            val colored = bg.isNotEmpty() && bg != "transparent" && bg != "none" && bg != "#fff" && bg != "#ffffff" && bg != "white"
            (colored && (st.contains("border-radius") || st.contains("padding"))) ||
                el.className().split(" ").any { it.equals("button", true) || it.equals("btn", true) }
        }
    }

    /** A box with a border on (at least three) sides: the email's callout, such as "Press Reply". */
    private fun isBox(el: Element): Boolean {
        val st = el.attr("style").lowercase().replace(" ", "")
        val sides = BOX_SIDES.count { it.containsMatchIn(st) }
        return sides >= 3 && el.text().length > 20
    }

    /** An empty cell that only draws a line (a divider): it becomes a line. */
    private fun isDividerLine(el: Element): Boolean {
        val st = el.attr("style").lowercase().replace(" ", "")
        return DIVIDER_BORDER.containsMatchIn(st) &&
            !st.contains("border-left") && !st.contains("border-right") &&
            el.text().replace(BLANK, "").isEmpty() && el.select("img, table, a").isEmpty()
    }

    /** The first heading, when it only repeats the article's title shown above (before real text). */
    private fun dropRepeatedHeadline(body: Element, title: String?) {
        if (title.isNullOrBlank()) return
        fun norm(t: String) = t.lowercase().filter { it.isLetterOrDigit() }
        val first = body.selectFirst("h1, h2, h3") ?: return
        val h = norm(first.text())
        if (h.length < 8 || !norm(title).contains(h)) return
        // Only when little text comes before it (a date line, a byline), not a later section.
        val before = body.allElements.takeWhile { it != first }.sumOf { it.ownText().length }
        if (before < 120) first.drop()
    }

    /**
     * A newsletter's article as a feed gives it, improved: the list text without hidden preview
     * text and Outlook settings, the list picture from the issue (not a tracking pixel or logo),
     * no "delivery_…@bounce" author, and the issue's public web page as its address when it has
     * one. Other articles are returned as they are.
     */
    fun adjust(article: me.ash.reader.domain.model.article.Article): me.ash.reader.domain.model.article.Article =
        runCatching {
                val html = article.rawDescription
                if (!isEmail(html, article.link)) return article
                val doc = Jsoup.parseBodyFragment(html, article.link)
                tidy(doc)
                val img = doc.body().selectFirst("img[src]")?.absUrl("src")?.takeIf { it.startsWith("http") }
                doc.body().select("h1, h2, h3").remove()
                article.copy(
                    shortDescription = doc.body().text().replace(SPACES, " ").trim().take(280),
                    author = readableAuthor(article.author),
                    img = img ?: article.img,
                    link = webLink(html) ?: article.link,
                )
            }
            .getOrDefault(article)

    /** "From" addresses that only a mail server reads (delivery_…@bounce.…), not a person's name. */
    fun readableAuthor(author: String?): String? {
        val a = author?.trim().orEmpty()
        if (a.isEmpty()) return null
        if (!a.contains('@')) return a
        val local = a.substringBefore('@').substringAfterLast('<').lowercase()
        val domain = a.substringAfter('@').lowercase()
        val machine =
            domain.startsWith("bounce") || domain.contains(".bounce") || local.startsWith("bounce") ||
                local.startsWith("delivery") || local.startsWith("noreply") || local.startsWith("no-reply") ||
                local.length > 24 || local.count { it.isDigit() } > 6
        return if (machine) null else a
    }

    /**
     * The email's "View in browser" address: a public page of this issue, better than the
     * email-to-feed service's own page for sharing (that one carries your private feed address).
     */
    fun webLink(html: String): String? =
        runCatching {
                Jsoup.parseBodyFragment(html).select("a[href]").firstOrNull {
                    VIEW_ONLINE.containsMatchIn(it.text()) && it.attr("href").startsWith("http")
                }?.attr("href")
            }
            .getOrNull()

    private fun isHidden(style: String): Boolean {
        val s = style.lowercase().replace(" ", "")
        return s.contains("display:none") || s.contains("mso-hide:all") || s.contains("visibility:hidden") ||
            s.contains("max-height:0;") || s.endsWith("max-height:0") || s.contains("max-height:0px") ||
            HIDDEN_OPACITY.containsMatchIn(s) || HIDDEN_FONT.containsMatchIn(s)
    }

    /** "Follow us" links (Instagram, LinkedIn, X…), with what's left of their line. */
    private fun removeSocialLinks(body: Element) {
        for (a in body.select("a[href]")) {
            if (body !in a.parents()) continue
            val host = runCatching { URI(a.attr("href")).host }.getOrNull()?.removePrefix("www.").orEmpty()
            // By address, or by name (links in emails often go through the sender's click counter).
            val social = SOCIAL_HOSTS.any { host == it || host.endsWith(".$it") } || SOCIAL.matches(a.text().trim())
            if (!social || a.text().length > 40) continue
            val parent = a.parent()
            a.drop()
            if (parent != null && parent != body && parent.text().replace(SEPARATORS, "").length < 25 &&
                parent.select("img").isEmpty()
            ) {
                parent.drop()
            }
        }
    }

    /**
     * The sender's logo above everything (a small picture before any text): the article already
     * shows who it's from, and a logo drawn for a white page is often unreadable on a dark one.
     */
    private fun removeMastheadLogo(body: Element, more: Boolean) {
        var text = 0
        for (el in body.allElements) {
            if (el.tagName() == "img") {
                val w = el.attr("width").toFloatOrNull()
                val h = el.attr("height").toFloatOrNull()
                val src = el.attr("src")
                val drop = { (el.parent()?.takeIf { it.tagName() == "a" && it.text().isBlank() } ?: el).drop() }
                // An unsized picture before everything is a tracking image: gone, and look on.
                if (more && w == null && h == null && src.substringBefore('?').endsWith(".gif")) {
                    drop()
                    continue
                }
                // The logo: narrow, or named like one (a banner of the sender's name can be wide).
                val named = more && LOGO.containsMatchIn(src) || LOGO.containsMatchIn(el.attr("alt")) || LOGO.containsMatchIn(el.attr("title"))
                if ((w != null && w < 400) || named) drop()
                return
            }
            text += el.ownText().replace(BLANK, "").length
            if (text > 0) return
        }
    }

    /**
     * The short lines at the very end (the sender's name and postal address, store and social
     * links, "Was this forwarded to you?"), up to the last paragraph or picture of the issue.
     */
    private fun trimSignOff(body: Element) {
        while (true) {
            val last = lastLeaf(body) ?: return
            if (last.select("img").isNotEmpty() || last.text().length >= 90) return
            last.drop()
            removeEmpty(body, fold = false)
        }
    }

    /** The last block that holds text directly (a paragraph, heading, list). */
    private fun lastLeaf(body: Element): Element? {
        var el: Element = body
        while (true) {
            val child = el.children().lastOrNull() ?: return el.takeIf { it != body }
            if (child.tagName() in setOf("p", "h2", "h3", "ul", "ol", "img", "hr", "blockquote", "table") ||
                child.children().none { it.isBlock }
            ) {
                return child
            }
            el = child
        }
    }

    /**
     * Removes this from the page if it's still on it. An older jsoup (the one in the app) throws
     * when removing something already taken out with a bigger part around it.
     */
    private fun Node.drop() {
        if (parentNode() != null) remove()
    }

    /** The table [el] belongs to (the nearest one around it). */
    private fun owningTable(el: Element): Element? = el.parents().firstOrNull { it.tagName() == "table" }

    private fun removeComments(node: Node) {
        for (child in node.childNodes().toList()) {
            if (child is Comment) child.remove() else if (child is Element) removeComments(child)
        }
    }

    /** Footers: preferences, unsubscribe, view in browser, the feed service's own links. */
    /** "Email preferences", "Unsubscribe", "Feed settings": the footer links worth keeping, by label. */
    private fun footerLinks(body: Element): Map<String, String> {
        val found = linkedMapOf<String, String>()
        for (a in body.select("a[href]")) {
            val href = a.attr("abs:href").ifEmpty { a.attr("href") }
            if (!href.startsWith("http")) continue
            val text = a.text()
            val label =
                when {
                    PREFERENCES.containsMatchIn(text) -> "Email preferences"
                    UNSUBSCRIBE.containsMatchIn(text) -> "Unsubscribe"
                    KILL_NEWSLETTER.containsMatchIn(text) -> "Feed settings"
                    else -> continue
                }
            found.putIfAbsent(label, href)
        }
        return found
    }

    private fun removeFooters(body: Element) {
        val targets =
            body.select("a, p, div, td, span").filter { el ->
                val text = el.ownText()
                // A short line only: a paragraph of news that mentions "unsubscribe" stays.
                el.text().length < 200 &&
                    (FOOTER.containsMatchIn(text) || (el.tagName() == "a" && FOOTER.containsMatchIn(el.text())))
            }
        for (el in targets) {
            if (el.parent() == null) continue
            // The smallest block around it that is only footer (a short line, not a paragraph of news).
            var block: Element = el
            while (true) {
                val up = block.parent() ?: break
                if (up == body || up.text().length > 400 || up.select("img").isNotEmpty()) break
                block = up
                if (block.tagName() in setOf("p", "td", "tr")) break
            }
            // Only if it's still on the page (it may sit in a footer already removed).
            if (block.parent() != null) block.drop()
        }
    }

    /** Short lines in big text become headings (by size: h2 from 22 px, h3 from 17 px, or bold). */
    private fun markHeadings(body: Element) {
        for (el in body.select("p, div, td, h1, h2, h3, h4").reversed()) {
            if (el.parent() == null) continue
            val text = el.text().trim()
            if (text.isEmpty() || text.length > 120) continue
            if (el.select("p, div, td, img, table, br").any { it != el }) continue
            val size = biggestSize(el) ?: continue
            val level =
                when {
                    el.tagName().matches(HEADING_TAG) -> if (size >= 22) "h2" else "h3"
                    size >= 22 -> "h2"
                    size >= 17 -> "h3"
                    else -> continue
                }
            el.empty()
            el.appendText(text)
            el.tagName(level)
        }
    }

    private fun biggestSize(el: Element): Float? =
        (listOf(el) + el.select("[style]"))
            .mapNotNull { FONT_SIZE.find(it.attr("style"))?.groupValues?.get(1)?.toFloatOrNull() }
            .maxOrNull()

    /** A real table of data (headers, or a grid of short text), kept as a table. */
    private fun isDataTable(table: Element): Boolean {
        if (table.attr("role") == "presentation") return false
        if (table.select("table").any { it != table }) return false
        if (table.select("th").isNotEmpty()) return true
        // Rows with two or more filled cells (a row with one, beside spacer cells, is layout).
        val rows = table.select("tr").filter { owningTable(it) == table }
        val filled = rows.count { r -> r.children().count { it.tagName() == "td" && it.text().replace(BLANK, "").isNotEmpty() } >= 2 }
        return rows.size >= 2 && filled * 2 >= rows.size && table.select("img, p, div").isEmpty()
    }

    private fun removeEmpty(body: Element, fold: Boolean = true) {
        var changed = true
        while (changed) {
            changed = false
            for (el in body.select("div, p, a, h2, h3, li, ul, ol, strong, b, em, i, table").reversed()) {
                if (el.parent() == null) continue
                if (el.select("img, br, hr").isEmpty() && el.text().replace(BLANK, "").isEmpty()) {
                    el.drop()
                    changed = true
                }
            }
        }
        if (!fold) return
        // Divs holding only one div are folded into it.
        for (el in body.select("div").reversed()) {
            if (el.parent() == null || el.hasClass("brief-note")) continue
            val only = el.children().singleOrNull()
            if (only != null && el.textNodes().all { it.isBlank } && only.tagName() == "div") el.unwrap()
        }
        // Bare text sitting in a block becomes a paragraph, so it gets the reader's spacing.
        for (div in body.select("div")) {
            val text = div.textNodes().filterNot { it.isBlank }
            if (text.isNotEmpty() && div.children().none { it.isBlock }) div.tagName("p")
        }
        for (t in body.select("*").flatMap { it.textNodes() }) {
            t.text(t.wholeText.replace(HAIR, ""))
        }
    }

    // Built once (they were built again for every element of an email).
    private val PRESENTATION = Regex("role=\"presentation\"")
    private val BACKGROUND = Regex("(^|;)background(-color)?:([^;]+)")
    private val BOX_SIDES =
        listOf("border-top", "border-right", "border-bottom", "border-left").map { Regex("$it:[1-9]\\d*px(solid|dashed)") }
    private val DIVIDER_BORDER = Regex("border-top:[1-9]\\d*px(solid|dashed|dotted)")
    private val SPACES = Regex("\\s+")
    private val HIDDEN_OPACITY = Regex("(^|;)opacity:0(;|$)")
    private val HIDDEN_FONT = Regex("(^|;)font-size:0(px)?(;|$)")
    private val PREFERENCES = Regex("(?i)prefer|manage (your )?subscription")
    private val UNSUBSCRIBE = Regex("(?i)unsubscribe")
    private val KILL_NEWSLETTER = Regex("(?i)kill the newsletter")
    private val HEADING_TAG = Regex("h[1-4]")
    private val FONT_SIZE = Regex("font-size:\\s*([\\d.]+)px")
    private val LOGO = Regex("(?i)logo|banner|masthead|wordmark|newsletter[_-]?header")
    private val BLANK = Regex("[\\s\\u00A0\\u200A\\u200B\\u200C\\u2007\\u034F\\uFEFF]")
    private val SEPARATORS = Regex("[\\s\\u00A0|·•,/–—-]")
    private val HAIR = Regex("[\\u200A\\u200B\\u200C\\u034F\\uFEFF]")
    private val SOCIAL = Regex("(?i)^(facebook|twitter|x|instagram|linkedin|youtube|tiktok|bluesky|threads|pinterest|whatsapp|telegram|mastodon|reddit)$")
    private val SOCIAL_HOSTS =
        listOf(
            "facebook.com", "twitter.com", "x.com", "instagram.com", "linkedin.com", "youtube.com", "youtu.be",
            "tiktok.com", "bsky.app", "threads.com", "threads.net", "pinterest.com", "t.me", "wa.me", "reddit.com",
        )
    private val VIEW_ONLINE = Regex("(?i)(view|read|open)[\\s\\u00A0]+(this[\\s\\u00A0]+(email|issue)[\\s\\u00A0]+)?(it[\\s\\u00A0]+)?(in|on)[\\s\\u00A0]+(your[\\s\\u00A0]+|a[\\s\\u00A0]+)?(browser|web)|view online|web version")
    private val FOOTER =
        Regex(
            "(?i)unsubscribe|manage (your )?(email )?preferences|update (your )?(email )?preferences|email preferences|" +
                "view (this email |it )?in (your |a )?browser|view online|kill the newsletter|you('re| are) receiving this|" +
                "why did i get this|forward (this )?to a friend|add us to your address book|forwarded to you"
        )
}
