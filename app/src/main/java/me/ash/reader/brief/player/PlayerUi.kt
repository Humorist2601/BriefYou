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

/** The open article's episode, video and comments link (provided by the reading page). */
val LocalArticleExtras = compositionLocalOf<ArticleExtras?> { null }

/** An article's extras, loaded once per article (see [ArticleMedia.of]). */
@Composable
fun rememberArticleExtras(articleId: String?, picture: String?): ArticleExtras? {
    val extras by
        produceState<ArticleExtras?>(null, articleId, picture) {
            value = articleId?.let { ArticleMedia.of(it, picture) }
        }
    return extras?.takeIf { it.articleId == articleId }
}

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
                    val web = me.ash.reader.brief.media.WebUrl.of(url) ?: return@OutlinedButton
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

private fun speedLabel(speed: Float): String = BriefPlayer.speedLabel(speed)

/**
 * The player's buttons, after Twine's: the big play button in the middle, and once the episode is
 * loaded the speed, back 30, forward 30 and sleep timer spring in around it (each keeps its own
 * place, so nothing moves). Speed opens the speed sheet; the moon opens the sleep timer sheet.
 */
@Composable
private fun MediaControls(
    moving: Boolean = true,
    current: Boolean,
    playing: Boolean,
    buffering: Boolean,
    speed: Float,
    sleep: BriefPlayer.Sleep,
    onPlay: () -> Unit,
) {
    var speedSheet by remember { mutableStateOf(false) }
    var sleepSheet by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Extra(current) { SpeedPill(speed) { speedSheet = true } }
        Extra(current) { SkipButton(forward = false, enabled = current) }
        PlayButton(playing = playing, buffering = buffering, onClick = onPlay, size = 64.dp, moving = moving)
        Extra(current) { SkipButton(forward = true, enabled = current) }
        Extra(current) {
            TonalIcon(
                icon = Icons.Outlined.Bedtime,
                description = stringResource(R.string.player_sleep_timer),
                active = sleep != BriefPlayer.Sleep.Off,
            ) { sleepSheet = true }
        }
    }
    if (speedSheet) SpeedSheet(onDismiss = { speedSheet = false })
    if (sleepSheet) SleepTimerSheet(onDismiss = { sleepSheet = false })
}

/** One of the buttons around play: it springs in (a little past its size) once [visible]. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Extra(visible: Boolean, content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        // The plain one (not the row's, which would grow sideways).
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter =
                fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.4f),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.4f),
        ) {
            content()
        }
    }
}

/**
 * The episode's picture; without one, the feed's icon on the tonal colour; without that, a
 * microphone (a film for a video).
 */
@Composable
internal fun Artwork(
    url: String?,
    modifier: Modifier,
    shape: Shape,
    feedName: String? = null,
    feedIcon: String? = null,
    video: Boolean = false,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (feedIcon != null) {
            me.ash.reader.ui.component.FeedIcon(feedName = feedName, iconUrl = feedIcon, size = minOf(minOf(maxWidth, maxHeight) * 0.6f, 96.dp))
        } else {
            Icon(
                if (video) Icons.Outlined.Movie else Icons.Outlined.Mic,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(minOf(26.dp, maxWidth * 0.5f)),
            )
        }
        if (url != null) {
            RYAsyncImage(modifier = Modifier.matchParentSize(), data = url, contentScale = ContentScale.Crop, contentDescription = null)
        }
    }
}

/** A Material shape morph drawn at the size it is given. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * The big play/pause, after Twine's: a circle in the accent colour while paused; playing, it
 * grows with a bounce, becomes a slowly turning nine-sided cookie in the softer accent colour,
 * and the icon turns into pause. While the episode loads, a loading shape shows in it. Pressed, it
 * squeezes. [size] is its size while playing (a little smaller while paused).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    /** It turns only while this is true (off screen, nothing needs to move). */
    moving: Boolean = true,
    /** Turns for this long after playing starts and then settles (the pill), instead of all along. */
    spinFor: Long? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.9f)
    val transition = updateTransition(playing, label = "play button")
    val shapeProgress by transition.animateFloat({ spring(stiffness = Spring.StiffnessMediumLow) }, label = "shape") { if (it) 1f else 0f }
    val shown by
        transition.animateDp(
            { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium) },
            label = "size",
        ) { if (it) size else size * 0.86f }
    val container by transition.animateColor(label = "colour") { if (it) scheme.primaryContainer else scheme.primary }
    val content by transition.animateColor(label = "icon colour") { if (it) scheme.onPrimaryContainer else scheme.onPrimary }
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    // The cookie turns slowly while playing, and stops where it is when paused.
    val turn = remember { Animatable(0f) }
    LaunchedEffect(playing, moving) {
        if (!playing || !moving) return@LaunchedEffect
        val until = spinFor?.let { System.currentTimeMillis() + it }
        while (until == null || System.currentTimeMillis() < until) {
            turn.animateTo(turn.value + 40f, tween(durationMillis = 667, easing = LinearEasing))
            turn.snapTo(turn.value % 360f)
        }
        // Settles on one of the cookie's nine turns (it looks the same there), with a bounce.
        turn.animateTo(turn.value + 40f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        turn.snapTo(turn.value % 360f)
    }
    val description = stringResource(if (playing) R.string.player_pause else R.string.player_play)
    Box(
        modifier =
            modifier
                .size(size)
                .semantics { contentDescription = description }
                .clickable(interactionSource = press.interaction, indication = null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(shown)
                    .graphicsLayer {
                        scaleX = press.scale
                        scaleY = press.scale
                        rotationZ = turn.value
                    }
                    .clip(MorphShape(morph, shapeProgress.coerceIn(0f, 1f)))
                    .background(container)
        )
        if (buffering) {
            LoadingIndicator(modifier = Modifier.size(size * 0.6f), color = content)
        } else {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.5f)) togetherWith
                        (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.5f))
                },
                label = "play icon",
            ) { isPlaying ->
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = content,
                    modifier = Modifier.size(size * 0.42f).graphicsLayer { scaleX = press.scale; scaleY = press.scale },
                )
            }
        }
    }
}

/** Back or forward 30 seconds: squeezes when pressed, and its arrow swings the way it skips. */
@Composable
private fun SkipButton(forward: Boolean, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swing = remember { Animatable(0f) }
    val press = rememberPressFeel(rest = 26.dp, held = 14.dp, squeezeTo = 0.88f)
    IconButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            BriefPlayer.seekBy(if (forward) PlayerService.SKIP_MS else -PlayerService.SKIP_MS)
            scope.launch {
                swing.animateTo(if (forward) 40f else -40f, spring(stiffness = Spring.StiffnessHigh))
                swing.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        },
        enabled = enabled,
        interactionSource = press.interaction,
        modifier = with(press) { Modifier.size(52.dp).squeeze() },
    ) {
        Icon(
            if (forward) Icons.Rounded.Forward30 else Icons.Rounded.Replay30,
            stringResource(if (forward) R.string.player_forward_30 else R.string.player_back_30),
            modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = swing.value },
        )
    }
}

// The speed pill and the moon are the same size.
private val SIDE_BUTTON_WIDTH = 52.dp
private val SIDE_BUTTON_HEIGHT = 36.dp

/** The speed, as a tonal pill; a new speed rolls in over the old one. */
@Composable
internal fun SpeedPill(speed: Float, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val press = rememberPressFeel(rest = 20.dp, held = 12.dp, squeezeTo = 0.9f)
    val description = stringResource(R.string.player_speed)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shape = RoundedCornerShape(press.corner),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = press.interaction,
        modifier = with(press) { Modifier.size(SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).squeeze() }.semantics { contentDescription = "$description ${speedLabel(speed)}" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            SpeedText(speed, MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SpeedText(speed: Float, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = speed,
        transitionSpec = {
            val up = targetState > initialState
            (fadeIn() + scaleIn(initialScale = 0.7f) + slideInVertically { if (up) it else -it }) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.7f) + slideOutVertically { if (up) -it / 2 else it / 2 })
        },
        label = "speed",
    ) {
        Text(speedLabel(it), style = style, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TonalIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val press = rememberPressFeel(rest = 20.dp, held = 12.dp, squeezeTo = 0.9f)
    val scheme = MaterialTheme.colorScheme
    val transition = updateTransition(active, label = "sleep timer")
    val container by transition.animateColor(label = "container") { if (it) scheme.primary else scheme.secondaryContainer }
    val content by transition.animateColor(label = "content") { if (it) scheme.onPrimary else scheme.onSecondaryContainer }
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shape = RoundedCornerShape(press.corner),
        color = container,
        contentColor = content,
        interactionSource = press.interaction,
        modifier = with(press) { Modifier.size(SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).squeeze() },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, modifier = Modifier.size(20.dp)) }
    }
}

/**
 * The progress: Expressive's wavy line while playing, a flat one while paused. Tap or drag to
 * move; a light tick is felt for each second passed while dragging, and the player only jumps
 * when the finger lifts (seeking on every move fought the player).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeekBar(
    progress: Float,
    playing: Boolean,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 0,
    /** The wave moves only while this is true (off screen, it lies flat). */
    moving: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration by rememberUpdatedState(durationMs)
    val tick = remember { longArrayOf(-1) }
    fun dragTo(share: Float) {
        drag = share
        // A tick per second (per percent when the length isn't known).
        // A tick per second, but at most about a hundred along the bar (a long episode would
        // otherwise tick on every pixel and buzz).
        val unit = if (duration > 0) maxOf(1_000L, duration / 100) else 0L
        val step = if (unit > 0) (share * duration / unit).toLong() else (share * 100).toLong()
        if (tick[0] >= 0 && step != tick[0]) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tick[0] = step
    }
    val shown by animateFloatAsState(drag ?: progress.coerceIn(0f, 1f), label = "seek")
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onSeek((it.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tick[0] = -1
                            dragTo((it.x / size.width).coerceIn(0f, 1f))
                        },
                        onDragEnd = {
                            drag?.let(onSeek)
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    ) { change, _ ->
                        dragTo((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (playing && moving && drag == null) {
            LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The speed sheet (from the speed pill): the speed, large, and a slider of the speeds offered. */
@Composable
fun SpeedSheet(onDismiss: () -> Unit) {
    // Picture-in-picture shows only the video: the sheet (its own window) closes.
    LaunchedEffect(VideoWindow.inPip) { if (VideoWindow.inPip) onDismiss() }
    val context = LocalContext.current
    val s by BriefPlayer.state.collectAsState()
    // The shared speed sheet (read aloud's too); the player follows each step.
    me.ash.reader.brief.ui.SpeedSheet(
        title = stringResource(R.string.player_speed_title),
        speeds = BriefPlayer.SPEEDS,
        speed = s.speed,
        onDismiss = onDismiss,
        onStep = { BriefPlayer.setSpeed(context, it) },
    )
}

/** The sleep timer sheet (from the moon): off, 15 to 60 minutes, or the end of the episode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    // Picture-in-picture shows only the video: the sheet (its own window) closes.
    LaunchedEffect(VideoWindow.inPip) { if (VideoWindow.inPip) onDismiss() }
    val haptic = LocalHapticFeedback.current
    val s by BriefPlayer.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // The app's sheet design: title, divider, then the choices.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.player_sleep_timer), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 6.dp))
            val options =
                listOf(
                    0 to stringResource(R.string.player_sleep_off),
                    15 to stringResource(R.string.player_minutes, 15),
                    30 to stringResource(R.string.player_minutes, 30),
                    45 to stringResource(R.string.player_minutes, 45),
                    60 to stringResource(R.string.player_hour),
                    -1 to stringResource(R.string.player_end_of_episode),
                )
            val selected =
                when (val sleep = s.sleep) {
                    BriefPlayer.Sleep.Off -> 0
                    BriefPlayer.Sleep.EndOfEpisode -> -1
                    is BriefPlayer.Sleep.Minutes -> sleep.minutes
                }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.chunked(3).forEachIndexed { row, items ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEachIndexed { col, (minutes, label) ->
                            val outer = 26.dp
                            val inner = 10.dp
                            val shape =
                                RoundedCornerShape(
                                    topStart = if (row == 0 && col == 0) outer else inner,
                                    topEnd = if (row == 0 && col == 2) outer else inner,
                                    bottomStart = if (row == 1 && col == 0) outer else inner,
                                    bottomEnd = if (row == 1 && col == 2) outer else inner,
                                )
                            val on = minutes == selected
                            val scheme = MaterialTheme.colorScheme
                            val transition = updateTransition(on, label = "sleep option")
                            val container by transition.animateColor(label = "container") { if (it) scheme.primary else scheme.secondaryContainer }
                            val content by transition.animateColor(label = "content") { if (it) scheme.onPrimary else scheme.onSecondaryContainer }
                            val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.94f)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    BriefPlayer.setSleep(minutes)
                                },
                                shape = shape,
                                color = container,
                                contentColor = content,
                                interactionSource = press.interaction,
                                modifier = with(press) { Modifier.weight(1f).height(52.dp).squeeze() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            val sleep = s.sleep
            AnimatedVisibility(visible = sleep is BriefPlayer.Sleep.Minutes) {
                val endsAt = (sleep as? BriefPlayer.Sleep.Minutes)?.endsAt ?: 0L
                val left by produceState(endsAt - System.currentTimeMillis(), endsAt) {
                    while (true) {
                        value = endsAt - System.currentTimeMillis()
                        kotlinx.coroutines.delay(1_000)
                    }
                }
                Text(
                    stringResource(R.string.player_sleep_left, formatTime(left)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What the pill shows (kept while it slides away after its close button). */
private data class PillContent(
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
                    title = now!!.title,
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
                    title = stringResource(R.string.player_reading_aloud, tts?.title.orEmpty()),
                    line = stringResource(R.string.read_aloud),
                    artwork = null,
                    progress = ttsState?.readProgress() ?: 0f,
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
                    else if (ttsState is TextToSpeechManager.State.Reading) tts?.pause() else tts?.resume()
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
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
