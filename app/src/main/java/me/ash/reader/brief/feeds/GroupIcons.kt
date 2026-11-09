package me.ash.reader.brief.feeds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ash.reader.ui.component.EMOJI_PREFIX

/**
 * Icons chosen for groups, like a feed's custom icon: a Material icon, an emoji (older choices) or
 * an image from the phone. Groups
 * have no icon field of their own, so the choice is kept here (by group id) and read by the feeds
 * page; a group without one shows a folder.
 */
object GroupIcons {
    private const val PREFS = "group_icons"
    private const val ICON_SIZE = 192

    /** Group id → icon (`emoji:…` or a file address), observed by the feeds page. */
    val icons = mutableStateMapOf<String, String>()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).all.forEach { (k, v) -> if (v is String) icons[k] = v }
    }

    /** A Material icon from [SymbolIcons], by name. */
    fun setSymbol(context: Context, groupId: String, name: String) {
        val value = SymbolIcons.PREFIX + name
        if (SymbolIcons.isKnown(value)) set(context, groupId, value)
    }

    fun setEmoji(context: Context, groupId: String, emoji: String) {
        val e = emoji.trim()
        if (e.isNotEmpty()) set(context, groupId, EMOJI_PREFIX + e)
    }

    /** Copies the picked image, scaled down to icon size, into the app's own storage. */
    suspend fun setImage(context: Context, groupId: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) error("Could not read that image")
            var sample = 1
            while (longest / (sample * 2) >= ICON_SIZE) sample *= 2
            val src =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Could not read that image")
            val scale = ICON_SIZE.toFloat() / maxOf(src.width, src.height)
            val bmp =
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true)
                } else src
            val dir = File(context.filesDir, "group_icons").apply { mkdirs() }
            val safe = groupId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            dir.listFiles { f -> f.name.startsWith(safe + "_") }?.forEach { it.delete() }
            // A new name each time, so the image cache shows the new one.
            val file = File(dir, "${safe}_${System.currentTimeMillis()}.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            withContext(Dispatchers.Main) { set(context, groupId, Uri.fromFile(file).toString()) }
        }

    /** Back to the folder. */
    fun clear(context: Context, groupId: String) {
        prefs(context).edit { remove(groupId) }
        icons.remove(groupId)
    }

    private fun set(context: Context, groupId: String, value: String) {
        prefs(context).edit { putString(groupId, value) }
        icons[groupId] = value
    }
}
