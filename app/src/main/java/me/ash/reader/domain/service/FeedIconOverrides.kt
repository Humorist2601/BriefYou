package me.ash.reader.domain.service

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
 * Custom feed icons chosen by the user: an emoji, or an image from the phone.
 *
 * The choice is written to the feed's icon field so every screen shows it, and also remembered
 * here, because some accounts (Fever, Google Reader API) rewrite feed icons when they sync.
 * [reapply] puts the custom icons back after every sync.
 */
@Singleton
class FeedIconOverrides
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val feedDao: FeedDao,
) {
    private val prefs = context.getSharedPreferences("feed_icon_overrides", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "feed_icons").apply { mkdirs() }

    suspend fun setEmoji(feedId: String, emoji: String) {
        val e = emoji.trim()
        if (e.isEmpty()) return
        set(feedId, EMOJI_PREFIX + e)
    }

    /** Copies the picked image, scaled down to a small square-ish icon, into private storage. */
    suspend fun setImage(feedId: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            val src =
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Could not read that image")
            storeBitmap(feedId, src)
        }

    private suspend fun storeBitmap(feedId: String, src: Bitmap) {
        val scale = 192f / maxOf(src.width, src.height)
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
            if (icon.startsWith(EMOJI_PREFIX)) return@withContext icon
            val file = Uri.parse(icon).path?.let { File(it) }
            if (file == null || !file.exists()) return@withContext null
            "image/png;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }

    /** Applies a custom icon read from an imported OPML file (the format of [exportValue]). */
    suspend fun adopt(feedId: String, value: String) =
        withContext(Dispatchers.IO) {
            when {
                value.startsWith(EMOJI_PREFIX) -> setEmoji(feedId, value.removePrefix(EMOJI_PREFIX))
                else -> {
                    val data = IMAGE_VALUE.matchEntire(value)?.groupValues?.get(1) ?: return@withContext
                    val bytes =
                        runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull()
                            ?: return@withContext
                    val bmp =
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
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

    /** Re-applies custom icons that a sync may have overwritten. */
    suspend fun reapply() =
        withContext(Dispatchers.IO) {
            for ((feedId, value) in prefs.all) {
                val icon = value as? String ?: continue
                val feed = feedDao.queryById(feedId) ?: continue
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
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
