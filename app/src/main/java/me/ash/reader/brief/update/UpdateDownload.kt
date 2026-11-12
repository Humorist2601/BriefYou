package me.ash.reader.brief.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.ui.ext.getFileProvider
import me.ash.reader.ui.ext.getLatestApk
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Downloads an app update in the background, the way the AI model is downloaded: a job with a
 * progress notification that carries on when the app is closed and picks up where it stopped if
 * the connection drops. When it's done a notification says so, and tapping it (or Install in the
 * update dialog) opens Android's installer. The file is checked to be this app before it's offered.
 */
object UpdateDownload {
    sealed interface State {
        /** Nothing downloaded for the offered update. */
        data object Idle : State

        /** Downloading; [percent] is null until the size is known. */
        data class Downloading(val percent: Int?) : State

        /** A newer version of this app is downloaded and waiting to be installed. */
        data object Ready : State
    }

    private const val WORK = "brief_update_download"
    internal const val PROGRESS = "progress"
    internal const val URL = "url"

    internal fun partialFile(context: Context) = File(context.filesDir, "updates/latest.apk.part")

    fun start(context: Context, url: String) {
        val request =
            OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(workDataOf(URL to url))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        partialFile(context).delete()
    }

    /** The downloaded APK when it is this app and newer than the installed one; otherwise removed. */
    fun readyApk(context: Context): File? {
        val file = context.getLatestApk()
        if (!file.isFile) return null
        val archive =
            runCatching { context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull()
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        if (archive == null || installed == null || archive.packageName != context.packageName ||
            archive.longVersionCode <= installed.longVersionCode
        ) {
            file.delete() // not an update (already installed, or not this app)
            return null
        }
        return file
    }

    /** Waiting, downloading or ready, as it changes. */
    fun state(context: Context): Flow<State> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WORK)
            .map { infos ->
                val active = infos.firstOrNull { !it.state.isFinished }
                when {
                    active != null ->
                        State.Downloading(active.progress.getInt(PROGRESS, -1).takeIf { it >= 0 })
                    readyApk(context) != null -> State.Ready
                    else -> State.Idle
                }
            }
            .flowOn(Dispatchers.IO)

    /** The intent that opens Android's installer for [readyApk]. */
    internal fun installIntent(context: Context, file: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(
                FileProvider.getUriForFile(context, context.getFileProvider(), file),
                "application/vnd.android.package-archive",
            )
        }
}

class UpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(null)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val url = inputData.getString(UpdateDownload.URL).orEmpty()
            if (url.isBlank()) return@withContext Result.failure()
            val part = UpdateDownload.partialFile(applicationContext)
            try {
                runCatching { setForeground(foreground(null)) }
                part.parentFile?.mkdirs()
                var have = if (part.exists()) part.length() else 0L
                val request =
                    Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
                client.newCall(request).execute().use { response ->
                    val append =
                        when (response.code) {
                            206 -> true
                            200 -> false.also { have = 0 }
                            416 -> true // Nothing left to fetch: the part is the whole file.
                            else -> error("The download failed (HTTP ${response.code})")
                        }
                    if (response.code != 416) {
                        val body = response.body
                        val total = body.contentLength().takeIf { it > 0 }?.let { it + have }
                        FileOutputStream(part, append).use { out ->
                            body.byteStream().use { input ->
                                val buf = ByteArray(1 shl 16)
                                var lastNotify = 0L
                                while (true) {
                                    if (isStopped) return@withContext Result.failure()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    have += n
                                    val now = System.currentTimeMillis()
                                    if (now - lastNotify > 700) {
                                        lastNotify = now
                                        val percent = total?.let { (have * 100 / it).toInt() }
                                        if (percent != null) {
                                            setProgress(Data.Builder().putInt(UpdateDownload.PROGRESS, percent).build())
                                        }
                                        runCatching { setForeground(foreground(percent)) }
                                    }
                                }
                            }
                        }
                        if (total != null && have < total) error("The download stopped early")
                    }
                }
                val latest = applicationContext.getLatestApk()
                latest.delete()
                if (!part.renameTo(latest)) {
                    part.copyTo(latest, overwrite = true)
                    part.delete()
                }
                val ready = UpdateDownload.readyApk(applicationContext)
                if (ready == null) {
                    notifyDone(applicationContext.getString(R.string.update_download_failed), null)
                    return@withContext Result.failure()
                }
                notifyDone(
                    applicationContext.getString(R.string.update_ready),
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        UpdateDownload.installIntent(applicationContext, ready),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                Result.success()
            } catch (e: Exception) {
                Timber.w(e, "update download failed")
                if (runAttemptCount < 5 && e is java.io.IOException) {
                    Result.retry()
                } else {
                    part.delete()
                    notifyDone(applicationContext.getString(R.string.update_download_failed), null)
                    Result.failure()
                }
            }
        }

    private fun manager(): NotificationManager {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    applicationContext.getString(R.string.update_download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        return manager
    }

    private fun notifyDone(text: String, tap: PendingIntent?) {
        runCatching {
            manager()
                .notify(
                    DONE_ID,
                    NotificationCompat.Builder(applicationContext, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(applicationContext.getString(R.string.update_download_title))
                        .setContentText(text)
                        .setAutoCancel(true)
                        .apply { tap?.let { setContentIntent(it) } }
                        .build(),
                )
        }
    }

    private fun foreground(progress: Int?): ForegroundInfo {
        manager()
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(applicationContext.getString(R.string.update_download_title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress ?: 0, progress == null)
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL = "brief_update_download"
        const val NOTIFICATION_ID = 0x757064
        const val DONE_ID = 0x757065
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
