package me.ash.reader.brief.ai

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.ash.reader.R
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** What a phone can expect from the model, checked before downloading 2.6 GB. */
data class PhoneCheck(
    val memoryGb: Double,
    val freeBytes: Long,
    val needBytes: Long,
    val arm64: Boolean,
    val metered: Boolean,
) {
    enum class Fit { Good, Okay, Poor }

    /**
     * The memory size the phone is sold with (Android reports a little less: 7.4 for 8 GB), the
     * number people know.
     */
    val nominalGb: Int
        get() = listOf(2, 3, 4, 6, 8, 12, 16, 24, 32).firstOrNull { it * 0.9 <= memoryGb && memoryGb <= it } ?: kotlin.math.ceil(memoryGb).toInt()

    /** 8 GB phones run it well; 6 GB works (other apps may close); less is unlikely to load. */
    val fit: Fit
        get() =
            when {
                !arm64 || nominalGb < 6 -> Fit.Poor
                nominalGb < 8 -> Fit.Okay
                else -> Fit.Good
            }

    val enoughSpace: Boolean get() = freeBytes >= needBytes

    companion object {
        fun of(context: Context): PhoneCheck {
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            val stat = StatFs(context.filesDir.absolutePath)
            val partial = ModelDownload.partialFile(context).takeIf { it.exists() }?.length() ?: 0L
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return PhoneCheck(
                memoryGb = memory.totalMem / 1_073_741_824.0,
                freeBytes = stat.availableBytes,
                // The file, less what's already downloaded, and a little room to spare.
                needBytes = (ModelDownload.MODEL_BYTES - partial).coerceAtLeast(0) + SPARE_BYTES,
                arm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a"),
                metered = connectivity.isActiveNetworkMetered,
            )
        }

        private const val SPARE_BYTES = 300L * 1024 * 1024
    }
}

/**
 * Downloads the model straight into the app's storage, as a background job with a progress
 * notification: it carries on when the app is closed, and picks up where it stopped if the
 * connection drops (the file is fetched in parts). The finished file is moved into place, so
 * there's no second 2.6 GB copy as with an import.
 */
object ModelDownload {
    // Fixed to one version of the repository (its commit), with that file's SHA-256, so a changed
    // file on the server is rejected instead of being loaded by the app's native model code. To
    // move to a newer model: take the new commit and the file's "oid sha256" from its Hugging Face
    // page (the file's raw view shows both) and change these two together.
    private const val COMMIT = "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1"
    const val URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$COMMIT/gemma-4-E2B-it.litertlm"
    const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

    /** The file's SHA-256 as lowercase hex, read in pieces (the file is gigabytes). */
    fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** About 2.6 GB, as the file's page and the model card show it (the real size comes from the server). */
    const val MODEL_BYTES = 2_583_000_000L

    private const val WORK = "brief_model_download"

    internal fun partialFile(context: Context) = File(context.filesDir, "models/download.part")

    fun start(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    /** Whether the download job is waiting or running. */
    fun running(context: Context): kotlinx.coroutines.flow.Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
            infos.any { !it.state.isFinished }
        }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        partialFile(context).delete()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ModelDownloadDependencies {
    fun summarizer(): GemmaSummarizer
}

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val summarizer =
        EntryPointAccessors.fromApplication(context.applicationContext, ModelDownloadDependencies::class.java)
            .summarizer()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(null)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val part = ModelDownload.partialFile(applicationContext)
            try {
                runCatching { setForeground(foreground(null)) }
                part.parentFile?.mkdirs()
                summarizer.onDownload(progress = null)
                var have = if (part.exists()) part.length() else 0L
                val request =
                    Request.Builder()
                        .url(ModelDownload.URL)
                        .apply { if (have > 0) header("Range", "bytes=$have-") }
                        .build()
                client.newCall(request).execute().use { response ->
                    val append =
                        when (response.code) {
                            206 -> true
                            200 -> false.also { have = 0 }
                            416 -> {
                                // Nothing left to fetch, if the part is exactly the whole file
                                // ("Content-Range: bytes */total"). Otherwise it is wrong (a bigger or
                                // damaged file): thrown away and downloaded again from the start.
                                val whole = response.header("Content-Range")?.substringAfter("*/", "")?.toLongOrNull()
                                if (whole == null || whole != part.length()) {
                                    part.delete()
                                    throw java.io.IOException("The partly downloaded file was wrong; starting again")
                                }
                                true
                            }
                            else -> error("The download failed (HTTP ${response.code})")
                        }
                    if (response.code != 416) {
                        val body = response.body
                        val total = body.contentLength().takeIf { it > 0 }?.let { it + have }
                        FileOutputStream(part, append).use { out ->
                            body.byteStream().use { input ->
                                val buf = ByteArray(1 shl 16)
                                var lastReport = 0L
                                var lastNotify = 0L
                                while (true) {
                                    if (isStopped) return@withContext Result.failure()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    have += n
                                    if (have - lastReport > (4 shl 20)) {
                                        lastReport = have
                                        val p = total?.let { have.toFloat() / it }
                                        summarizer.onDownload(progress = p)
                                        val now = System.currentTimeMillis()
                                        if (now - lastNotify > 1_000) {
                                            lastNotify = now
                                            runCatching { setForeground(foreground(p)) }
                                        }
                                    }
                                }
                            }
                        }
                        // An IOException, so it is tried again and carries on from where it stopped.
                        if (total != null && have < total) throw java.io.IOException("The download stopped early")
                    }
                }
                // Only the file that was expected is installed.
                if (ModelDownload.sha256Of(part) != ModelDownload.SHA256) {
                    part.delete()
                    error("The downloaded model was not the expected file, so it was removed. Try the download again.")
                }
                summarizer.installDownloaded(part)
                Result.success()
            } catch (e: Exception) {
                Timber.w(e, "model download failed")
                if (runAttemptCount < 5 && e is java.io.IOException) {
                    summarizer.onDownload(progress = null, waiting = true)
                    Result.retry()
                } else {
                    summarizer.onDownloadFailed(e.message ?: e.javaClass.simpleName)
                    Result.failure()
                }
            }
        }

    private fun foreground(progress: Float?): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    applicationContext.getString(R.string.ai_download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(applicationContext.getString(R.string.ai_download_title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL = "brief_model_download"
        const val NOTIFICATION_ID = 0x6d6f64
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
