package me.ash.reader.ui.motion

import android.graphics.Path
import android.os.Build
import android.view.RoundedCorner
import android.view.animation.PathInterpolator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * The predictive back gesture of every screen except Triage (which keeps its pull and toss), made
 * to match stock Android's own back animation between screens (the system's "cross-activity"
 * animation in AOSP: DefaultCrossActivityBackAnimation and CrossActivityBackAnimation).
 *
 * While you swipe:
 * - The screen you are leaving shrinks to 90%. Swiping from the left edge, it also slides right
 *   until its right edge is 8 dp from the screen's edge; from the right edge it stays centred.
 * - It follows your finger up and down, slowing down, and never closer than 8 dp to the edge.
 * - The screen you go back to sits 96 dp to the left and shrinks to 90% along with it, follows the
 *   finger up and down too, and is dimmed (80% in dark mode, 20% in light mode).
 * - Both have the display's own rounded corners.
 * - The progress goes through the system's back gesture curve, so the motion shows early.
 *
 * When you let go (450 ms in stock Android, on the emphasized curve): the screen behind grows back
 * to full screen, the dim fades away, and the screen you leave goes back to full size while sliding
 * a further 96 dp to the right, fading out within the first fifth of the animation.
 */

/** The system's back gesture curve (Material: "don't use linear progress"). */
private val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/** Android's emphasized curve, the exact path the system uses after you let go. */
private val EmphasizedEasing: Easing =
    PathInterpolator(
            Path().apply {
                moveTo(0f, 0f)
                cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
            }
        )
        .let { interpolator -> Easing { interpolator.getInterpolation(it) } }

/** The Y-shift curve (a plain decelerate interpolator in the system). */
private fun decelerate(x: Float): Float = 1f - (1f - x) * (1f - x)

private const val MAX_SCALE = 0.9f
private val MARGIN = 8.dp
private val ENTERING_START_OFFSET = 96.dp
private const val MAX_SCRIM_DARK = 0.8f
private const val MAX_SCRIM_LIGHT = 0.2f

/**
 * How long a predictive back takes in the navigation host from the first touch to the end. After
 * you let go, the host plays what is left of it, so letting go halfway leaves about the 450 ms the
 * system uses.
 */
const val BACK_PREVIEW_DURATION = 900

/**
 * One back gesture, shared by the screen you leave (which sees the gesture and writes this) and the
 * screen behind it (which reads it). Only one back gesture runs at a time.
 */
internal object BackPreviewState {
    var active by mutableStateOf(false)
    var released by mutableStateOf(false)
    /** The raw gesture progress when you let go. */
    var releasedAt by mutableFloatStateOf(1f)
    /** True when the gesture came from the right edge. */
    var fromRight by mutableStateOf(false)
    var startY by mutableFloatStateOf(0f)
    var rawDy by mutableFloatStateOf(0f)

    fun reset() {
        active = false
        released = false
        releasedAt = 1f
        fromRight = false
        rawDy = 0f
    }
}

/**
 * True while a predictive back gesture is showing. Screens use it to stop drawing shared elements
 * (like the filter bar) above both screens, so everything on the screen moves with the card.
 */
val isBackPreviewActive: Boolean
    get() = BackPreviewState.active

/**
 * The navigation host's transition for a predictive back. Both screens are drawn by
 * [BackPreviewPage]; these halves only keep them on screen for the whole gesture.
 */
fun backPreviewPopTransform(): ContentTransform =
    fadeIn(animationSpec = tween(BACK_PREVIEW_DURATION), initialAlpha = 1f) togetherWith
        fadeOut(animationSpec = tween(BACK_PREVIEW_DURATION), targetAlpha = 1f)

/** Where a screen is: its scale (about its centre), offsets, and opacity. */
private class Placement(val scale: Float, val x: Float, val y: Float, val alpha: Float = 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The screens' positions before you let go, for a back gesture at [raw] progress. [width] and
 * [height] are the screen size, [margin] and [offset] are 8 dp and 96 dp in px.
 */
private fun preCommit(
    raw: Float,
    entering: Boolean,
    width: Float,
    height: Float,
    margin: Float,
    offset: Float,
    rawDy: Float,
): Placement {
    val p = BackGestureEasing.transform(raw.coerceIn(0f, 1f))
    val scale = lerp(1f, MAX_SCALE, p)
    // Up and down with the finger: at most half the screen of finger travel, slowing down, and
    // never closer than the margin to the edge.
    val ratio = min(height / 2f, abs(rawDy)) / (height / 2f)
    val room = max(0f, (height - height * scale) / 2f - margin)
    val y = room * decelerate(ratio) * (if (rawDy < 0f) -1f else 1f)
    return if (entering) {
        Placement(scale, -offset, y)
    } else {
        // From the left edge the card slides right, to 8 dp from the edge; from the right it stays.
        val shift = if (BackPreviewState.fromRight) 0f else (width * (1f - MAX_SCALE) / 2f - margin)
        Placement(scale, shift * p, y)
    }
}

/**
 * Where a screen is when the gesture is at [raw] progress (for the screen behind, the host's
 * progress runs from 1 down to 0, so its [raw] is 1 minus that).
 */
private fun placement(
    raw: Float,
    entering: Boolean,
    width: Float,
    height: Float,
    margin: Float,
    offset: Float,
): Placement {
    val s = BackPreviewState
    if (!s.released) return preCommit(raw, entering, width, height, margin, offset, s.rawDy)
    if (raw <= s.releasedAt + 0.0005f) {
        // Cancelled: everything runs back to where it started, the finger offset with it.
        val follow = if (s.releasedAt > 0f) (raw / s.releasedAt).coerceIn(0f, 1f) else 0f
        return preCommit(raw, entering, width, height, margin, offset, s.rawDy * follow)
    }
    // Let go and going back: from where things were to the end, on the emphasized curve.
    val linear = ((raw - s.releasedAt) / (1f - s.releasedAt)).coerceIn(0f, 1f)
    val t = EmphasizedEasing.transform(linear)
    val from = preCommit(s.releasedAt, entering, width, height, margin, offset, s.rawDy)
    return if (entering) {
        Placement(lerp(from.scale, 1f, t), lerp(from.x, 0f, t), lerp(from.y, 0f, t))
    } else {
        // Back to full size, its left edge moving a further 96 dp right, fading out quickly.
        val left = width * (1f - from.scale) / 2f + from.x
        Placement(
            scale = lerp(from.scale, 1f, t),
            x = lerp(from.x, left + offset, t),
            y = lerp(from.y, 0f, t),
            alpha = max(1f - linear * 5f, 0f),
        )
    }
}

/** How much of the dim is left: all of it until you let go, then it fades away with the rest. */
private fun dimFade(raw: Float): Float {
    val s = BackPreviewState
    return if (s.released && raw > s.releasedAt) {
        1f - ((raw - s.releasedAt) / (1f - s.releasedAt)).coerceIn(0f, 1f)
    } else 1f
}

private fun GraphicsLayerScope.place(placement: Placement) {
    scaleX = placement.scale
    scaleY = placement.scale
    translationX = placement.x
    translationY = placement.y
    alpha = placement.alpha
}

/** The display's own corner radius, like the system uses, or 28 dp where the phone doesn't say. */
@Composable
private fun displayCornerRadius(): Float {
    val view = LocalView.current
    val fallback = with(LocalDensity.current) { 28.dp.toPx() }
    return remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?.radius
                ?.toFloat()
                ?.takeIf { it > 0f } ?: fallback
        } else {
            fallback
        }
    }
}

/**
 * Wraps a screen of the navigation host so the predictive back gesture plays stock Android's back
 * animation on it.
 *
 * The host moves its transition along with the gesture, so each screen reads the progress from its
 * own transition. The finger position, the edge and the moment you let go come from the back
 * gesture itself. After you let go, the host plays the rest of the transition: forwards when going
 * back, backwards when cancelled.
 */
@Composable
fun BackPreviewPage(content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val gestureFlow =
        remember(dispatcher) {
            dispatcher?.transitionState
                ?: MutableStateFlow<NavigationEventTransitionState>(NavigationEventTransitionState.Idle)
        }
    val gesture by gestureFlow.collectAsState()
    val inGesture = gesture is NavigationEventTransitionState.InProgress

    val exiting =
        transition.currentState == EnterExitState.Visible &&
            transition.targetState == EnterExitState.PostExit
    val entering =
        transition.currentState == EnterExitState.PreEnter &&
            transition.targetState == EnterExitState.Visible

    val density = LocalDensity.current
    val margin = with(density) { MARGIN.toPx() }
    val offset = with(density) { ENTERING_START_OFFSET.toPx() }
    val corner = displayCornerRadius()
    val backdrop = MaterialTheme.colorScheme.surface
    val scrim = if (backdrop.luminance() < 0.5f) MAX_SCRIM_DARK else MAX_SCRIM_LIGHT

    // Only a predictive back gets this long, linear timeline (it follows the gesture); any other
    // navigation ends it at once, so the screen is not kept around longer than its transition.
    val fraction =
        transition.animateFloat(
            transitionSpec = {
                if (inGesture || BackPreviewState.active) {
                    tween(BACK_PREVIEW_DURATION, easing = LinearEasing)
                } else {
                    snap()
                }
            },
            label = "backPreview",
        ) {
            if (it == EnterExitState.Visible) 0f else 1f
        }

    // The screen being left sees the gesture and records it for both screens.
    var leading by remember { mutableStateOf(false) }
    LaunchedEffect(exiting, gesture) {
        val g = gesture
        val s = BackPreviewState
        if (exiting && g is NavigationEventTransitionState.InProgress) {
            val event = g.latestEvent
            if (!leading) {
                leading = true
                s.released = false
                s.startY = event.touchY
                s.fromRight = event.swipeEdge == NavigationEvent.EDGE_RIGHT
                s.active = true
            }
            s.rawDy = event.touchY - s.startY
        } else if (exiting && leading && !s.released) {
            s.released = true
            s.releasedAt = fraction.value.coerceIn(0.001f, 0.999f)
        } else if (!exiting && leading) {
            leading = false
            s.reset()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (leading) BackPreviewState.reset()
        }
    }

    // The layout is the same whether or not a back gesture runs, so the screen keeps its state;
    // everything below is read while drawing only, so following the finger does not recompose it.
    Box(
        modifier =
            Modifier.fillMaxSize()
                // Behind everything (drawn before the screen is moved and shrunk, so it fills the
                // whole display): the backdrop the two cards sit on, dimmed like the screen behind.
                // As in the system, the dim covers everything under the card you are leaving, so
                // that card always stands out instead of blending into the space around the cards.
                .drawBehind {
                    val s = BackPreviewState
                    if (entering && s.active) {
                        drawRect(backdrop)
                        val alpha = scrim * dimFade(1f - fraction.value)
                        if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
                    }
                }
                .graphicsLayer {
                    val s = BackPreviewState
                    if (s.active && (exiting || entering)) {
                        val raw = if (entering) 1f - fraction.value else fraction.value
                        place(placement(raw, entering, size.width, size.height, margin, offset))
                        shape = RoundedCornerShape(corner)
                        clip = true
                    } else {
                        clip = false
                    }
                }
                .drawWithContent {
                    drawContent()
                    // The screen behind is dimmed, and the dim fades away after you let go.
                    val s = BackPreviewState
                    if (entering && s.active) {
                        val alpha = scrim * dimFade(1f - fraction.value)
                        if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
                    }
                }
    ) {
        content()
    }
}
