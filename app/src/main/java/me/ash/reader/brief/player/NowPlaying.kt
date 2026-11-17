package me.ash.reader.brief.player

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
import me.ash.reader.R
import me.ash.reader.brief.media.ArticleExtras
import me.ash.reader.brief.media.ArticleMedia
import me.ash.reader.brief.media.PlaybackPosition
import me.ash.reader.brief.readaloud.TextToSpeechManager
import me.ash.reader.brief.readaloud.readProgress
import me.ash.reader.brief.ui.rememberPressFeel
import me.ash.reader.ui.component.base.RYAsyncImage

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
                    progress = ttsState.readProgress(),
                    playing = ttsState is TextToSpeechManager.State.Reading,
                )
            else -> null
        }
    // After the close button the pill slides down still showing what it showed.
    val last = remember { arrayOfNulls<PillContent>(1) }
    if (live != null) last[0] = live
    val c = live ?: last[0]?.copy(playing = false) ?: return

    val scheme = MaterialTheme.colorScheme
    val transition = updateTransition(c.playing, label = "now playing")
    val container by transition.animateColor(label = "container") { if (it) scheme.primaryContainer else scheme.surfaceContainerHighest }
    val onContainer by transition.animateColor(label = "content") { if (it) scheme.onPrimaryContainer else scheme.onSurface }
    val ring by animateFloatAsState(c.progress, label = "ring")
    Surface(
        onClick = { if (c.episode && live != null) sheet = true },
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 24.dp),
        shape = CircleShape,
        color = container,
        contentColor = onContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ring },
                    modifier = Modifier.size(52.dp),
                    color = scheme.primary,
                    trackColor = onContainer.copy(alpha = 0.16f),
                    strokeWidth = 3.dp,
                )
                if (c.episode) {
                    Artwork(c.artwork, Modifier.size(40.dp), CircleShape, c.feedName, c.feedIcon, c.video)
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(scheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.GraphicEq, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            IconButton(
                onClick = { if (c.episode) BriefPlayer.stop() else tts?.stop() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.player_stop), modifier = Modifier.size(20.dp))
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = me.ash.reader.brief.ui.rememberFullSheetState()) {
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
