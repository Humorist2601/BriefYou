package me.ash.reader.brief.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import me.ash.reader.R
import me.ash.reader.ui.component.base.RYAsyncImage

/**
 * Video: in the article (the card), full screen (sideways, edge to edge, a floating toolbar the
 * full width at the bottom) and picture-in-picture (a small window over other apps, with
 * Android's own controls). All three show the same player, so the video carries on as it moves
 * between them. YouTube videos stay in YouTube's own player inside the article. Brief You.
 */
object VideoWindow {
    /** Full screen is open. */
    var fullscreen by mutableStateOf(false)

    /** The app is in picture-in-picture (only the video is drawn then). */
    var inPip by mutableStateOf(false)

    /** The full-controls sheet shows the video (so the article's card shows its picture). */
    var inSheet by mutableStateOf(false)
}

/**
 * The video, in its own shape (16:9 until it is known; very tall ones are shown 3:4 with bars),
 * for the article's card and the full-controls sheet: the playing picture (or the episode's
 * picture before it starts), its length in one corner until it starts, and picture-in-picture
 * with full screen as a small joined pair in the other. Tapping it plays and pauses it.
 */
@Composable
internal fun VideoFrame(
    now: BriefPlayer.Now,
    current: Boolean,
    showVideo: Boolean,
    duration: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
) {
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    // Its shape: while loaded, the player's; else the one seen before; else 16:9.
    val saved by
        androidx.compose.runtime.produceState<Float?>(null, now.articleId) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BriefPlayer.savedAspect(context, now.articleId) }
        }
    val aspect = ((if (current) s.videoAspect else null) ?: saved)?.coerceIn(0.75f, 2.4f) ?: (16f / 9f)
    val shown by androidx.compose.animation.core.animateFloatAsState(aspect, label = "video shape")
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(shown)
                .clip(RoundedCornerShape(corner))
                .background(Color.Black)
                // Tap: plays, or pauses and plays again.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPlay() },
        contentAlignment = Alignment.Center,
    ) {
        if (showVideo) {
            VideoSurface(Modifier.fillMaxSize())
        } else {
            // Before it plays: the episode's picture, else the feed's icon, else a film.
            Artwork(
                now.artwork,
                Modifier.fillMaxSize(),
                RoundedCornerShape(0.dp),
                now.feedName,
                now.feedIcon,
                video = true,
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !current && duration > 0,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.6f),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.6f),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    formatTime(duration),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
        // The same joined buttons as the top bars (Feeds, the lists, the article): pressed, one
        // widens and squeezes the other.
        val pipLabel = stringResource(R.string.player_pip)
        val fullscreenLabel = stringResource(R.string.player_fullscreen)
        me.ash.reader.brief.ui.TopBarButtonGroup(
            actions =
                listOf(
                    me.ash.reader.brief.ui.TopBarAction(Icons.Rounded.PictureInPictureAlt, pipLabel) {
                        if (!current) onPlay()
                        context.findActivity()?.let { enterPip(it) }
                    },
                    me.ash.reader.brief.ui.TopBarAction(Icons.Rounded.Fullscreen, fullscreenLabel) {
                        if (!current) onPlay()
                        VideoWindow.fullscreen = true
                    },
                ),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 2.dp),
        )
    }
}

/** The video's picture, fitted to its own shape. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(modifier: Modifier) {
    val player = BriefPlayer.player ?: return
    ContentFrame(player, modifier)
}

/**
 * Play/pause (a shaped button in the accent colour), the wavy progress with the time, and
 * picture-in-picture with full screen as a joined pair.
 */
@Composable
private fun VideoToolbar(
    playing: Boolean,
    buffering: Boolean,
    progress: Float,
    time: String,
    seekable: Boolean,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    onPlay: () -> Unit,
    onPip: () -> Unit,
    fullscreenIcon: Boolean,
    onFullscreen: () -> Unit,
    speed: Float,
    sleepOn: Boolean,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayButton(playing = playing, buffering = buffering, onClick = onPlay, size = 48.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SeekBar(progress = progress, playing = playing && !buffering, enabled = seekable, durationMs = durationMs, onSeek = onSeek)
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Speed and the sleep timer, as on the card.
            SpeedPill(speed, onSpeed)
            TonalIcon(Icons.Outlined.Bedtime, stringResource(R.string.player_sleep_timer), sleepOn, onSleep)
            me.ash.reader.brief.ui.TopBarButtonGroup(
                actions =
                    listOf(
                        me.ash.reader.brief.ui.TopBarAction(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.player_pip), onClick = onPip),
                        me.ash.reader.brief.ui.TopBarAction(
                            if (fullscreenIcon) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            stringResource(if (fullscreenIcon) R.string.player_leave_fullscreen else R.string.player_fullscreen),
                            onClick = onFullscreen,
                        ),
                    ),
            )
        }
    }
}

/**
 * Full screen and picture-in-picture, put once around the app (in BriefRoot): full screen as a
 * window over everything, sideways; in picture-in-picture, only the video.
 */
@Composable
fun VideoHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val s by BriefPlayer.state.collectAsState()
    val video = s.now?.isVideo == true

    // Picture-in-picture: follow the activity, and close the video when its window is closed.
    DisposableEffect(activity) {
        val listener =
            Consumer<PictureInPictureModeChangedInfo> { info ->
                VideoWindow.inPip = info.isInPictureInPictureMode
                // The small window shows only the video: full screen (its own window) closes.
                if (info.isInPictureInPictureMode) VideoWindow.fullscreen = false
                // Closed (✕), the screen ends up stopped; opened back into the app, it ends up
                // started. Android reports the two in either order, so it's checked a moment later.
                if (!info.isInPictureInPictureMode) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        {
                            if (activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) != true &&
                                BriefPlayer.state.value.now?.isVideo == true
                            ) {
                                BriefPlayer.pause()
                            }
                        },
                        500,
                    )
                }
            }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    // Leaving the app while a video plays opens the small window by itself (Android 12 and later).
    LaunchedEffect(activity, video, s.isPlaying, s.videoAspect) {
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(pipRatio())
                        .setAutoEnterEnabled(video && s.isPlaying)
                        .build()
                )
            }
        }
    }

    // The app stays underneath (its screens keep their place); in picture-in-picture only the
    // video shows, over it.
    content()
    if (VideoWindow.inPip && video) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            VideoSurface(Modifier.fillMaxSize())
        }
        return
    }
    if (VideoWindow.fullscreen && video) {
        FullscreenVideo(onClose = { VideoWindow.fullscreen = false })
    } else if (VideoWindow.fullscreen && !video) {
        LaunchedEffect(Unit) { VideoWindow.fullscreen = false }
    }
}

/** Opens the small window now (the toolbar's button). */
fun enterPip(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(pipRatio()).build())
    }
}

/** The small window in the video's shape (Android allows from 1:2.39 to 2.39:1). */
private fun pipRatio(): Rational {
    val aspect = (BriefPlayer.state.value.videoAspect ?: (16f / 9f)).coerceIn(1f / 2.39f, 2.39f)
    return Rational((aspect * 1000).toInt(), 1000)
}

/** The video sideways and edge to edge, the title at the top and the toolbar along the bottom. */
@Composable
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
private fun FullscreenVideo(onClose: () -> Unit) {
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    val now = s.now ?: return
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? DialogWindowProvider)?.window
        val activity = context.findActivity()
        // Wide videos turn sideways; tall ones stay upright (also once the shape becomes known).
        val tall = (s.videoAspect ?: (16f / 9f)) < 1f
        // How the screen was turned before, put back on close.
        val before = remember(activity) { activity?.requestedOrientation }
        DisposableEffect(activity, tall) {
            activity?.requestedOrientation =
                if (tall) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {}
        }
        DisposableEffect(window, activity) {
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                WindowInsetsControllerCompat(it, it.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { activity?.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        var controls by remember { mutableStateOf(true) }
        var speedSheet by remember { mutableStateOf(false) }
        var sleepSheet by remember { mutableStateOf(false) }
        // While it plays, the controls go after 3 seconds (not while a sheet is open).
        LaunchedEffect(controls, s.isPlaying, speedSheet, sleepSheet) {
            if (controls && s.isPlaying && !speedSheet && !sleepSheet) {
                kotlinx.coroutines.delay(3_000)
                controls = false
            }
        }
        if (speedSheet) SpeedSheet(onDismiss = { speedSheet = false })
        if (sleepSheet) SleepTimerSheet(onDismiss = { sleepSheet = false })
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { controls = !controls },
        ) {
            VideoSurface(Modifier.fillMaxSize())
            // Couldn't be played: says so, with Try again and Open in browser.
            if (s.error) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 420.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(Modifier.padding(16.dp)) { PlayError(video = true, url = now.url, onRetry = { BriefPlayer.retry(context) }) }
                }
            }
            // The controls slide in from the edges and back out (tap the video).
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            androidx.compose.animation.AnimatedVisibility(
                visible = controls,
                modifier = Modifier.align(Alignment.TopStart),
                enter = slideInVertically(spatial) { -it } + fadeIn(effects),
                exit = slideOutVertically(spatial) { -it } + fadeOut(effects),
            ) {
                Surface(
                    onClick = onClose,
                    modifier = Modifier.safeDrawingPadding().padding(16.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                    // A see-through colour has no matching text colour of its own (the title was black).
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.player_leave_fullscreen), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(now.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(now.feedName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = controls,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spatial) { it } + fadeIn(effects),
                exit = slideOutVertically(spatial) { it } + fadeOut(effects),
            ) {
                VideoToolbar(
                    playing = s.isPlaying,
                    buffering = s.buffering,
                    progress = s.progress,
                    time = formatTime(s.positionMs) + if (s.durationMs > 0) " / " + formatTime(s.durationMs) else "",
                    seekable = s.durationMs > 0,
                    durationMs = s.durationMs,
                    onSeek = { BriefPlayer.seekTo((it * s.durationMs).toLong()) },
                    onPlay = { BriefPlayer.toggle(context) },
                    onPip = {
                        VideoWindow.fullscreen = false // carries on in the small window
                        context.findActivity()?.let { enterPip(it) }
                    },
                    fullscreenIcon = true,
                    onFullscreen = onClose,
                    speed = s.speed,
                    sleepOn = s.sleep != BriefPlayer.Sleep.Off,
                    onSpeed = { speedSheet = true },
                    onSleep = { sleepSheet = true },
                    modifier = Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                    container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                )
            }
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
