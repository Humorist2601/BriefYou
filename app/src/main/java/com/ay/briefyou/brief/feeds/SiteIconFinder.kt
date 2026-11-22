package com.ay.briefyou.brief.feeds

import android.graphics.BitmapFactory
import android.util.Log
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Finds a website's sharpest square icon, for feeds' "Website icon". Replaces the original
 * BestIconFinder, which ranked by file type and file size: it could pick the page's social
 * picture (og:image, a wide photo), Safari's one-colour pinned-tab icon, or a 16 px favicon over
 * a 180 px home-screen icon.
 *
 * Looks at the page's home-screen icons (apple-touch-icon), its web app manifest's icons, its
 * favicons, and the usual /apple-touch-icon.png and /favicon.ico; fetches the most promising few
 * and measures the real picture. The biggest roughly square one wins (up to 256 px; past that
 * they all look the same at icon size), home-screen and app icons first when sizes tie.
 */
class SiteIconFinder(private val client: OkHttpClient) {

    private enum class Kind(val rank: Int) {
        Favicon(0),
        Icon(1),
        Maskable(2),
        Touch(3),
        Manifest(4),
    }

    private class Candidate(val url: String, val kind: Kind, val declared: Int)

    private class Measured(val url: String, val kind: Kind, val px: Int)

    suspend fun find(site: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val pages =
                    if (site.startsWith("http://") || site.startsWith("https://")) listOf(site)
                    else listOf("https://$site", "http://$site")
                var base: String? = null
                var html: String? = null
                for (page in pages) {
                    val got = runCatching { text(page) }.getOrNull() ?: continue
                    base = got.first
                    html = got.second
                    break
                }
                val root = base ?: pages.first()
                val candidates = mutableListOf<Candidate>()
                html?.let { candidates += fromPage(root, it) }
                candidates += Candidate(URL(URL(root), "/apple-touch-icon.png").toString(), Kind.Touch, 180)
                candidates += Candidate(URL(URL(root), "/favicon.ico").toString(), Kind.Favicon, 16)
                candidates
                    .distinctBy { it.url }
                    // The most promising first; only a few are fetched.
                    .sortedWith(compareByDescending<Candidate> { minOf(it.declared, 256) }.thenByDescending { it.kind.rank })
                    .take(MAX_FETCHED)
                    .mapNotNull { measure(it) }
                    .maxWithOrNull(compareBy<Measured> { minOf(it.px, 256) }.thenBy { it.kind.rank })
                    ?.url
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("RLog", "SiteIconFinder: $e")
                null
            }
        }

    /** The page's own icons: home-screen icons, favicons and the web app manifest's icons. */
    private fun fromPage(base: String, html: String): List<Candidate> {
        val doc = Jsoup.parse(html, base)
        val out = mutableListOf<Candidate>()
        for (link in doc.select("link[rel][href]")) {
            val rel = link.attr("rel").lowercase(Locale.ROOT).split(Regex("\\s+")).toSet()
            val href = link.attr("abs:href").takeIf { it.startsWith("http") } ?: continue
            val declared = declaredSize(link.attr("sizes"))
            when {
                // Safari's pinned-tab icon is one colour, drawn as a mask: not an icon to show.
                "mask-icon" in rel -> Unit
                "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel ->
                    out += Candidate(href, Kind.Touch, declared ?: 180)
                "icon" in rel -> out += Candidate(href, Kind.Icon, declared ?: if (href.endsWith(".svg")) 192 else 32)
                "manifest" in rel -> out += manifestIcons(href)
            }
        }
        return out
    }

    private fun manifestIcons(manifestUrl: String): List<Candidate> =
        runCatching {
                val json = JSONObject(text(manifestUrl)?.second ?: return emptyList())
                val icons = json.optJSONArray("icons") ?: return emptyList()
                (0 until icons.length()).mapNotNull { i ->
                    val icon = icons.optJSONObject(i) ?: return@mapNotNull null
                    val src = icon.optString("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val purpose = icon.optString("purpose").lowercase(Locale.ROOT).split(Regex("\\s+")).toSet()
                    // One-colour icons are for the system to tint: not the site's own picture.
                    if (purpose.isNotEmpty() && purpose.all { it == "monochrome" }) return@mapNotNull null
                    val url = URL(URL(manifestUrl), src).toString()
                    val kind = if ("any" in purpose || purpose == setOf("")) Kind.Manifest else Kind.Maskable
                    Candidate(url, kind, declaredSize(icon.optString("sizes")) ?: 192)
                }
            }
            .getOrDefault(emptyList())

    /** "180x180", "16x16 32x32", "any" (a scalable picture) → the biggest side. */
    private fun declaredSize(sizes: String?): Int? {
        if (sizes.isNullOrBlank()) return null
        if (sizes.contains("any", ignoreCase = true)) return 192
        return sizes.split(Regex("\\s+")).mapNotNull { s ->
            s.lowercase(Locale.ROOT).split('x').takeIf { it.size == 2 }?.mapNotNull { it.toIntOrNull() }?.maxOrNull()
        }.maxOrNull()
    }

    /** Fetches a candidate and measures the real picture; null when it isn't a usable square icon. */
    private fun measure(candidate: Candidate): Measured? =
        runCatching {
                client.newCall(Request.Builder().url(candidate.url).build()).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val type = response.header("Content-Type")?.lowercase(Locale.ROOT) ?: ""
                    if (type.contains("text/html")) return null // a "not found" page sent as a success
                    val bytes = response.body.bytes()
                    if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
                    if (type.contains("svg") || candidate.url.substringBefore('?').endsWith(".svg", ignoreCase = true)) {
                        // Scalable: sharp at any size; counted like a good home-screen icon.
                        return if (String(bytes.copyOfRange(0, minOf(bytes.size, 512))).contains("<svg", ignoreCase = true))
                            Measured(response.request.url.toString(), candidate.kind, 192)
                        else null
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val w = bounds.outWidth
                    val h = bounds.outHeight
                    if (w <= 0 || h <= 0) return null
                    // Roughly square only: a wide banner isn't an icon.
                    val ratio = w.toFloat() / h
                    if (ratio < 0.8f || ratio > 1.25f) return null
                    Measured(response.request.url.toString(), candidate.kind, minOf(w, h))
                }
            }
            .getOrNull()

    /** The page's address after redirects and its text, or null when it couldn't be read. */
    private fun text(url: String): Pair<String, String>? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.request.url.toString() to response.body.string()
        }

    private companion object {
        const val MAX_FETCHED = 8
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
