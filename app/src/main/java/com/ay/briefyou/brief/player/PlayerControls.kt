package com.ay.briefyou.brief.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.animation.core.animateDpAsState
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

/* The player's controls: play, skip, speed, the sleep timer, the seek bar and the artwork (used by the episode card, the video player and the Now playing sheet). */
/**
 * The player's buttons, after Twine's: the big play button in the middle, and once the episode is
 * loaded the speed, back 30, forward 30 and sleep timer spring in around it (each keeps its own
 * place, so nothing moves). Speed opens the speed sheet; the moon opens the sleep timer sheet.
 */
@Composable
internal fun MediaControls(
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
internal fun androidx.compose.foundation.layout.RowScope.Extra(visible: Boolean, content: @Composable () -> Unit) {
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
    fillIcon: Boolean = false, // the feed icon fills the circle, as in the lists (the pill)
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (feedIcon != null) {
            com.ay.briefyou.ui.component.FeedIcon(feedName = feedName, iconUrl = feedIcon, size = if (fillIcon) minOf(maxWidth, maxHeight) else minOf(minOf(maxWidth, maxHeight) * 0.6f, 96.dp))
        } else {
            Icon(
                if (video) Icons.Outlined.Movie else Icons.Outlined.Mic,
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(minOf(26.dp, maxWidth * 0.5f)),
            )
        }
        if (url != null) {
            BYAsyncImage(modifier = Modifier.matchParentSize(), data = url, contentScale = ContentScale.Crop, contentDescription = null)
        }
    }
}

/** A Material shape morph drawn at the size it is given. */
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
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
internal fun SkipButton(forward: Boolean, enabled: Boolean) {
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
internal val SIDE_BUTTON_WIDTH = 52.dp
internal val SIDE_BUTTON_HEIGHT = 36.dp

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
internal fun SpeedText(speed: Float, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = speed,
        transitionSpec = {
            val up = targetState > initialState
            // A plain roll up or down (no scaling about the centre, which with a text that changes
            // width looked diagonal); the size changes at once and the text is centred.
            (fadeIn(androidx.compose.animation.core.tween(180)) +
                slideInVertically(androidx.compose.animation.core.tween(220)) { if (up) it else -it }) togetherWith
                (fadeOut(androidx.compose.animation.core.tween(120)) +
                    slideOutVertically(androidx.compose.animation.core.tween(220)) { if (up) -it else it }) using
                androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
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
 * The progress: Expressive's wavy line while playing, flat while paused, with a dot riding the
 * wave's running end. Tap or drag to move; a light tick is felt for each second passed while dragging, and the player only jumps
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
        SeekLine(progress = { shown }, wavy = playing && moving && drag == null, held = drag != null, showDot = enabled)
    }
}

/**
 * The seek bar's line, drawn like Material's wavy progress indicator (the same 4 dp line and track,
 * 40 dp waves 3 dp high that drift one wavelength a second, the 4 dp gap before the track and its
 * end stop, the wave settling flat in the first 10 % and last 5 %) but by us, so the dot can sit
 * exactly on the wave's running end: Material's own wave moves on its own clock and can't be
 * followed. The wave eases flat when paused (and while dragging).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeekLine(progress: () -> Float, wavy: Boolean, held: Boolean, showDot: Boolean) {
    val color = WavyProgressIndicatorDefaults.indicatorColor
    val trackColor = WavyProgressIndicatorDefaults.trackColor
    val p = progress()
    val amplitude by animateFloatAsState(
        if (wavy && p > 0.1f && p < 0.95f) 1f else 0f,
        androidx.compose.animation.core.tween(500),
        label = "seekWave",
    )
    // The wave's drift: one wavelength a second, only while it shows.
    val drift = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(amplitude > 0f) {
        if (amplitude > 0f) {
            while (true) {
                drift.animateTo(
                    drift.value + 1f,
                    androidx.compose.animation.core.tween(1_000, easing = androidx.compose.animation.core.LinearEasing),
                )
                drift.snapTo(drift.value % 1f)
            }
        }
    }
    val dot by animateDpAsState(if (held) 16.dp else 12.dp, label = "seekDot")
    val path = remember { androidx.compose.ui.graphics.Path() }
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
        val stroke = 4.dp.toPx()
        val cap = stroke / 2
        val gap = 4.dp.toPx()
        val wavelength = 40.dp.toPx()
        val waveHigh = 3.dp.toPx() * amplitude
        val mid = size.height / 2
        val w = size.width
        val head = (progress().coerceIn(0f, 1f) * w).coerceIn(cap, w - cap)
        val shift = drift.value * wavelength
        fun yAt(x: Float) = mid + waveHigh * kotlin.math.sin(2 * Math.PI * (x + shift) / wavelength).toFloat()

        // The track after the gap, and its end stop.
        val trackStart = head + gap + stroke
        if (trackStart < w - cap) {
            drawLine(trackColor, Offset(trackStart, mid), Offset(w - cap, mid), stroke, StrokeCap.Round)
        }
        if (head < w - gap - stroke * 2) drawCircle(color, radius = cap, center = Offset(w - cap, mid))

        // The played part, as a wave (or flat).
        path.rewind()
        path.moveTo(cap, yAt(cap))
        var x = cap
        while (x < head) {
            x = minOf(x + 2f, head)
            path.lineTo(x, yAt(x))
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))

        // The dot on the running end.
        if (showDot) {
            val r = dot.toPx() / 2
            drawCircle(color, radius = r, center = Offset(head.coerceIn(r, w - r), yAt(head)))
        }
    }
}
