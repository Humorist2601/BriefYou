package me.ash.reader.brief.feeds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.ui.component.EMOJI_PREFIX

/**
 * Custom feed icons chosen by the user: a Material icon, an emoji (older choices), an image
 * from the phone, or the feed's own picture (see [FeedPictures]; its address).
 *
 * The choice is written to the feed's icon field so every screen shows it, and also remembered
 * here, because some accounts (Fever, Google Reader API) rewrite feed icons when they sync.
 * [reapply] puts the custom icons back after every sync.
 */
@Singleton
class FeedIconOverrides
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val feedDao: FeedDao,
    private val accountService: me.ash.reader.domain.service.AccountService,
) {
    private val prefs = context.getSharedPreferences("feed_icon_overrides", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "feed_icons").apply { mkdirs() }

    /** A Material icon from [SymbolIcons], by name. */
    suspend fun setSymbol(feedId: String, name: String) {
        val value = SymbolIcons.PREFIX + name
        if (SymbolIcons.isKnown(value)) set(feedId, value)
    }

    suspend fun setEmoji(feedId: String, emoji: String) {
        val e = emoji.trim()
        if (e.isEmpty()) return
        set(feedId, EMOJI_PREFIX + e)
    }

    /** The feed's own picture (its address), instead of the website's icon. */
    suspend fun setPicture(feedId: String, url: String) {
        if (url.startsWith("http", ignoreCase = true)) set(feedId, url)
    }

    /** Copies the picked image, scaled down to a small square-ish icon, into private storage. */
    suspend fun setImage(feedId: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            // Read at about icon size, not full size: a big photo would otherwise need hundreds
            // of megabytes of memory.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val options = scaledOptions(bounds) ?: error("Could not read that image")
            val src =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: error("Could not read that image")
            storeBitmap(feedId, src)
        }

    private suspend fun storeBitmap(feedId: String, src: Bitmap) {
        val scale = ICON_SIZE.toFloat() / maxOf(src.width, src.height)
        val bmp =
            if (scale < 1f)
                Bitmap.createScaledBitmap(
                    src,
                    (src.width * scale).toInt().coerceAtLeast(1),
                    (src.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            else src
        // A new file name each time, so image caches pick up the change.
        dir.listFiles { f -> f.name.startsWith(safe(feedId) + "_") }?.forEach { it.delete() }
        val file = File(dir, "${safe(feedId)}_${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        set(feedId, Uri.fromFile(file).toString())
    }

    /**
     * The feed's custom icon in a form that works on any phone, for OPML export: `emoji:😀`, or the
     * image itself as `image/png;base64,...` (a file path would mean nothing elsewhere). Null when
     * the feed has no custom icon.
     */
    suspend fun exportValue(feedId: String): String? =
        withContext(Dispatchers.IO) {
            val icon = prefs.getString(feedId, null) ?: return@withContext null
            if (icon.startsWith(EMOJI_PREFIX) || icon.startsWith(SymbolIcons.PREFIX)) return@withContext icon
            if (icon.startsWith("http", ignoreCase = true)) return@withContext icon // the feed's picture
            val file = Uri.parse(icon).path?.let { File(it) }
            if (file == null || !file.exists()) return@withContext null
            "image/png;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }

    /** Applies a custom icon read from an imported OPML file (the format of [exportValue]). */
    suspend fun adopt(feedId: String, value: String) =
        withContext(Dispatchers.IO) {
            when {
                value.startsWith(EMOJI_PREFIX) -> setEmoji(feedId, value.removePrefix(EMOJI_PREFIX))
                value.startsWith(SymbolIcons.PREFIX) -> setSymbol(feedId, value.removePrefix(SymbolIcons.PREFIX))
                value.startsWith("http", ignoreCase = true) -> setPicture(feedId, value)
                else -> {
                    val data = IMAGE_VALUE.matchEntire(value)?.groupValues?.get(1) ?: return@withContext
                    val bytes =
                        runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull()
                            ?: return@withContext
                    // At about icon size: a tiny file can describe a huge picture.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = scaledOptions(bounds) ?: return@withContext
                    val bmp =
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            ?: return@withContext
                    storeBitmap(feedId, bmp)
                }
            }
        }

    /** Forgets the custom icon. The website icon comes back with the next icon reload or sync. */
    suspend fun clear(feedId: String) =
        withContext(Dispatchers.IO) {
            prefs.edit { remove(feedId) }
            dir.listFiles { f -> f.name.startsWith(safe(feedId) + "_") }?.forEach { it.delete() }
        }

    fun has(feedId: String): Boolean = prefs.contains(feedId)

    /** A feed was deleted: its custom icon (and image file) and its noted picture go with it. */
    suspend fun feedDeleted(feedId: String, feedUrl: String?) {
        clear(feedId)
        FeedPictures.forget(context, feedUrl)
    }

    /** Forgets custom icons (and image files) of feeds that no longer exist ([feedIds]: every feed's id). */
    suspend fun sweep(feedIds: Collection<String>) =
        withContext(Dispatchers.IO) {
            val known = feedIds.toHashSet()
            prefs.all.keys.filterNot { it in known }.forEach { clear(it) }
            // Image files left without a saved icon.
            val used = prefs.all.values.mapNotNull { (it as? String)?.let { v -> Uri.parse(v).path } }.toHashSet()
            dir.listFiles()?.filter { it.absolutePath !in used }?.forEach { it.delete() }
        }

    /** Re-applies custom icons that a sync may have overwritten. */
    suspend fun reapply() =
        withContext(Dispatchers.IO) {
            // A feed added with its own picture chosen: kept from its first sync on (accounts
            // that sync icons from their server would otherwise replace it).
            for (url in FeedPictures.chosen(context)) {
                val picture = FeedPictures.get(context, url) ?: continue
                // The server may keep the address a little differently (http/https, a slash).
                val accountId = accountService.getCurrentAccountId()
                val feeds = FeedPictures.variants(url).flatMap { feedDao.queryByLink(accountId, it) }.distinctBy { it.id }
                if (feeds.isEmpty()) continue
                feeds.forEach { prefs.edit { putString(it.id, picture) } }
                FeedPictures.unchoose(context, url)
            }
            // One lookup for all the feeds with a custom icon.
            val overrides = prefs.all.mapNotNull { (id, v) -> (v as? String)?.let { id to it } }
            val feeds = overrides.map { it.first }.chunked(500).flatMap { feedDao.queryByIds(it) }.associateBy { it.id }
            for ((feedId, value) in overrides) {
                var icon = value
                val feed = feeds[feedId] ?: continue
                // The feed's picture (an address): follows the feed when it changes its cover.
                if (icon.startsWith("http", ignoreCase = true)) {
                    FeedPictures.get(context, feed.url)?.takeIf { it != icon }?.let {
                        icon = it
                        prefs.edit { putString(feedId, it) }
                    }
                }
                if (feed.icon != icon) feedDao.update(feed.copy(icon = icon))
            }
        }

    private suspend fun set(feedId: String, icon: String) =
        withContext(Dispatchers.IO) {
            prefs.edit { putString(feedId, icon) }
            feedDao.queryById(feedId)?.let { feedDao.update(it.copy(icon = icon)) }
        }

    companion object {
        /** Image icons in OPML files: `image/png;base64,...` */
        val IMAGE_VALUE = Regex("^image/[a-z+.-]+;base64,(.+)$", RegexOption.DOT_MATCHES_ALL)
        const val MAX_IMPORT_ICON_CHARS = 400_000
        private const val ICON_SIZE = 192
        /** 200 megapixels: anything bigger isn't a real photo. */
        private const val MAX_PIXELS = 200_000_000L
    }

    /**
     * Options that read a picture at about icon size (a power of two at least [ICON_SIZE]), or
     * null when it isn't a readable picture or is absurdly large.
     */
    private fun scaledOptions(bounds: BitmapFactory.Options): BitmapFactory.Options? {
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= ICON_SIZE) sample *= 2
        return BitmapFactory.Options().apply { inSampleSize = sample }
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
