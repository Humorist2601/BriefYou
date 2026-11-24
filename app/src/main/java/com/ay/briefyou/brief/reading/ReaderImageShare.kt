package com.ay.briefyou.brief.reading

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shares the picture itself (not its link): it is fetched into the app's cache folder "shared" and
 * handed to the share sheet through the file provider. False if it could not be fetched.
 */
suspend fun shareImageFile(context: Context, url: String, referer: String): Boolean {
    val file =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                if (referer.isNotBlank()) connection.setRequestProperty("Referer", referer)
                val type = connection.contentType?.substringBefore(';')?.trim().orEmpty()
                val sub = type.substringAfter('/', "jpeg").substringBefore('+')
                val extension = if (sub == "jpeg" || sub == "*" || sub.isBlank()) "jpg" else sub
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                dir.listFiles { f -> f.name.startsWith("image-") }?.forEach { it.delete() }
                val target = File(dir, "image-${System.currentTimeMillis()}.$extension")
                connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                Pair(target, if (type.startsWith("image/")) type else "image/*")
            }.getOrNull()
        } ?: return false
    val (target, mime) = file
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}
