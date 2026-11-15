package me.ash.reader.brief.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.ash.reader.brief.media.ArticleExtras
import me.ash.reader.brief.media.ArticleMedia
import me.ash.reader.brief.media.PlaybackPosition
import me.ash.reader.brief.readaloud.TextToSpeechManager
import me.ash.reader.domain.service.RssService

/**
 * The app's side of the podcast and video player ([PlayerService] plays). The article's player
 * card, the reading bar and the "now playing" pill read [state] and call the functions here.
 *
 * - Carries on where you stopped (positions are kept per article).
 * - Speed is kept for next time; back and forward 30 seconds; a sleep timer (minutes, or the end
 *   of the episode) that fades out over its last seconds.
 * - A podcast episode heard past the share chosen in Usage → Audio and video is marked as read.
 * - One thing plays at a time: an episode pauses read aloud, and read aloud pauses the episode.
 *
 * Brief You.
 */
object BriefPlayer {

    /** What is loaded. */
    data class Now(
        val articleId: String,
        val url: String,
        val kind: String,
        val title: String,
        val feedName: String,
        val artwork: String?,
    ) {
        val isVideo get() = kind == ArticleExtras.VIDEO
    }

    sealed interface Sleep {
        data object Off : Sleep
        data class Minutes(val minutes: Int, val endsAt: Long) : Sleep
        data object EndOfEpisode : Sleep
    }

    data class State(
        val now: Now? = null,
        val isPlaying: Boolean = false,
        val buffering: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val speed: Float = 1f,
        val sleep: Sleep = Sleep.Off,
        /** It couldn't be played (the card offers Try again and Open in browser). */
        val error: Boolean = false,
        /** A video's shape (width / height), once known. */
        val videoAspect: Float? = null,
    ) {
        val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        fun isFor(articleId: String?) = articleId != null && now?.articleId == articleId
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun tts(): TextToSpeechManager
        fun rssService(): RssService
    }

    /** Speeds offered, 0.8× to 3×. */
    val SPEEDS = listOf(0.8f, 1f, 1.2f, 1.4f, 1.6f, 1.8f, 2f, 2.5f, 3f)

    /** 1.2× or 2×. */
    fun speedLabel(speed: Float): String =
        (if (speed % 1f == 0f) "%.0f".format(speed) else "%.1f".format(speed)) + "×"

    const val PREFS = "brief_player"

    /** Podcasts and videos keep their own speed. */
    fun speedKey(video: Boolean) = if (video) "speed_video" else "speed"

    /** In a media item's metadata extras: it is a video (for the notification's speed button). */
    const val EXTRA_VIDEO = "brief_video"
    private const val KEY_MARK_READ_AT = "mark_read_at"
    private const val SAVE_EVERY_MS = 5_000L
    private const val FADE_MS = 6_000L

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var appContext: Context? = null
    // Read by the video's picture, so it appears as soon as the player is connected.
    private var controller by androidx.compose.runtime.mutableStateOf<MediaController?>(null)
    private var connecting = false
    private val waiting = ArrayList<(MediaController) -> Unit>()
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private var lastSaved = 0L
    private var markedRead: String? = null
    // Between asking for an item and it being set (so "nothing loaded" isn't mistaken for a stop).
    private var loading = false

    private fun secure(url: String) = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    /** The player, for a video's picture (null until something was played). */
    val player: Player? get() = controller

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The share of a podcast episode after which it is marked as read (1 = when finished). */
    fun markReadAt(context: Context): Float = prefs(context).getFloat(KEY_MARK_READ_AT, 0.9f)

    /** The speed kept for podcasts or for videos. */
    fun savedSpeed(context: Context, video: Boolean): Float = prefs(context).getFloat(speedKey(video), 1f)

    fun setMarkReadAt(context: Context, share: Float) = prefs(context).edit { putFloat(KEY_MARK_READ_AT, share) }

    private fun withController(context: Context, block: (MediaController) -> Unit) {
        appContext = context.applicationContext
        controller?.let { block(it); return }
        waiting += block
        if (connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlayerService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val c = runCatching { future.get() }.getOrNull() ?: run { waiting.clear(); return@addListener }
                controller = c
                c.addListener(listener)
                waiting.toList().forEach { it(c) }
                waiting.clear()
            },
            MoreExecutors.directExecutor(),
        )
    }

    private val listener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                // Stopped from the notification: nothing is loaded any more.
                if (player.mediaItemCount == 0 && _state.value.now != null && !loading) {
                    ticker?.cancel()
                    setSleep(Sleep.Off)
                    _state.update { State() }
                    return
                }
                refresh(player)
                if (player.isPlaying) startTicker() else {
                    ticker?.cancel()
                    savePosition(force = true)
                }
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_ENDED) {
                    finished()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.update { it.copy(error = true, isPlaying = false, buffering = false) }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                val aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                _state.update { it.copy(videoAspect = aspect) }
            }
        }

    private fun refresh(player: Player) {
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                // Only while it is meant to be playing (not after a seek while paused).
                buffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                speed = player.playbackParameters.speed,
            )
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker =
            main.launch {
                while (isActive) {
                    controller?.let { refresh(it) }
                    savePosition(force = false)
                    checkMarkRead()
                    delay(500)
                }
            }
    }

    /** Plays [now], from where it was left. Pauses read aloud. */
    fun play(context: Context, now: Now, durationHintMs: Long? = null) {
        pauseReadAloud(context)
        if (_state.value.now?.articleId == now.articleId && _state.value.now?.url == now.url && !_state.value.error) {
            withController(context) { it.play() }
            return
        }
        savePosition(force = true)
        val speed = prefs(context).getFloat(speedKey(now.isVideo), 1f)
        _state.update { State(now = now, durationMs = durationHintMs ?: 0, speed = speed, sleep = it.sleep) }
        markedRead = null
        loading = true
        main.launch {
            val resumeAt =
                runCatching { ArticleMedia.dao?.position(now.articleId) }.getOrNull()
                    ?.takeIf { it.durationMs <= 0 || it.positionMs < it.durationMs - 5_000 }
                    ?.positionMs ?: 0L
            withController(context) { c ->
                val item =
                    MediaItem.Builder()
                        .setMediaId(now.articleId)
                        // The app only uses secure addresses: an http:// one is tried as https://
                        // (most hosts have both); if that fails, the card says so.
                        .setUri(secure(now.url))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(now.title)
                                .setArtist(now.feedName)
                                .setExtras(android.os.Bundle().apply { putBoolean(EXTRA_VIDEO, now.isVideo) })
                                .apply { now.artwork?.let { setArtworkUri(Uri.parse(secure(it))) } }
                                .build()
                        )
                        .build()
                c.setMediaItem(item, resumeAt)
                c.playbackParameters = PlaybackParameters(speed)
                c.prepare()
                c.play()
                loading = false
            }
        }
    }

    /** Tries again after it couldn't be played. */
    fun retry(context: Context) {
        if (_state.value.now == null) return
        _state.update { it.copy(error = false) }
        pauseReadAloud(context)
        withController(context) {
            it.prepare()
            it.play()
        }
    }

    fun toggle(context: Context) {
        val c = controller ?: return
        if (_state.value.error) {
            retry(context)
            return
        }
        if (c.isPlaying) c.pause() else {
            pauseReadAloud(context)
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0, c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        refresh(c)
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs.coerceAtLeast(0))
        refresh(c)
    }

    fun setSpeed(context: Context, speed: Float) {
        prefs(context).edit { putFloat(speedKey(_state.value.now?.isVideo == true), speed) }
        controller?.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** Stops and unloads (the pill's close). */
    fun stop() {
        savePosition(force = true)
        setSleep(Sleep.Off)
        controller?.run {
            stop()
            clearMediaItems()
        }
        ticker?.cancel()
        _state.update { State(speed = it.speed) }
    }

    /** Sets the sleep timer: [minutes] from now, the end of the episode (-1), or off (0). */
    fun setSleep(minutes: Int) =
        setSleep(
            when {
                minutes < 0 -> Sleep.EndOfEpisode
                minutes == 0 -> Sleep.Off
                else -> Sleep.Minutes(minutes, System.currentTimeMillis() + minutes * 60_000L)
            }
        )

    private fun setSleep(sleep: Sleep) {
        sleepJob?.cancel()
        controller?.volume = 1f
        _state.update { it.copy(sleep = sleep) }
        if (sleep !is Sleep.Minutes) return
        sleepJob =
            main.launch {
                while (isActive) {
                    val left = sleep.endsAt - System.currentTimeMillis()
                    if (left <= 0) break
                    // Fades out over the last seconds instead of stopping mid-sentence.
                    controller?.volume = (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
                    delay(if (left < FADE_MS) 100 else 1_000)
                }
                controller?.pause()
                controller?.volume = 1f
                _state.update { it.copy(sleep = Sleep.Off) }
            }
    }

    /**
     * Read aloud is starting: the episode pauses. Read aloud starts from a background thread, and
     * the player may only be used from the main one (it crashed there).
     */
    fun pauseForReadAloud() {
        main.launch { controller?.takeIf { it.isPlaying }?.pause() }
    }

    private fun pauseReadAloud(context: Context) {
        runCatching { EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).tts().pause() }
    }

    private fun savePosition(force: Boolean) {
        val now = _state.value.now ?: return
        val c = controller ?: return
        // Finished: its place is cleared (see finished), not kept at the end.
        if (c.playbackState == Player.STATE_ENDED || c.mediaItemCount == 0) return
        val time = System.currentTimeMillis()
        if (!force && time - lastSaved < SAVE_EVERY_MS) return
        lastSaved = time
        val position = PlaybackPosition(now.articleId, c.currentPosition.coerceAtLeast(0), c.duration.coerceAtLeast(0), time)
        main.launch(Dispatchers.IO) { runCatching { ArticleMedia.dao?.putPosition(position) } }
    }

    /** A podcast episode heard past the chosen share is marked as read (once). */
    private fun checkMarkRead() {
        val s = _state.value
        val now = s.now ?: return
        if (now.isVideo || markedRead == now.articleId || s.durationMs <= 0) return
        val context = appContext ?: return
        val share = markReadAt(context)
        if (share >= 1f || s.progress < share) return
        markRead(context, now)
    }

    private fun finished() {
        val now = _state.value.now ?: return
        main.launch(Dispatchers.IO) { runCatching { ArticleMedia.dao?.clearPosition(now.articleId) } }
        if (_state.value.sleep == Sleep.EndOfEpisode) _state.update { it.copy(sleep = Sleep.Off) }
        val context = appContext ?: return
        if (!now.isVideo && markedRead != now.articleId) markRead(context, now)
    }

    private fun markRead(context: Context, now: Now) {
        markedRead = now.articleId
        main.launch(Dispatchers.IO) {
            runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
                    .rssService().get().batchMarkAsRead(setOf(now.articleId), isUnread = false)
            }
        }
    }
}
