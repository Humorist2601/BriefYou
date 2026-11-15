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
import androidx.compose.foundation.background
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
}

/** The video in the article: the picture, then an Expressive toolbar under it. */
@Composable
internal fun VideoCard(
    now: BriefPlayer.Now,
    current: Boolean,
    playing: Boolean,
    buffering: Boolean,
    position: Long,
    duration: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (current && !VideoWindow.fullscreen && !VideoWindow.inPip) {
                VideoSurface(Modifier.fillMaxSize())
            } else {
                now.artwork?.let {
                    RYAsyncImage(modifier = Modifier.fillMaxSize(), data = it, contentScale = ContentScale.Crop)
                }
            }
            if (!current && duration > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                ) {
                    Text(
                        formatTime(duration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        VideoToolbar(
            playing = playing,
            buffering = buffering,
            progress = if (duration > 0) position.toFloat() / duration else 0f,
            time = if (duration > 0) formatTime(position) + " / " + formatTime(duration) else formatTime(position),
            seekable = current && duration > 0,
            onSeek = { BriefPlayer.seekTo((it * duration).toLong()) },
            onPlay = onPlay,
            onPip = {
                if (!current) onPlay()
                context.findActivity()?.let { enterPip(it) }
            },
            fullscreenIcon = false,
            onFullscreen = {
                if (!current) onPlay()
                VideoWindow.fullscreen = true
            },
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
    onSeek: (Float) -> Unit,
    onPlay: () -> Unit,
    onPip: () -> Unit,
    fullscreenIcon: Boolean,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = container) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayButton(playing = playing, onClick = onPlay, width = 56.dp, height = 48.dp, corner = 18.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SeekBar(progress = progress, playing = playing && !buffering, enabled = seekable, onSeek = onSeek)
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                JoinedButton(
                    icon = Icons.Rounded.PictureInPictureAlt,
                    description = stringResource(R.string.player_pip),
                    start = 22.dp,
                    end = 8.dp,
                    onClick = onPip,
                )
                JoinedButton(
                    icon = if (fullscreenIcon) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    description = stringResource(if (fullscreenIcon) R.string.player_leave_fullscreen else R.string.player_fullscreen),
                    start = 8.dp,
                    end = 22.dp,
                    onClick = onFullscreen,
                )
            }
        }
    }
}

@Composable
private fun JoinedButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, start: Dp, end: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(20.dp)) }
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
                if (!info.isInPictureInPictureMode && activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) {
                    // The small window was closed (not opened back into the app): stop the video.
                    if (BriefPlayer.state.value.now?.isVideo == true) BriefPlayer.pause()
                }
            }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    // Leaving the app while a video plays opens the small window by itself (Android 12 and later).
    LaunchedEffect(activity, video, s.isPlaying) {
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
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
        activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
    }
}

/** The video sideways and edge to edge, the title at the top and the toolbar along the bottom. */
@Composable
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
        DisposableEffect(window, activity) {
            val before = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
            if (controls) {
                Surface(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
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
                VideoToolbar(
                    playing = s.isPlaying,
                    buffering = s.buffering,
                    progress = s.progress,
                    time = formatTime(s.positionMs) + if (s.durationMs > 0) " / " + formatTime(s.durationMs) else "",
                    seekable = s.durationMs > 0,
                    onSeek = { BriefPlayer.seekTo((it * s.durationMs).toLong()) },
                    onPlay = { BriefPlayer.toggle(context) },
                    onPip = {
                        onClose()
                        context.findActivity()?.let { enterPip(it) }
                    },
                    fullscreenIcon = true,
                    onFullscreen = onClose,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
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
