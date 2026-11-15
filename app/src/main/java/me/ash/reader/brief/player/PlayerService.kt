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
import me.ash.reader.R

/**
 * Plays podcast episodes and videos in the background, with their own media notification (lock
 * screen, Quick Settings, headphone and Bluetooth buttons): the episode's title and picture,
 * back and forward 30 seconds, play/pause, speed (steps through the speeds, like read aloud's)
 * and stop, and a progress bar. Tapping it opens the article that is playing. Read aloud keeps
 * its own notification (ReadAloudService); only one of them plays at a time. The app talks to it
 * through [BriefPlayer]. Brief You.
 */
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())

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
            }
        )
    }

    private val callback =
        object : MediaSession.Callback {
            @OptIn(UnstableApi::class)
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(COMMAND_SPEED, Bundle.EMPTY))
                            .add(SessionCommand(COMMAND_STOP, Bundle.EMPTY))
                            .build()
                    )
                    .build()

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
                        // Paused first so the app keeps the place, then unloaded (the
                        // notification goes away).
                        player.pause()
                        main.postDelayed(
                            {
                                player.stop()
                                player.clearMediaItems()
                            },
                            400,
                        )
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

    /** Back 30 · play/pause · forward 30, then speed (showing the speed) and stop. */
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
            CommandButton.Builder(speedIcon(speed))
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

    /** Android's speed icons show the speed for the common ones. */
    @OptIn(UnstableApi::class)
    private fun speedIcon(speed: Float): Int =
        when (Math.round(speed * 10)) {
            8 -> CommandButton.ICON_PLAYBACK_SPEED_0_8
            10 -> CommandButton.ICON_PLAYBACK_SPEED_1_0
            12 -> CommandButton.ICON_PLAYBACK_SPEED_1_2
            15 -> CommandButton.ICON_PLAYBACK_SPEED_1_5
            18 -> CommandButton.ICON_PLAYBACK_SPEED_1_8
            20 -> CommandButton.ICON_PLAYBACK_SPEED_2_0
            else -> CommandButton.ICON_PLAYBACK_SPEED
        }

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
