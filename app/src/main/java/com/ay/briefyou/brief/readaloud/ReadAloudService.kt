package com.ay.briefyou.brief.readaloud

import com.ay.briefyou.brief.readaloud.TextToSpeechManager // Brief You
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.ay.briefyou.R

/**
 * Read aloud in the system's media notification and controls, like a music app: the title, a
 * progress bar with the time (follows the words being spoken; drag it to jump), play/pause, and
 * Speed and Stop on the right, the same as the podcast player's, also on the lock screen and in Quick Settings. A media session
 * service like the podcast player's, so Android shows both the same way. It keeps reading going when you leave the
 * article or the app, and goes away when reading stops or finishes.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class ReadAloudService : MediaSessionService() {

    @Inject lateinit var tts: TextToSpeechManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private lateinit var player: ReadAloudPlayer
    private lateinit var session: MediaSession

    /** The last time and kind of state given to the system, to avoid updating on every word. */
    private var lastUpdate = 0L
    private var lastKind: Class<*>? = null
    private var lastDuration = -1L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        player = ReadAloudPlayer()
        session =
            MediaSession.Builder(this, player)
                .setId("BriefYouReadAloud")
                .setCallback(
                    object : MediaSession.Callback {
                        // Like the podcast player: only Brief You, its notification and Android's
                        // own controls (lock screen, Quick Settings, Bluetooth, cars, watches).
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            val allowed =
                                controller.packageName == packageName ||
                                    controller.isTrusted ||
                                    session.isMediaNotificationController(controller) ||
                                    session.isAutomotiveController(controller) ||
                                    session.isAutoCompanionController(controller)
                            if (!allowed) return MediaSession.ConnectionResult.reject()
                            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                                // Read aloud is one article: no previous or next, so Android leaves
                                // those slots empty and the progress bar runs edge to edge.
                                .setAvailablePlayerCommands(
                                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                        .removeAll(
                                            Player.COMMAND_SEEK_TO_PREVIOUS,
                                            Player.COMMAND_SEEK_TO_NEXT,
                                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                                        )
                                        .build()
                                )
                                .setAvailableSessionCommands(
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                        .add(SessionCommand(ACTION_SPEED, Bundle.EMPTY))
                                        .add(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                                        .build()
                                )
                                .build()
                        }

                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            when (customCommand.customAction) {
                                ACTION_STOP -> tts.stop()
                                ACTION_SPEED -> tts.cycleSpeed()
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                )
                .setMediaButtonPreferences(buttons())
                .build()
        // The same media notification as the podcast player's (Media3 builds it from the session),
        // so Android lays the buttons out the same way: play/pause, then Speed and Stop.
        ensureChannel(this)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )
        addSession(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    /** Swiping the app away from recent apps doesn't stop reading (as before). */
    override fun onTaskRemoved(rootIntent: Intent?) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> tts.stop()
            ACTION_SPEED -> tts.cycleSpeed()
        }
        ensureChannel(this)
        updateSession(tts.state, force = true)
        if (!started) {
            started = true
            // Only the first start, to be in time: Media3 replaces it with the media notification
            // straight away, and starts this service again itself; a placeholder then would cover
            // the media notification.
            startInForeground(placeholder())
            scope.launch {
                tts.stateFlow.collect { state ->
                    when (state) {
                        is TextToSpeechManager.State.Reading,
                        is TextToSpeechManager.State.Paused,
                        TextToSpeechManager.State.Preparing -> {
                            updateSession(state, force = false)
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
                    }
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
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
        }
        // The media controls (Quick Settings, lock screen) open the article being read too.
        session.setSessionActivity(
            com.ay.briefyou.brief.player.PlayerIntents.open(this, com.ay.briefyou.brief.player.PlayerIntents.articleOf(tts.sourceKey), readAloud = true)
        )
        if (force) session.setMediaButtonPreferences(buttons())
        player.update()
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

    private var artworkBytesFor: android.graphics.Bitmap? = null
    private var artworkBytes: ByteArray? = null

    /** The artwork as PNG bytes for the media session (made again only with the artwork). */
    private val artworkData: ByteArray?
        get() {
            val bitmap = artwork ?: return null
            if (artworkBytesFor !== bitmap) {
                artworkBytes =
                    runCatching {
                            java.io.ByteArrayOutputStream().also {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }.toByteArray()
                        }
                        .getOrNull()
                artworkBytesFor = bitmap
            }
            return artworkBytes
        }

    /** The same Speed (as a number) and Stop buttons as the podcast player's, on the right. */
    private fun buttons(): ImmutableList<CommandButton> =
        ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(com.ay.briefyou.brief.player.SpeedIcons.of(tts.speed.value))
                .setDisplayName(getString(R.string.read_aloud_speed, TextToSpeechManager.speedLabel(tts.speed.value)))
                .setSessionCommand(SessionCommand(ACTION_SPEED, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setDisplayName(getString(R.string.player_stop))
                .setSessionCommand(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )

    /**
     * Read aloud as a Media3 player, so Android's media controls show and drive it: the title,
     * the length and position (the bar follows the words; drag it to jump), play/pause and stop.
     * It only reports what read aloud does; the speaking itself stays in TextToSpeechManager.
     */
    @OptIn(UnstableApi::class)
    private inner class ReadAloudPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        fun update() = invalidateState()

        override fun getState(): State {
            val state = tts.state
            val reading =
                state is TextToSpeechManager.State.Reading ||
                    state is TextToSpeechManager.State.Paused ||
                    state == TextToSpeechManager.State.Preparing
            val builder =
                State.Builder()
                    .setAvailableCommands(
                        Player.Commands.Builder()
                            .addAll(
                                Player.COMMAND_PLAY_PAUSE,
                                Player.COMMAND_STOP,
                                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                                Player.COMMAND_GET_METADATA,
                            )
                            .build()
                    )
                    .setPlayWhenReady(
                        state !is TextToSpeechManager.State.Paused,
                        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                    )
            if (!reading) return builder.setPlaybackState(Player.STATE_IDLE).build()
            val metadata =
                MediaMetadata.Builder()
                    .setTitle(tts.title ?: getString(R.string.read_aloud))
                    .setArtist(subtitle())
                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
            return builder
                .setPlaybackState(
                    if (state == TextToSpeechManager.State.Preparing) Player.STATE_BUFFERING else Player.STATE_READY
                )
                .setPlaylist(
                    listOf(
                        MediaItemData.Builder("read-aloud")
                            .setMediaItem(MediaItem.Builder().setMediaId("read-aloud").setMediaMetadata(metadata).build())
                            .setDurationUs(lastDuration.coerceAtLeast(0) * 1_000)
                            .setIsSeekable(true)
                            .build()
                    )
                )
                .setContentPositionMs { tts.positionMs }
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) tts.resume() else tts.pause()
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            tts.stop()
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            tts.seekTo(positionMs)
            return Futures.immediateVoidFuture()
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
        player.update()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    /** "Read aloud" (the speed shows on its own button now, as a number). */
    private fun subtitle(): String = getString(R.string.read_aloud)

    /** The notification shown for a moment while the media notification takes over. */
    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tts.title ?: getString(R.string.read_aloud))
            .setSilent(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.ay.briefyou.READ_ALOUD_STOP"
        private const val ACTION_SPEED = "com.ay.briefyou.READ_ALOUD_SPEED"
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
