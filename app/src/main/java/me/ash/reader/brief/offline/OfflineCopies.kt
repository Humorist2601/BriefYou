package me.ash.reader.brief.offline

import android.content.Context
import android.webkit.WebSettings
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.model.article.ArticleWithFeed
import org.jsoup.Jsoup

/**
 * Offline copies of starred articles (Read later and Triage saves are starred too): the full text,
 * for feeds set to Full content, and the article's pictures, kept in the app's own storage (not
 * the cache, which Android may clear). The text in the feed itself is already stored on the
 * phone; this adds what would otherwise need the internet.
 *
 * - Pictures are stored once each, by address, in `files/offline/images`, and each article lists
 *   the ones it uses; [imageFile] is what the reading page asks for before going online.
 * - The full text goes in `files/offline/text`, read when the full-text cache has none.
 * - [OfflineWorker] keeps copies in step with the stars: downloads what's missing, removes what's
 *   no longer starred. [schedule] runs it (on starring, after a save, and when the app starts).
 *   It waits for Wi-Fi unless the setting says any connection ([wifiOnly]).
 */
object OfflineCopies {
    private const val PREFS = "offline_copies"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val WORK = "offline_copies"

    /** At most this many pictures per article, each at most [MAX_IMAGE_BYTES]. */
    private const val MAX_IMAGES = 40
    private const val MAX_IMAGE_BYTES = 6L * 1024 * 1024

    private fun root(context: Context) = File(context.filesDir, "offline")

    private fun imagesDir(context: Context) = File(root(context), "images")

    private fun textDir(context: Context) = File(root(context), "text")

    private fun listsDir(context: Context) = File(root(context), "lists")

    @OptIn(ExperimentalStdlibApi::class)
    private fun hash(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).toHexString()

    /** Downloads only on Wi-Fi (the default) or on any connection. */
    fun wifiOnly(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_WIFI_ONLY, value) }
        schedule(context)
    }

    /** Brings the copies in step with the stars, when the network allows. */
    fun schedule(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<OfflineWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(3, TimeUnit.SECONDS) // a few stars in a row make one run
                .build()
        // A run already going finishes; one more follows to pick up what changed meanwhile.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** A stored picture for [url], with its type, or null. */
    fun imageFile(context: Context, url: String): Pair<File, String>? {
        val file = File(imagesDir(context), hash(url))
        if (!file.isFile) return null
        val type = File(imagesDir(context), hash(url) + ".type").takeIf { it.isFile }?.readText() ?: "image/*"
        return file to type
    }

    /** The stored full text for an article, or null. */
    fun fullText(context: Context, articleId: String): String? =
        File(textDir(context), hash(articleId)).takeIf { it.isFile }?.readText()

    /** Whether this article has its copy already. */
    fun has(context: Context, articleId: String): Boolean = File(listsDir(context), hash(articleId)).isFile

    /**
     * Downloads [item]'s copy: [fullText] when its feed is set to Full content (null when it
     * couldn't be fetched), and the pictures in its text. False when something is still missing,
     * so the run tries again later.
     */
    fun download(context: Context, item: ArticleWithFeed, fullText: String?): Boolean {
        val article = item.article
        var complete = true
        if (item.feed.isFullContent) {
            if (fullText.isNullOrBlank()) complete = false
            else File(textDir(context).apply { mkdirs() }, hash(article.id)).writeText(fullText)
        }
        val html = (fullText ?: "") + article.rawDescription
        val urls =
            (listOfNotNull(article.img) +
                    Jsoup.parse(html, article.link).select("img").flatMap { img ->
                        listOf(img.absUrl("src"), img.absUrl("data-src")) +
                            img.attr("srcset").split(',').mapNotNull { part ->
                                part.trim().substringBefore(' ').takeIf { it.isNotBlank() }?.let { src ->
                                    runCatching { URI(article.link).resolve(src).toString() }.getOrNull()
                                }
                            }
                    })
                .filter { it.startsWith("http") }
                .distinct()
                .take(MAX_IMAGES)
        val dir = imagesDir(context).apply { mkdirs() }
        for (url in urls) {
            val file = File(dir, hash(url))
            if (file.isFile) continue
            if (!fetch(context, url, article.link, file)) complete = false
        }
        File(listsDir(context).apply { mkdirs() }, hash(article.id)).writeText(urls.joinToString("\n"))
        return complete
    }

    /** Removes the copies of articles not in [keepIds], and pictures no copy uses any more. */
    fun prune(context: Context, keepIds: Collection<String>) {
        val keep = keepIds.map { hash(it) }.toSet()
        listsDir(context).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        textDir(context).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        val used =
            listsDir(context).listFiles().orEmpty().flatMap { it.readLines() }.filter { it.isNotBlank() }.map { hash(it) }.toSet()
        imagesDir(context).listFiles()?.forEach { f ->
            if (f.name.removeSuffix(".type") !in used) f.delete()
        }
    }

    /** Fetches a picture like a browser would (the article as referrer, then without). */
    private fun fetch(context: Context, url: String, pageUrl: String, into: File): Boolean {
        val userAgent =
            runCatching { WebSettings.getDefaultUserAgent(context).replace("; wv)", ")") }.getOrNull()
        for (referer in listOf(pageUrl, null)) {
            try {
                val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                userAgent?.let { connection.setRequestProperty("User-Agent", it) }
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                referer?.let { connection.setRequestProperty("Referer", it) }
                try {
                    if (connection.responseCode !in 200..299) continue
                    val type = connection.contentType?.substringBefore(';')?.trim().orEmpty()
                    // Pictures only, and not huge ones.
                    if (!type.startsWith("image/")) return true // not a picture: nothing to keep
                    if (connection.contentLengthLong > MAX_IMAGE_BYTES) return true
                    val part = File(into.parentFile, into.name + ".part")
                    var size = 0L
                    connection.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                size += n
                                if (size > MAX_IMAGE_BYTES) {
                                    part.delete()
                                    return true
                                }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    File(into.parentFile, into.name + ".type").writeText(type)
                    part.renameTo(into)
                    return true
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {}
        }
        return false
    }
}
