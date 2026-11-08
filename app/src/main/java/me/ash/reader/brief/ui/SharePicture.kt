package me.ash.reader.brief.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
