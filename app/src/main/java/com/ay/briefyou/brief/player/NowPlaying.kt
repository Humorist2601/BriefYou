package com.ay.briefyou.brief.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.brief.feeds.FeedAccent
import com.ay.briefyou.brief.media.ArticleExtras
import com.ay.briefyou.brief.media.ArticleMedia
import com.ay.briefyou.brief.media.PlaybackPosition
import com.ay.briefyou.brief.readaloud.TextToSpeechManager
import com.ay.briefyou.brief.readaloud.readProgress
import com.ay.briefyou.brief.ui.animateColorScheme
import com.ay.briefyou.brief.ui.pictureColorScheme
import com.ay.briefyou.brief.ui.rememberPressFeel
import com.ay.briefyou.infrastructure.preference.LocalAmoledDarkTheme
import com.ay.briefyou.infrastructure.preference.LocalDarkTheme
import com.ay.briefyou.ui.component.base.BYAsyncImage

/* The Now playing pill above the bottom bar and its sheet. */
/** What the pill shows (kept while it slides away after its close button). */
internal data class PillContent(
    val episode: Boolean,
    val feedName: String? = null,
    val feedIcon: String? = null,
    val video: Boolean = false,
    val title: String,
    val line: String,
    val artwork: String?,
    val progress: Float,
    val playing: Boolean,
)

/**
 * The colours for what is playing, from its cover (the feed icon's colour when there is no cover or
 * it has no clear colour), shared by the pill and its sheet. The app's own colours when neither has
 * one, and while it is still being worked out; the change fades in.
 */
@Composable
internal fun rememberCoverScheme(coverUrl: String?, feedIcon: String?): androidx.compose.material3.ColorScheme {
    val context = LocalContext.current
    val cover = coverUrl?.takeIf { it.isNotBlank() }
    val iconUrl = feedIcon?.takeIf { it.isNotBlank() }
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val isAmoled = isDark && LocalAmoledDarkTheme.current.value
    val knownCover = remember(cover) { cover?.let { FeedAccent.knownPictureColor(it) } }
    var seed by remember(cover, iconUrl) { mutableStateOf(knownCover?.second?.let { Color(it) }) }
    LaunchedEffect(cover, iconUrl) {
        if (knownCover?.second != null) return@LaunchedEffect
        val fromCover = cover?.let { FeedAccent.pictureColor(context, it) }
        seed = (fromCover ?: iconUrl?.let { FeedAccent.sourceColor(context, it) })?.let { Color(it) }
    }
    val app = MaterialTheme.colorScheme
    val tinted = remember(seed, isDark, isAmoled) { seed?.let { pictureColorScheme(it, isDark, isAmoled) } }
    return animateColorScheme(tinted ?: app, durationMillis = 300)
}

/**
 * The "now playing" pill above the bottom bar (on Feeds and the article lists): the episode's
 * picture with its progress as a ring, title and time left, and play/pause. While playing it
 * takes the softer accent colour (Twine's chip). Tap: the full controls. Read aloud shows here the
 * same way while it reads (with its sound-wave icon).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    val tts = remember { runCatching { EntryPointAccessors.fromApplication(context.applicationContext, BriefPlayer.Deps::class.java).tts() }.getOrNull() }
    val ttsState = tts?.stateFlow?.collectAsState()?.value
    val reading = ttsState is TextToSpeechManager.State.Reading || ttsState is TextToSpeechManager.State.Paused
    val now = s.now
    var sheet by remember { mutableStateOf(false) }
    val showEpisode = now != null && (s.isPlaying || !reading || ttsState !is TextToSpeechManager.State.Reading)
    val live =
        when {
            showEpisode ->
                PillContent(
                    episode = true,
                    title = now.title,
                    line =
                        if (s.error) stringResource(if (now.isVideo) R.string.player_error_video else R.string.player_error_episode)
                        else if (s.durationMs > 0) stringResource(R.string.player_feed_left, now.feedName, formatTime(s.durationMs - s.positionMs))
                        else now.feedName,
                    artwork = now.artwork,
                    feedName = now.feedName,
                    feedIcon = now.feedIcon,
                    video = now.isVideo,
                    progress = s.progress,
                    playing = s.isPlaying,
                )
            reading ->
                PillContent(
                    episode = false,
                    title = stringResource(R.string.player_reading_aloud, tts.title.orEmpty()),
                    line = stringResource(R.string.read_aloud),
                    artwork = null,
                    feedName = tts.feedName,
                    feedIcon = tts.feedIcon,
                    progress = ttsState.readProgress(),
                    playing = ttsState is TextToSpeechManager.State.Reading,
                )
            else -> null
        }
    // After the close button the pill slides down still showing what it showed.
    val last = remember { arrayOfNulls<PillContent>(1) }
    if (live != null) last[0] = live
    val c = live ?: last[0]?.copy(playing = false) ?: return

    // Tinted by the episode's cover (the same picture colour and scheme as the reading page); read
    // aloud, a video without a picture or a cover still being worked out keeps the app's colours.
    val scheme = rememberCoverScheme(if (c.episode) c.artwork else null, c.feedIcon)
    val transition = updateTransition(c.playing, label = "now playing")
    // Soft tint in both states (playing a little stronger); the ring and the play button carry the
    // stronger colours.
    val container by transition.animateColor(label = "container") {
        if (it) androidx.compose.ui.graphics.lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, 0.35f)
        else scheme.surfaceContainerHighest
    }
    val onContainer by transition.animateColor(label = "content") { scheme.onSurface }
    val ring by animateFloatAsState(c.progress, label = "ring")
    // The tinted colours reach the play button and icons too (the rest of the theme stays).
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, motionScheme = MaterialTheme.motionScheme) {
    // Swipe either way to dismiss: a muted red "Close" pill is uncovered behind the sliding pill, a
    // light buzz marks the point where letting go closes it (same as the old close button: stops).
    val haptic = LocalHapticFeedback.current
    val dragScope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    var boxWidth by remember { mutableStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(live != null) { if (live != null) { dragX.snapTo(0f); armed = false } }
    val dismissAt = boxWidth / 3f
    Box(
        modifier = modifier.onSizeChanged { boxWidth = it.width } // Brief You: the caller gives its width and height (the bar group's, see SharedFilterBar)
            .pointerInput(live != null) {
                if (live == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val past = kotlin.math.abs(dragX.value) > dismissAt
                        dragScope.launch {
                            if (past) {
                                val dir = if (dragX.value > 0) 1f else -1f
                                if (c.episode) BriefPlayer.stop() else tts?.stop()
                                dragX.animateTo(dir * boxWidth * 1.1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium))
                            } else {
                                dragX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                            }
                            armed = false
                        }
                    },
                    onDragCancel = { dragScope.launch { dragX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)); armed = false } },
                ) { change, delta ->
                    change.consume()
                    dragScope.launch { dragX.snapTo(dragX.value + delta) }
                    val now = kotlin.math.abs(dragX.value + delta) > dismissAt
                    if (now != armed) {
                        armed = now
                        if (now) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                }
            },
    ) {
    val toRight = dragX.value > 0f
    Row(
        modifier = Modifier.matchParentSize().clip(CircleShape)
            .background(scheme.errorContainer.copy(alpha = if (dragX.value == 0f) 0f else 1f))
            .padding(horizontal = 22.dp),
        horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fade = (kotlin.math.abs(dragX.value) / dismissAt).coerceIn(0f, 1f)
        Row(
            Modifier.graphicsLayer { alpha = fade },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!toRight) Text(stringResource(R.string.close), style = MaterialTheme.typography.titleSmall, color = scheme.onErrorContainer)
            Icon(Icons.Rounded.Close, null, tint = scheme.onErrorContainer, modifier = Modifier.size(20.dp))
            if (toRight) Text(stringResource(R.string.close), style = MaterialTheme.typography.titleSmall, color = scheme.onErrorContainer)
        }
    }
    Surface(
        onClick = {
            if (live == null) return@Surface
            // Episodes and videos open the player sheet; read aloud opens the article it reads.
            if (c.episode) sheet = true
            else runCatching { PlayerIntents.open(context, PlayerIntents.articleOf(tts?.sourceKey), readAloud = true).send() }
        },
        modifier = Modifier.fillMaxSize().offset { IntOffset(dragX.value.toInt(), 0) },
        shape = CircleShape,
        color = container,
        contentColor = onContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp), // 8 + 48 + 8 = the bottom bar's 64 dp
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ring },
                    modifier = Modifier.size(48.dp),
                    color = scheme.primary,
                    trackColor = onContainer.copy(alpha = 0.16f),
                    strokeWidth = 3.dp,
                )
                if (c.episode) {
                    Artwork(c.artwork, Modifier.size(37.dp), CircleShape, c.feedName, c.feedIcon, c.video, fillIcon = true)
                } else if (c.feedName != null || c.feedIcon != null) {
                    // Read aloud: the feed's icon (its first letter when it has none), full circle.
                    com.ay.briefyou.ui.component.FeedIcon(feedName = c.feedName, iconUrl = c.feedIcon, size = 37.dp)
                } else {
                    Box(
                        Modifier.size(37.dp).clip(CircleShape).background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.GraphicEq, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // While playing a long title slides slowly sideways; paused, it ends with an ellipsis.
                Text(
                    c.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = if (c.playing) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier =
                        if (c.playing) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000, repeatDelayMillis = 2_000, velocity = 24.dp)
                        else Modifier,
                )
                Text(
                    c.line,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayButton(
                playing = c.playing,
                buffering = c.episode && s.buffering,
                onClick = {
                    if (live == null) return@PlayButton
                    if (c.episode) BriefPlayer.toggle(context)
                    else if (ttsState is TextToSpeechManager.State.Reading) tts.pause() else tts?.resume()
                },
                size = 48.dp,
                // Always on screen: turns for a few seconds after play, then settles.
                spinFor = 4_000,
            )
        }
    }
    }
    }
    if (sheet && now != null) NowPlayingSheet(onDismiss = { sheet = false })
}

/**
 * The pill's full controls: the episode's picture (a video: the video itself, large, with
 * picture-in-picture and full screen on its corner), title, progress and the same buttons as the
 * article's card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingSheet(onDismiss: () -> Unit) {
    // Picture-in-picture shows only the video: the sheet (its own window) closes.
    LaunchedEffect(VideoWindow.inPip) { if (VideoWindow.inPip) onDismiss() }
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    val now = s.now ?: return
    // The video shows here while the sheet is open (it can only show in one place at a time).
    androidx.compose.runtime.DisposableEffect(now.isVideo) {
        VideoWindow.inSheet = now.isVideo
        onDispose { VideoWindow.inSheet = false }
    }
    // The sheet takes the cover's colours too (its own window, so the theme is given again).
    val scheme = rememberCoverScheme(now.artwork, now.feedIcon)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, motionScheme = MaterialTheme.motionScheme) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = com.ay.briefyou.brief.ui.rememberFullSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (now.isVideo) {
                VideoFrame(
                    now = now,
                    current = true,
                    showVideo = !VideoWindow.fullscreen && !VideoWindow.inPip,
                    duration = s.durationMs,
                    onPlay = { BriefPlayer.toggle(context) },
                    corner = 24.dp,
                )
            } else {
                // The picture grows a little while playing (with a bounce) and settles when paused.
                val art by animateFloatAsState(
                    if (s.isPlaying) 1f else 0.9f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "artwork",
                )
                Artwork(now.artwork, Modifier.size(180.dp).graphicsLayer { scaleX = art; scaleY = art }, RoundedCornerShape(32.dp), now.feedName, now.feedIcon)
            }
            Spacer(Modifier.height(14.dp))
            Text(now.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(now.feedName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            if (s.error) {
                PlayError(video = now.isVideo, url = now.url, onRetry = { BriefPlayer.retry(context) })
            } else {
                SeekRow(position = s.positionMs, duration = s.durationMs, playing = s.isPlaying, enabled = s.durationMs > 0)
            }
            Spacer(Modifier.height(6.dp))
            MediaControls(
                current = true,
                playing = s.isPlaying,
                buffering = s.buffering,
                speed = s.speed,
                sleep = s.sleep,
                onPlay = { BriefPlayer.toggle(context) },
            )
        }
    }
    }
}
