package me.ash.reader.brief.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.ash.reader.R

/**
 * Plays podcast episodes and videos in the background, with their own media notification (lock
 * screen, Quick Settings, headphone and Bluetooth buttons), laid out like read aloud's: the
 * episode's title and picture, speed (steps through the speeds) · play/pause · stop, back and
 * forward 30 seconds where there is room, and a progress bar. Tapping it opens the article that is playing. Read aloud keeps
 * its own notification (ReadAloudService); only one of them plays at a time. The app talks to it
 * through [BriefPlayer]. Brief You.
 */
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    /**
     * Keeps the place itself when it pauses or stops (also when the app isn't open: Android's
     * "resume", a headphone button).
     */
    private fun savePosition(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        val position =
            me.ash.reader.brief.media.PlaybackPosition(
                id,
                player.currentPosition.coerceAtLeast(0),
                player.duration.coerceAtLeast(0),
                System.currentTimeMillis(),
            )
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { me.ash.reader.brief.media.ArticleMedia.dao?.putPosition(position) }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                // Headphones unplugged: pause, like other players.
                .setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(SKIP_MS)
                .setSeekForwardIncrementMs(SKIP_MS)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        val built =
            MediaSession.Builder(this, player)
                .setId("brief_player")
                .setSessionActivity(PlayerIntents.open(this, null))
                .setCallback(callback)
                .setMediaButtonPreferences(buttons(1f))
                .build()
        session = built
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // The notification opens the article that is playing.
                    built.setSessionActivity(PlayerIntents.open(this@PlayerService, mediaItem?.mediaId))
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    built.setMediaButtonPreferences(buttons(playbackParameters.speed))
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) savePosition(player)
                }
            }
        )
    }

    private val callback =
        object : MediaSession.Callback {
            @OptIn(UnstableApi::class)
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                // Only Brief You, the notification, and Android's own controls (lock screen,
                // Quick Settings, Bluetooth, cars, watches) may connect: other apps can't see what
                // you listen to or control it.
                val allowed =
                    controller.packageName == packageName ||
                        controller.isTrusted ||
                        session.isMediaNotificationController(controller) ||
                        session.isAutomotiveController(controller) ||
                        session.isAutoCompanionController(controller)
                if (!allowed) return MediaSession.ConnectionResult.reject()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(COMMAND_SPEED, Bundle.EMPTY))
                            .add(SessionCommand(COMMAND_STOP, Bundle.EMPTY))
                            .build()
                    )
                    .build()
            }

            /** Android's "resume" (lock screen, Quick Settings, after a restart): the last thing played. */
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val last = BriefPlayer.lastItem(this@PlayerService)
                    ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing played yet"))
                // At the speed kept for it, and with the app following it (place, marked as read).
                val video = last.first.mediaMetadata.extras?.getBoolean(BriefPlayer.EXTRA_VIDEO) == true
                mediaSession.player.playbackParameters = PlaybackParameters(BriefPlayer.savedSpeed(this@PlayerService, video))
                main.post { BriefPlayer.attach(applicationContext) }
                return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(listOf(last.first), 0, last.second))
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                val player = session.player
                when (customCommand.customAction) {
                    COMMAND_SPEED -> {
                        val now = player.playbackParameters.speed
                        val next = BriefPlayer.SPEEDS.firstOrNull { it > now + 0.01f } ?: BriefPlayer.SPEEDS.first()
                        player.playbackParameters = PlaybackParameters(next)
                        // Kept for next time, for podcasts or videos (whichever this is).
                        val video = player.mediaMetadata.extras?.getBoolean(BriefPlayer.EXTRA_VIDEO) == true
                        getSharedPreferences(BriefPlayer.PREFS, MODE_PRIVATE).edit {
                            putFloat(BriefPlayer.speedKey(video), next)
                        }
                    }
                    COMMAND_STOP -> {
                        // The place is kept, then it's unloaded (the notification goes away).
                        savePosition(player)
                        player.pause()
                        player.stop()
                        player.clearMediaItems()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

    /**
     * Speed (showing the speed as a number) · back 30 · the progress bar · forward 30 · stop, with
     * play/pause where Android puts it. Android draws the back and forward buttons on either side
     * of the progress bar and the two others at the ends.
     */
    @OptIn(UnstableApi::class)
    private fun buttons(speed: Float): ImmutableList<CommandButton> =
        ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30)
                .setDisplayName(getString(R.string.player_back_30))
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setDisplayName(getString(R.string.player_forward_30))
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(SpeedIcons.of(speed))
                .setDisplayName(getString(R.string.read_aloud_speed, BriefPlayer.speedLabel(speed)))
                .setSessionCommand(SessionCommand(COMMAND_SPEED, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setDisplayName(getString(R.string.player_stop))
                .setSessionCommand(SessionCommand(COMMAND_STOP, Bundle.EMPTY))
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiped away from recent apps: keeps playing if it is playing, else goes. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val SKIP_MS = 30_000L
        private const val COMMAND_SPEED = "me.ash.reader.PLAYER_SPEED"
        private const val COMMAND_STOP = "me.ash.reader.PLAYER_STOP"
    }
}
