package me.ash.reader.brief.ui

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.delay

/**
 * Shares [picture] as a PNG through the app's file provider (cache folder "shared"), with [text]
 * (such as a link) alongside. A new file each time, named after [name] (an app still reading the
 * last one isn't handed a half-written picture); older ones with that name are cleared.
 */
fun sharePicture(context: Context, picture: ImageBitmap, name: String, text: String? = null) {
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith(name) }?.forEach { it.delete() }
        val file = File(dir, "$name-${System.currentTimeMillis()}.png")
        file.outputStream().use { picture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .apply { text?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_TEXT, it) } }
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * While [capturing], lays out [content] at the width it's given but taking no space and never
 * drawn on screen, waits a moment for it to settle, then hands its image to [onPicture] (null if
 * it couldn't be taken). Put it inside whatever is being shared, so the picture gets its width.
 */
@Composable
fun OffscreenPicture(capturing: Boolean, onPicture: (ImageBitmap?) -> Unit, content: @Composable () -> Unit) {
    if (!capturing) return
    val layer = rememberGraphicsLayer()
    LaunchedEffect(Unit) {
        withFrameNanos {}
        delay(350)
        withFrameNanos {}
        onPicture(runCatching { layer.toImageBitmap() }.getOrNull())
    }
    Layout(
        content = {
            androidx.compose.foundation.layout.Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                }
            ) {
                content()
            }
        }
    ) { measurables, constraints ->
        val placeable =
            measurables.first().measure(Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth))
        layout(0, 0) { placeable.place(0, 0) }
    }
}

/**
 * The Brief You mark for shared pictures: the app's glyph (its one-colour themed-icon glyph) in a
 * shape, and the name. By default in your accent colour on a squiggly cookie; the newspaper
 * passes ink and paper and a squircle, so it stays black and white.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@androidx.compose.runtime.Composable
fun BriefYouMark(
    color: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    glyph: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
    shape: androidx.compose.ui.graphics.Shape? = null,
) {
    val markShape = shape ?: MaterialShapes.Cookie12Sided.toShape()
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(20.dp).clip(markShape).background(container),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            // The glyph is drawn on 108 units with the icon in the middle 72, so it's drawn half
            // as big again as the shape and cropped to it.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(me.ash.reader.R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(glyph),
                modifier = Modifier.requiredSize(30.dp),
            )
        }
        androidx.compose.material3.Text(
            text = androidx.compose.ui.res.stringResource(me.ash.reader.R.string.triage_share_brand),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Like [OffscreenPicture], but always [widthPx] pixels wide whatever the phone: [content] is laid
 * out [widthDp] wide at the density that makes that many pixels, and at a font scale of 1. For
 * pictures with small print (the newspaper), so they stay sharp after messaging apps shrink them.
 */
@Composable
fun OffscreenPictureAt(
    widthPx: Int,
    widthDp: Float,
    capturing: Boolean,
    onPicture: (ImageBitmap?) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!capturing) return
    val layer = rememberGraphicsLayer()
    LaunchedEffect(Unit) {
        withFrameNanos {}
        delay(350)
        withFrameNanos {}
        onPicture(runCatching { layer.toImageBitmap() }.getOrNull())
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(widthPx / widthDp, 1f)
    ) {
        Layout(
            content = {
                androidx.compose.foundation.layout.Box(
                    Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() } }
                ) {
                    content()
                }
            }
        ) { measurables, _ ->
            val placeable = measurables.first().measure(Constraints(minWidth = widthPx, maxWidth = widthPx))
            layout(0, 0) { placeable.place(0, 0) }
        }
    }
}
