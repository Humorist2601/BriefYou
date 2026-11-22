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
import com.ay.briefyou.R
import com.ay.briefyou.brief.media.ArticleExtras
import com.ay.briefyou.brief.media.ArticleMedia
import com.ay.briefyou.brief.media.PlaybackPosition
import com.ay.briefyou.brief.readaloud.TextToSpeechManager
import com.ay.briefyou.brief.readaloud.readProgress
import com.ay.briefyou.brief.ui.rememberPressFeel
import com.ay.briefyou.ui.component.base.BYAsyncImage

/** The open article's episode, video and comments link (provided by the reading page). */
val LocalArticleExtras = compositionLocalOf<ArticleExtras?> { null }

/** 1:05:09 or 12:04. */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The article's podcast episode or video, under its title: one Expressive player card for both.
 * Audio: the episode's picture, feed and status; a video: the video itself (its own shape, with
 * picture-in-picture and full screen on its corner, see [VideoFrame]). Then the progress with the
 * times beside it, and speed · back 30 · play · forward 30 · sleep timer. When the file can't be
 * played, the progress makes way for Try again and Open in browser.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArticleMediaCard(title: String, feedName: String, artwork: String?, modifier: Modifier = Modifier, feedIcon: String? = null) {
    val extras = LocalArticleExtras.current ?: return
    val url = extras.mediaUrl ?: return
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    val current = s.isFor(extras.articleId)
    val now =
        remember(extras, title, feedName, artwork, feedIcon) {
            BriefPlayer.Now(
                articleId = extras.articleId,
                url = url,
                kind = extras.mediaKind ?: ArticleExtras.AUDIO,
                title = title,
                feedName = feedName,
                artwork = extras.image ?: artwork,
                feedIcon = feedIcon,
            )
        }
    val saved by
        produceState<PlaybackPosition?>(null, extras.articleId, current) {
            value = if (current) null else runCatching { ArticleMedia.dao?.position(extras.articleId) }.getOrNull()
        }
    val position = if (current) s.positionMs else saved?.positionMs ?: 0L
    val duration = if (current && s.durationMs > 0) s.durationMs else saved?.durationMs?.takeIf { it > 0 } ?: extras.durationMs ?: 0L
    val playing = current && s.isPlaying
    val onPlay: () -> Unit = {
        if (current) BriefPlayer.toggle(context) else BriefPlayer.play(context, now, extras.durationMs)
    }

    // Off screen (scrolled past), nothing in it moves: no redrawing on every frame.
    var onScreen by remember { mutableStateOf(true) }
    Surface(
        modifier =
            modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(if (now.isVideo) 8.dp else 14.dp)) {
            if (now.isVideo) {
                VideoFrame(
                    now = now,
                    current = current,
                    showVideo = current && !VideoWindow.fullscreen && !VideoWindow.inPip && !VideoWindow.inSheet,
                    duration = duration,
                    onPlay = onPlay,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(now.artwork, Modifier.size(44.dp), RoundedCornerShape(14.dp), now.feedName, now.feedIcon)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = feedName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                when {
                                    current && s.buffering -> stringResource(R.string.player_loading)
                                    !current && position > 0 && (duration <= 0 || position < duration - 5_000) ->
                                        stringResource(R.string.player_picks_up, formatTime(position))
                                    duration > 0 -> stringResource(R.string.player_episode_length, ((duration + 30_000) / 60_000).toInt())
                                    else -> stringResource(R.string.player_episode)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = if (now.isVideo) 6.dp else 0.dp)) {
                Spacer(Modifier.height(10.dp))
                if (current && s.error) {
                    PlayError(video = now.isVideo, url = url, onRetry = { BriefPlayer.retry(context) })
                } else {
                    SeekRow(
                        position = position,
                        duration = duration,
                        playing = playing,
                        enabled = current && duration > 0,
                        moving = onScreen,
                    )
                }
                Spacer(Modifier.height(6.dp))
                MediaControls(
                    moving = onScreen,
                    current = current,
                    playing = playing,
                    buffering = current && s.buffering,
                    speed = if (current) s.speed else BriefPlayer.savedSpeed(context, now.isVideo),
                    sleep = s.sleep,
                    onPlay = onPlay,
                )
            }
        }
    }
}

/** The progress with the time played before it and the time left after it. */
@Composable
internal fun SeekRow(position: Long, duration: Long, playing: Boolean, enabled: Boolean, moving: Boolean = true) {
    // The times keep one width (even-width digits, sized for the longest time this episode can
    // show), so the progress bar doesn't change length as they tick.
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widest = "−" + formatTime(maxOf(duration, position)).replace(Regex("[0-9]"), "0")
    val timeWidth = remember(widest, style) { with(density) { measurer.measure(widest, style).size.width.toDp() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            formatTime(position),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(timeWidth),
        )
        SeekBar(
            progress = if (duration > 0) position.toFloat() / duration else 0f,
            playing = playing,
            enabled = enabled,
            durationMs = duration,
            onSeek = { BriefPlayer.seekTo((it * duration).toLong()) },
            modifier = Modifier.weight(1f),
            moving = moving,
        )
        Text(
            if (duration > 0) "−" + formatTime(duration - position) else "",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(timeWidth),
        )
    }
}

/** Couldn't be played: says so, with Try again and Open in browser (the file's own address). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayError(video: Boolean, url: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (video) R.string.player_error_video else R.string.player_error_episode),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.player_try_again), maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    val web = com.ay.briefyou.brief.media.WebUrl.of(url) ?: return@OutlinedButton
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(web))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.player_open_browser), maxLines = 1)
            }
        }
    }
}

internal fun speedLabel(speed: Float): String = BriefPlayer.speedLabel(speed)
