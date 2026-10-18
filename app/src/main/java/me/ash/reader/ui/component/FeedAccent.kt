package me.ash.reader.ui.component

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
    private val memory = ConcurrentHashMap<String, Int>()

    /** Raw dominant icon color (ARGB), or null when the icon has no clear color. */
    suspend fun sourceColor(context: Context, iconUrl: String): Int? {
        val key = hash(iconUrl)
        memory[key]?.let { return it.takeIf { c -> c != NONE } }
        val prefs = context.getSharedPreferences("feed_accent", Context.MODE_PRIVATE)
        if (prefs.contains(key)) {
            val c = prefs.getInt(key, NONE)
            memory[key] = c
            return c.takeIf { it != NONE }
        }
        val color = withContext(Dispatchers.IO) { runCatching { extract(context, iconUrl) }.getOrNull() }
        memory[key] = color ?: NONE
        prefs.edit().putInt(key, color ?: NONE).apply()
        return color
    }

    private suspend fun extract(context: Context, iconUrl: String): Int? {
        if (iconUrl.startsWith(EMOJI_PREFIX)) return dominantColor(renderEmoji(iconUrl.removePrefix(EMOJI_PREFIX)))
        val bitmap: Bitmap =
            if ("^image/.*;base64,.*".toRegex().matches(iconUrl)) {
                val bytes = Base64.decode(iconUrl.substringAfter("base64,"), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            } else {
                val result =
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(iconUrl)
                            .size(64)
                            .allowHardware(false)
                            .build()
                    )
                (result as? SuccessResult)?.drawable?.toBitmapOrNull() ?: return null
            }
        return dominantColor(bitmap)
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

    private val pictureMemory = ConcurrentHashMap<String, Int>()

    /**
     * The main colour of a picture (an article's), or null when it has no clear colour (black and
     * white photos, for example). Remembered while the app runs.
     */
    suspend fun pictureColor(context: Context, url: String): Int? {
        pictureMemory[url]?.let { return it.takeIf { c -> c != NONE } }
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
                        (result as? SuccessResult)?.drawable?.toBitmapOrNull()?.let(::dominantColor)
                    }
                    .getOrNull()
            }
        pictureMemory[url] = color ?: NONE
        return color
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
            if (chroma < 15 || lab[0] < 15 || lab[0] > 95) continue // greys, near-black, near-white
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
        val sHue = atan2(s[2], s[1]) * 180 / PI
        val pHue = atan2(p[2], p[1]) * 180 / PI
        val diff = ((pHue - sHue + 540) % 360) - 180 // signed shortest distance to primary
        // Material harmonization: rotate halfway toward primary, at most 15°.
        val rotation = minOf(abs(diff) * 0.5, 15.0) * Math.signum(diff)
        val hue = (sHue + rotation) * PI / 180
        var chroma = minOf(sqrt(s[1] * s[1] + s[2] * s[2]), MAX_CHROMA)
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

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    /** Muted: Material's custom colors sit well below the dynamic palette's most vivid tones. */
    private const val MAX_CHROMA = 38.0
}

/** The themed accent color for a feed's icon, or null (use the default) while unknown or grey. */
@Composable
fun rememberFeedAccent(iconUrl: String?): Color? {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val source by
        produceState<Int?>(null, iconUrl) {
            value = iconUrl?.takeIf { it.isNotBlank() }?.let { FeedAccent.sourceColor(context, it) }
        }
    return remember(source, primary, dark) {
        source?.let { FeedAccent.themed(it, primary, dark) }
    }
}
