package me.ash.reader.infrastructure.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.ash.reader.R

/**
 * Keeps read aloud going when you leave the article or the app, with a notification (also on the
 * lock screen) showing what is being read, how far along it is, and a Stop button. Tapping the
 * notification opens the app. It starts when reading starts and goes away when reading stops or
 * finishes.
 */
@AndroidEntryPoint
class ReadAloudService : Service() {

    @Inject lateinit var tts: TextToSpeechManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tts.stop()
            finish()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        startInForeground(build(tts.state))
        if (!started) {
            started = true
            scope.launch {
                tts.stateFlow.collect { state ->
                    when (state) {
                        is TextToSpeechManager.State.Reading,
                        TextToSpeechManager.State.Preparing ->
                            NotificationManagerCompat.from(this@ReadAloudService).let {
                                if (it.areNotificationsEnabled()) {
                                    runCatching { it.notify(NOTIFICATION_ID, build(state)) }
                                }
                            }
                        else -> finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: android.app.Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                else 0,
            )
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(state: TextToSpeechManager.State): android.app.Notification {
        val open =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, ReadAloudService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(tts.title ?: getString(R.string.read_aloud))
                .setContentText(getString(R.string.read_aloud_notification_text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setDeleteIntent(stop)
                .addAction(R.drawable.ic_notification, getString(R.string.ai_stop_reading), stop)
        if (state is TextToSpeechManager.State.Reading && state.total > 1) {
            builder.setProgress(state.total, state.current, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "me.ash.reader.READ_ALOUD_STOP"

        private fun ensureChannel(context: Context) {
            NotificationManagerCompat.from(context)
                .createNotificationChannel(
                    NotificationChannel(
                            CHANNEL_ID,
                            context.getString(R.string.read_aloud),
                            NotificationManager.IMPORTANCE_LOW,
                        )
                        .apply { setShowBadge(false) }
                )
        }

        /** Shows the notification (and keeps reading alive in the background). */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ReadAloudService::class.java),
                )
            }
        }
    }
}
