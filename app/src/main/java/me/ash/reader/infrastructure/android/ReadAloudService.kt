package me.ash.reader.infrastructure.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.ash.reader.R

/**
 * Read aloud in the system's media notification and controls, like a music app: the title, a
 * progress bar with the time (follows the words being spoken; drag it to jump), play/pause and
 * Stop, also on the lock screen and in Quick Settings. It keeps reading going when you leave the
 * article or the app, and goes away when reading stops or finishes.
 */
@AndroidEntryPoint
class ReadAloudService : Service() {

    @Inject lateinit var tts: TextToSpeechManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private lateinit var session: MediaSessionCompat

    /** The last time and kind of state given to the system, to avoid updating on every word. */
    private var lastUpdate = 0L
    private var lastKind: Class<*>? = null
    private var lastDuration = -1L

    override fun onCreate() {
        super.onCreate()
        session =
            MediaSessionCompat(this, "BriefYouReadAloud").apply {
                setCallback(
                    object : MediaSessionCompat.Callback() {
                        override fun onPlay() = tts.resume()

                        override fun onPause() = tts.pause()

                        override fun onStop() = tts.stop()

                        override fun onSeekTo(pos: Long) = tts.seekTo(pos)

                        override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                            when (action) {
                                ACTION_STOP -> tts.stop()
                                ACTION_SPEED -> tts.cycleSpeed()
                            }
                        }
                    }
                )
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    setSessionActivity(
                        PendingIntent.getActivity(
                            this@ReadAloudService,
                            0,
                            it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    )
                }
                isActive = true
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> tts.stop()
            ACTION_PAUSE -> tts.pause()
            ACTION_PLAY -> tts.resume()
            ACTION_SPEED -> tts.cycleSpeed()
        }
        ensureChannel(this)
        updateSession(tts.state, force = true)
        startInForeground(build(tts.state))
        if (!started) {
            started = true
            scope.launch {
                tts.stateFlow.collect { state ->
                    when (state) {
                        is TextToSpeechManager.State.Reading,
                        is TextToSpeechManager.State.Paused,
                        TextToSpeechManager.State.Preparing -> {
                            if (updateSession(state, force = false)) post(state)
                        }
                        else -> finish()
                    }
                }
            }
            // A new speed shows straight away (on the speed button and under the title).
            scope.launch {
                tts.speed.collect {
                    val state = tts.state
                    if (state is TextToSpeechManager.State.Reading || state is TextToSpeechManager.State.Paused) {
                        updateSession(state, force = true)
                        post(state)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Gives the system the title, length, position and play/pause state. */
    private fun updateSession(state: TextToSpeechManager.State, force: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        val kindChanged = state.javaClass != lastKind
        if (!force && !kindChanged && now - lastUpdate < UPDATE_EVERY_MS) return false
        lastUpdate = now
        lastKind = state.javaClass

        val duration = tts.durationMs
        if (force || kindChanged || kotlin.math.abs(duration - lastDuration) > 3_000) {
            lastDuration = duration
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        tts.title ?: getString(R.string.read_aloud),
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
                    .build()
            )
        }
        val playing = state !is TextToSpeechManager.State.Paused
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                            ACTION_SPEED,
                            getString(
                                R.string.read_aloud_speed,
                                TextToSpeechManager.speedLabel(tts.speed.value),
                            ),
                            R.drawable.ic_read_aloud_speed,
                        )
                        .build()
                )
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                            ACTION_STOP,
                            getString(R.string.ai_stop_reading),
                            R.drawable.ic_read_aloud_stop,
                        )
                        .build()
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING
                    else PlaybackStateCompat.STATE_PAUSED,
                    tts.positionMs,
                    if (playing) 1f else 0f,
                )
                .build()
        )
        return true
    }

    /**
     * The colour that fills the media notification: the article page's (from its picture when it
     * has one), or the app's accent colour.
     */
    private val accent: Int
        get() = tts.color ?: ContextCompat.getColor(this, R.color.system_accent)

    private var artworkColor: Int? = null
    private var artworkBitmap: android.graphics.Bitmap? = null

    /**
     * The "album art": a square in that colour with the Brief You card stack in the middle, in a
     * shade of the same colour, so the whole player takes the colour. Made again only when the
     * colour changes.
     */
    private val artwork: android.graphics.Bitmap?
        get() {
            val color = accent
            artworkBitmap?.takeIf { artworkColor == color }?.let { return it }
            val made =
                runCatching {
                        val size = 512
                        val bitmap =
                            android.graphics.Bitmap.createBitmap(
                                size,
                                size,
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(color)
                        // The card stack in a shade of the same colour (like a themed icon): light
                        // on a dark colour, dark on a light one; the lines and sparkle show the
                        // colour through.
                        val shade =
                            if (androidx.core.graphics.ColorUtils.calculateLuminance(color) < 0.5)
                                androidx.core.graphics.ColorUtils.blendARGB(color, android.graphics.Color.WHITE, 0.85f)
                            else
                                androidx.core.graphics.ColorUtils.blendARGB(color, android.graphics.Color.BLACK, 0.7f)
                        ContextCompat.getDrawable(this, R.drawable.ic_launcher_monochrome)?.mutate()?.let {
                            it.setTint(shade)
                            it.setBounds(0, 0, size, size)
                            it.draw(canvas)
                        }
                        bitmap
                    }
                    .getOrNull()
            artworkColor = color
            artworkBitmap = made
            return made
        }

    private fun post(state: TextToSpeechManager.State) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(NOTIFICATION_ID, build(state)) }
        }
    }

    private fun startInForeground(notification: Notification) {
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
        session.isActive = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    /** "Read aloud · 1.25×". */
    private fun subtitle(): String =
        getString(R.string.read_aloud) + " \u00B7 " + TextToSpeechManager.speedLabel(tts.speed.value)

    private fun action(name: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, ReadAloudService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun build(state: TextToSpeechManager.State): Notification {
        val paused = state is TextToSpeechManager.State.Paused
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tts.title ?: getString(R.string.read_aloud))
            .setContentText(subtitle())
            .setLargeIcon(artwork)
            // The whole notification in the accent colour (where Android colours notifications).
            .setColor(accent)
            .setColorized(true)
            .setOngoing(!paused)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(session.controller.sessionActivity)
            .setDeleteIntent(action(ACTION_STOP, 1))
            .addAction(
                if (paused) R.drawable.ic_read_aloud_play else R.drawable.ic_read_aloud_pause,
                getString(if (paused) R.string.read_aloud_play else R.string.read_aloud_pause),
                action(if (paused) ACTION_PLAY else ACTION_PAUSE, 2),
            )
            .addAction(
                R.drawable.ic_read_aloud_stop,
                getString(R.string.ai_stop_reading),
                action(ACTION_STOP, 3),
            )
            .addAction(
                R.drawable.ic_read_aloud_speed,
                getString(R.string.read_aloud_speed, TextToSpeechManager.speedLabel(tts.speed.value)),
                action(ACTION_SPEED, 4),
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "me.ash.reader.READ_ALOUD_STOP"
        private const val ACTION_PAUSE = "me.ash.reader.READ_ALOUD_PAUSE"
        private const val ACTION_PLAY = "me.ash.reader.READ_ALOUD_PLAY"
        private const val ACTION_SPEED = "me.ash.reader.READ_ALOUD_SPEED"
        private const val UPDATE_EVERY_MS = 1_000L

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

        /** Shows the media notification (and keeps reading alive in the background). */
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
