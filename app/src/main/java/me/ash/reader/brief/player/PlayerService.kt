package me.ash.reader.brief.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import me.ash.reader.R

/**
 * Plays podcast episodes and videos in the background, with their own media notification (lock
 * screen, Quick Settings, headphone and Bluetooth buttons): the episode's title and picture,
 * play/pause, back and forward 30 seconds and a progress bar. Read aloud keeps its own
 * notification (ReadAloudService); only one of them plays at a time. The app talks to it through
 * [BriefPlayer]. Brief You.
 */
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null

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
        val openApp =
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        session =
            MediaSession.Builder(this, player)
                .setId("brief_player")
                .apply { openApp?.let { setSessionActivity(it) } }
                // Back and forward 30 seconds on each side of play/pause.
                .setMediaButtonPreferences(
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
                    )
                )
                .build()
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
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val SKIP_MS = 30_000L
    }
}
