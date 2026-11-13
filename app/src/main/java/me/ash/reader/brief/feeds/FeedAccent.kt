package me.ash.reader.brief.feeds

import me.ash.reader.ui.component.EMOJI_PREFIX

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Accent colors for feed names, taken from each feed's icon.
 *
 * The icon's dominant colorful hue is extracted once (cached in memory and on disk), then turned
 * into a theme-friendly color following Material You's guidance for custom colors:
 * 1. **Harmonize** — rotate the hue up to 15° toward the theme's primary color.
 * 2. **Mute** — cap the chroma so it sits calmly beside the dynamic palette.
 * 3. **Tone** — lightness 40 in light theme and 80 in dark theme, like other accent roles.
 */
object FeedAccent {
    private const val NONE = 0
    private const val MAX_SAVED = 1000
    private val memory = ConcurrentHashMap<String, Int>()

    /** Raw dominant icon color (ARGB), or null when the icon has no clear color. */
    suspend fun sourceColor(context: Context, iconUrl: String): Int? {
        // Looked up by the icon's address first: every row of the article list asks while you
        // scroll, and this answers at once without any work on the main thread. Only a colour
        // not known yet in memory goes to the saved ones and the icon (in the background).
        memory[iconUrl]?.let { return it.takeIf { c -> c != NONE } }
        // An icon that couldn't be fetched a moment ago (offline, a slow host) isn't asked again at
        // once, and isn't remembered as "no colour": it is tried again later.
        failedAt[iconUrl]?.let { if (System.currentTimeMillis() - it < RETRY_MS) return null }
        val color =
            withContext(Dispatchers.IO) {
                val key = hash(iconUrl)
                // v3: white and grey icons keep their own faint tint (the old cache said "no colour").
                val prefs = context.getSharedPreferences("feed_accent_v3", Context.MODE_PRIVATE)
                if (prefs.contains(key)) {
                    prefs.getInt(key, NONE)
                } else {
                    val found =
                        try {
                            extract(context, iconUrl) ?: NONE
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            return@withContext null // not read this time: nothing is saved
                        }
                    // Old icons' colours are never needed again; start over now and then rather
                    // than keep every icon a feed ever had (the current ones are worked out again
                    // when shown).
                    if (prefs.all.size > MAX_SAVED) prefs.edit().clear().apply()
                    prefs.edit().putInt(key, found).apply()
                    found
                }
            }
        if (color == null) {
            failedAt[iconUrl] = System.currentTimeMillis()
            return null
        }
        failedAt.remove(iconUrl)
        memory[iconUrl] = color
        return color.takeIf { it != NONE }
    }

    private val failedAt = ConcurrentHashMap<String, Long>()
    private const val RETRY_MS = 60_000L

    private suspend fun extract(context: Context, iconUrl: String): Int? {
        // Saved Articles' bookmark is drawn in the theme's colour: no colour of its own.
        if (iconUrl == me.ash.reader.ui.component.SAVED_ARTICLES_ICON) return null
        if (iconUrl.startsWith(SymbolIcons.PREFIX)) return null // a Material icon: the theme's colour
        if (iconUrl.startsWith(EMOJI_PREFIX)) return colorOf(renderEmoji(iconUrl.removePrefix(EMOJI_PREFIX)))
        val bitmap: Bitmap =
            if ("^image/.*;base64,.*".toRegex().matches(iconUrl)) {
                val bytes = Base64.decode(iconUrl.substringAfter("base64,"), Base64.DEFAULT)
                // Read small: only the colours are needed, and a tiny file can be a huge picture.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 64) sample *= 2
                BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return null
            } else {
                val result =
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(iconUrl)
                            .size(64)
                            .allowHardware(false)
                            .build()
                    )
                // Not fetched (offline, a failing host): raised, so it isn't saved as "no colour".
                (result as? SuccessResult)?.drawable?.toBitmapOrNull() ?: error("icon not fetched")
            }
        return colorOf(bitmap)
    }

    /** Draws an emoji with the system emoji font so its colors can be sampled like an icon. */
    private fun renderEmoji(emoji: String): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * 0.8f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        android.graphics.Canvas(bmp).drawText(emoji, size / 2f, y, paint)
        return bmp
    }

    /** Colours of recently opened pictures (the newest 200), so the cache can't grow forever. */
    private val pictureMemory = android.util.LruCache<String, Int>(200)

    /**
     * The main colour of a picture (an article's); for black and white or white pictures, their
     * faint tint (grey when there is none), so the reading page turns muted with them; null when
     * the picture could not be read (the reading page then keeps Brief You's own colours).
     * Remembered while the app runs.
     */
    suspend fun pictureColor(context: Context, url: String): Int? {
        pictureMemory.get(url)?.let { return it.takeIf { c -> c != NONE } }
        val color =
            withContext(Dispatchers.IO) {
                runCatching {
                        val result =
                            context.imageLoader.execute(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(128)
                                    .allowHardware(false)
                                    .build()
                            )
                        (result as? SuccessResult)?.drawable?.toBitmapOrNull()?.let { colorOf(it) }
                    }
                    .getOrNull()
            }
        // Only a colour that was read is remembered, so a picture that failed to load is tried again.
        if (color != null) pictureMemory.put(url, color)
        return color
    }

    /**
     * The icon's colour: its strongest hue, or, for white, grey and black icons, the faint tint
     * they have (a warm white, a cool grey), which [themed] keeps muted. Null only when there is
     * no picture to read.
     */
    private fun colorOf(src: Bitmap): Int? = dominantColor(src) ?: mutedAverage(src)

    /** The average tint of the visible, not-black pixels, at a middle lightness; null with none. */
    private fun mutedAverage(src: Bitmap): Int? {
        val bmp = if (src.width > 64 || src.height > 64) Bitmap.createScaledBitmap(src, 64, 64, true) else src
        var n = 0
        var a = 0.0
        var b = 0.0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            val lab = argbToLab(p)
            if (lab[0] < 12) continue
            a += lab[1]
            b += lab[2]
            n++
        }
        return if (n == 0) null else labToArgb(60.0, a / n, b / n)
    }

    /** Chroma-weighted hue histogram; returns the average color of the strongest hue bucket. */
    private fun dominantColor(src: Bitmap): Int? {
        val bmp = if (src.width > 64 || src.height > 64) Bitmap.createScaledBitmap(src, 64, 64, true) else src
        val bins = 24
        val weight = DoubleArray(bins)
        val sumL = DoubleArray(bins)
        val sumA = DoubleArray(bins)
        val sumB = DoubleArray(bins)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val p = bmp.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            val lab = argbToLab(p)
            val chroma = sqrt(lab[1] * lab[1] + lab[2] * lab[2])
            // Greys, near-black and pure white are skipped; faint and pale colours still count
            // (light icons: a pale blue or pink is still the feed's colour).
            if (chroma < 8 || lab[0] < 12 || lab[0] > 98) continue
            val hue = (atan2(lab[2], lab[1]) * 180 / PI + 360) % 360
            val b = (hue / (360.0 / bins)).toInt().coerceIn(0, bins - 1)
            weight[b] += chroma
            sumL[b] += lab[0] * chroma
            sumA[b] += lab[1] * chroma
            sumB[b] += lab[2] * chroma
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        val total = weight.sum()
        if (weight[best] <= 0 || weight[best] < total * 0.15) return null
        val w = weight[best]
        return labToArgb(sumL[best] / w, sumA[best] / w, sumB[best] / w)
    }

    /** The source color adapted to the current theme, as described on [FeedAccent]. */
    fun themed(source: Int, primary: Color, dark: Boolean): Color {
        val s = argbToLab(source)
        val p = argbToLab(primary.toArgb())
        // A grey source stays a grey, at the same tones as the other accents.
        if (sqrt(s[1] * s[1] + s[2] * s[2]) < 4.0) return Color(labToArgb(if (dark) 80.0 else 40.0, 0.0, 0.0))
        val sHue = atan2(s[2], s[1]) * 180 / PI
        val pHue = atan2(p[2], p[1]) * 180 / PI
        val diff = ((pHue - sHue + 540) % 360) - 180 // signed shortest distance to primary
        // Material harmonization: rotate halfway toward primary, at most 15°.
        val rotation = minOf(abs(diff) * 0.5, 15.0) * Math.signum(diff)
        val hue = (sHue + rotation) * PI / 180
        // Pale icons: their hue is lifted to a readable accent (at least MIN_CHROMA).
        // A faint tint (white and grey icons) stays faint: muted, not lifted to a vivid colour.
        val sourceChroma = sqrt(s[1] * s[1] + s[2] * s[2])
        var chroma = if (sourceChroma < MUTED_BELOW) sourceChroma else sourceChroma.coerceIn(MIN_CHROMA, MAX_CHROMA)
        val l = if (dark) 80.0 else 40.0
        // Reduce chroma until the color fits in sRGB.
        while (chroma > 0) {
            val argb = labToArgbOrNull(l, chroma * cos(hue), chroma * sin(hue))
            if (argb != null) return Color(argb)
            chroma -= 2
        }
        return Color(labToArgb(l, 0.0, 0.0))
    }

    // --- CIELAB conversions (D65) ---

    private fun argbToLab(argb: Int): DoubleArray {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((argb shr 16) and 0xFF)
        val g = lin((argb shr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun labToArgbOrNull(l: Double, a: Double, b: Double): Int? {
        val fy = (l + 16) / 116
        val fx = fy + a / 500
        val fz = fy - b / 200
        fun inv(t: Double) = if (t.pow(3) > 0.008856) t.pow(3) else (t - 16.0 / 116) / 7.787
        val x = inv(fx) * 0.95047
        val y = inv(fy)
        val z = inv(fz) * 1.08883
        val rl = 3.2406 * x - 1.5372 * y - 0.4986 * z
        val gl = -0.9689 * x + 1.8758 * y + 0.0415 * z
        val bl = 0.0557 * x - 0.2040 * y + 1.0570 * z
        val eps = 0.001
        if (listOf(rl, gl, bl).any { it < -eps || it > 1 + eps }) return null
        fun enc(v: Double): Int {
            val c = v.coerceIn(0.0, 1.0)
            val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
            return (s * 255).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (enc(rl) shl 16) or (enc(gl) shl 8) or enc(bl)
    }

    private fun labToArgb(l: Double, a: Double, b: Double): Int {
        var c = sqrt(a * a + b * b)
        val h = atan2(b, a)
        while (true) {
            labToArgbOrNull(l, c * cos(h), c * sin(h))?.let { return it }
            if (c <= 0) return 0xFF808080.toInt()
            c = maxOf(0.0, c - 2)
        }
    }

    private fun hash(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        val hex = "0123456789abcdef"
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(hex[v ushr 4]).append(hex[v and 0x0F])
        }
        return out.toString()
    }

    /** Muted: Material's custom colors sit well below the dynamic palette's most vivid tones. */
    private const val MAX_CHROMA = 38.0

    /** Colourful enough to read as the feed's colour, even from a pale icon. */
    private const val MIN_CHROMA = 24.0

    /** Below this, an icon's tint is too faint to be a colour: it is kept as it is. */
    private const val MUTED_BELOW = 8.0
}

/** The themed accent color for a feed's icon, or null (Brief You's own colour) while unknown or when the icon can't be read. */
@Composable
fun rememberFeedAccent(iconUrl: String?): Color? {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val source by
        produceState<Int?>(null, iconUrl) {
            value =
                iconUrl?.takeIf { it.isNotBlank() }?.let {
                    FeedAccent.sourceColor(context, it)
                }
        }
    return remember(source, primary, dark) {
        source?.let { FeedAccent.themed(it, primary, dark) }
    }
}
