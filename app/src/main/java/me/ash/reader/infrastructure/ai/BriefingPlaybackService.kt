package me.ash.reader.infrastructure.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import me.ash.reader.R
import timber.log.Timber

/**
 * Reads the latest morning briefing aloud like a short podcast, with play/pause/stop on the lock
 * screen and in the notification shade. Uses the speech engine and rate chosen in Settings.
 */
@AndroidEntryPoint
class BriefingPlaybackService : Service() {

    @Inject lateinit var briefing: MorningBriefing

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var session: MediaSession
    private var segments: List<String> = emptyList()
    private var index = 0
    private var playing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session =
            MediaSession(this, "BriefYouBriefing").apply {
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() = play()

                        override fun onPause() = pause()

                        override fun onStop() = stopPlayback()

                        override fun onSkipToNext() = skip(1)

                        override fun onSkipToPrevious() = skip(-1)
                    }
                )
                isActive = true
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground promptly after startForegroundService().
        startInForeground()
        when (intent?.action) {
            ACTION_PLAY -> {
                if (segments.isEmpty()) loadBriefing()
                play()
            }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    private fun loadBriefing() {
        val b = briefing.latest.value
        if (b == null) {
            stopPlayback()
            return
        }
        segments =
            listOf(b.title) +
                b.text.lines()
                    .map { it.trim().trimStart('•', '*', '-', ' ') }
                    .filter { it.isNotBlank() }
        index = 0
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, b.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.read_you))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, -1)
                .build()
        )
    }

    private fun ensureTts(onReady: () -> Unit) {
        tts?.let {
            if (ttsReady) onReady()
            return
        }
        val prefs = getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
        val engine = prefs.getString("engine", null)
        val rate = prefs.getFloat("rate", 1f)
        val listener =
            TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.setSpeechRate(rate)
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}

                            override fun onDone(utteranceId: String?) {
                                if (!playing) return
                                index++
                                if (index >= segments.size) stopPlayback() else speakCurrent()
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                Timber.w("briefing TTS error")
                                stopPlayback()
                            }
                        }
                    )
                    onReady()
                } else {
                    Timber.w("briefing TTS init failed $status")
                    stopPlayback()
                }
            }
        tts = if (engine != null) TextToSpeech(this, listener, engine) else TextToSpeech(this, listener)
    }

    private fun play() {
        if (segments.isEmpty()) return
        playing = true
        updateState()
        ensureTts { speakCurrent() }
    }

    private fun speakCurrent() {
        val text = segments.getOrNull(index) ?: return stopPlayback()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "seg$index")
        updateState()
    }

    private fun pause() {
        playing = false
        tts?.stop()
        updateState()
    }

    private fun skip(delta: Int) {
        index = (index + delta).coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        if (playing) speakCurrent() else updateState()
    }

    private fun stopPlayback() {
        playing = false
        tts?.stop()
        session.setPlaybackState(
            PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 1f).build()
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateState() {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    index.toLong(),
                    1f,
                )
                .build()
        )
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.briefing_playback_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        fun action(icon: Int, title: Int, action: String) =
            Notification.Action.Builder(
                    Icon.createWithResource(this, icon),
                    getString(title),
                    PendingIntent.getService(
                        this,
                        action.hashCode(),
                        Intent(this, BriefingPlaybackService::class.java).setAction(action),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build()

        val toggle =
            if (playing) action(android.R.drawable.ic_media_pause, R.string.briefing_pause, ACTION_PAUSE)
            else action(android.R.drawable.ic_media_play, R.string.briefing_play, ACTION_PLAY)
        val stop = action(android.R.drawable.ic_menu_close_clear_cancel, R.string.briefing_stop, ACTION_STOP)

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(briefing.latest.value?.title ?: getString(R.string.briefing_title))
            .setContentText(segments.getOrNull(index) ?: "")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    1,
                    MorningBriefing.openBriefingIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .addAction(toggle)
            .addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "briefing_playback"
        private const val NOTIFICATION_ID = 0xB41F
        const val ACTION_PLAY = "brief_you.briefing.PLAY"
        const val ACTION_PAUSE = "brief_you.briefing.PAUSE"
        const val ACTION_STOP = "brief_you.briefing.STOP"

        fun playIntent(context: Context) =
            Intent(context, BriefingPlaybackService::class.java).setAction(ACTION_PLAY)
    }
}
