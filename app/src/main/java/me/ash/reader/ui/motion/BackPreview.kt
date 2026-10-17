package me.ash.reader.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * "Back preview": the predictive back gesture of every screen except Triage (which keeps its own
 * pull and toss), following Android's predictive back design guidelines.
 *
 * - While you swipe, the screen shrinks to 90% into a card with rounded corners. It drifts a little
 *   towards your finger (a few dp, towards the edge you swipe from) and follows it up and down,
 *   never further than a small margin. The motion is front-loaded: most of it happens at the start
 *   of the swipe, so the screen responds straight away.
 * - The screen you go back to sits behind it, dimmed, and brightens as you pull.
 * - Let go and the card slides back to the right, the way it came in, and fades: back looks like
 *   undoing the navigation, never like throwing the screen towards your swipe. Cancel and it
 *   settles back.
 */

/** Android's recommended curve for the gesture's progress: quick to show, gentle to finish. */
val BackPreviewProgressEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

/** How long a predictive back takes in the navigation host, from the first touch to the end. */
const val BACK_PREVIEW_DURATION = 500

private val BACK_PREVIEW_CORNER = 28.dp
private val BACK_PREVIEW_MARGIN = 8.dp

/**
 * The card. [pull] is the eased gesture progress (0 to 1), [leave] how far the card has gone after
 * letting go (0 to 1), [direction] is +1 when the gesture started from the left edge, -1 from the
 * right and 0 with no edge, and [rawDy] how far the finger moved up or down since the start, in px.
 */
fun GraphicsLayerScope.backPreview(pull: Float, leave: Float, direction: Float, rawDy: Float) {
    val p = pull.coerceIn(0f, 1f)
    val l = leave.coerceIn(0f, 1f)
    val scale = 1f - 0.1f * p
    scaleX = scale
    scaleY = scale
    // A few dp towards the finger: (width / 20) - 8 dp, as in the guidelines.
    val xShift = (size.width / 20f - BACK_PREVIEW_MARGIN.toPx()).coerceAtLeast(0f)
    // Leaving: back out to the right, the way the screen came in.
    translationX = direction * xShift * p + l * size.width * 0.5f
    // Up and down with the finger, slowing down towards a limit of (height / 20) - 8 dp.
    val yLimit = (size.height / 20f - BACK_PREVIEW_MARGIN.toPx()).coerceAtLeast(0f)
    val yAmount = (abs(rawDy) / (size.height * 0.5f)).coerceIn(0f, 1f)
    translationY = sign(rawDy) * yLimit * BackPreviewProgressEasing.transform(yAmount) * (1f - l)
    alpha = 1f - l
}

/** The card's corners: they round off quickly as the gesture starts. */
fun Density.backPreviewCorner(pull: Float): Float =
    BACK_PREVIEW_CORNER.toPx() * (pull.coerceIn(0f, 1f) * 3f).coerceIn(0f, 1f)

/** How dark the screen behind the card is at the start; it brightens as you go back. */
fun backPreviewScrim(dark: Boolean): Float = if (dark) 0.45f else 0.25f

/**
 * A light shadow so the card lifts off the dimmed screen behind it: thin rounded outlines that fade
 * smoothly from the edge outwards.
 */
fun DrawScope.drawBackPreviewShadow(pull: Float, corner: Float, dark: Boolean) {
    val strength = (pull.coerceIn(0f, 1f) * 3f).coerceIn(0f, 1f)
    if (strength <= 0.01f) return
    val alpha = if (dark) 0.35f else 0.18f
    val rings = 40
    val spread = 24.dp.toPx() * strength
    val drop = 6.dp.toPx() * strength
    val step = spread / rings
    for (i in rings downTo 0) {
        val distance = step * i
        val t = i / rings.toFloat()
        val falloff = (1f - t) * (1f - t)
        drawRoundRect(
            color = Color.Black.copy(alpha = alpha * strength * falloff),
            topLeft = Offset(-distance, drop - distance),
            size = Size(size.width + 2 * distance, size.height + 2 * distance),
            cornerRadius = CornerRadius(corner + distance),
            style = Stroke(width = step * 1.6f),
        )
    }
}

/**
 * Whether a predictive back is running in the navigation host right now. Set by the screen being
 * left (it is the one that sees the gesture), read by the screen behind it to dim itself.
 */
internal object BackPreviewState {
    var active by mutableStateOf(false)
}

/**
 * The navigation host's transition for a predictive back: the screen behind grows from 95% to full
 * size; the screen you leave is drawn by [BackPreviewPage] and only kept on screen here.
 */
fun backPreviewPopTransform(): ContentTransform =
    scaleIn(
        animationSpec = tween(BACK_PREVIEW_DURATION, easing = FastOutSlowInEasing),
        initialScale = 0.95f,
    ) togetherWith fadeOut(animationSpec = tween(BACK_PREVIEW_DURATION), targetAlpha = 1f)

/**
 * Wraps a screen of the navigation host so the predictive back gesture shows the back preview on
 * it.
 *
 * The host moves its transition along with the gesture, so the screen reads the progress from its
 * own exit animation. The finger position, the edge and the moment you let go come from the back
 * gesture itself. After you let go, the host plays the rest of the transition: forwards when going
 * back, backwards when cancelled; the card leaves (or settles) over that remaining time.
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

    val state = remember { PreviewState() }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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

    LaunchedEffect(exiting, gesture) {
        val g = gesture
        if (exiting && g is NavigationEventTransitionState.InProgress) {
            val event = g.latestEvent
            if (!state.previewing) {
                state.previewing = true
                state.released = false
                state.startY = event.touchY
                state.direction =
                    when (event.swipeEdge) {
                        NavigationEvent.EDGE_LEFT -> 1f
                        NavigationEvent.EDGE_RIGHT -> -1f
                        else -> 0f
                    }
                BackPreviewState.active = true
            }
            state.rawDy = event.touchY - state.startY
        } else if (exiting && state.previewing && !state.released) {
            // Let go: from here the host finishes (or reverses) the transition by itself.
            state.released = true
            state.releasedAt = fraction.value.coerceIn(0.01f, 0.99f)
        } else if (!exiting && state.previewing) {
            state.reset()
            BackPreviewState.active = false
        }
    }

    // If the screen goes away mid-preview (the back went through), the screen behind stops dimming.
    DisposableEffect(Unit) {
        onDispose { if (state.previewing) BackPreviewState.active = false }
    }

    // The layout is the same whether or not a preview is showing, so the screen keeps its state;
    // everything below is read while drawing only, so following the finger does not recompose it.
    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer {
                    if (state.previewing) {
                        val m = state.motion(fraction.value)
                        backPreview(m.pull, m.leave, state.direction, state.rawDy * m.follow)
                    }
                }
                .drawBehind {
                    if (state.previewing) {
                        val m = state.motion(fraction.value)
                        drawBackPreviewShadow(m.pull, backPreviewCorner(m.pull), dark)
                    }
                }
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (state.previewing) {
                            shape = RoundedCornerShape(backPreviewCorner(state.motion(fraction.value).pull))
                            clip = true
                        } else {
                            clip = false
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        // The screen behind the card: dimmed, brightening as you go back.
                        // (For the screen coming in, the progress runs from 1 down to 0.)
                        if (entering && BackPreviewState.active) {
                            val alpha = backPreviewScrim(dark) * fraction.value
                            if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
                        }
                    }
        ) {
            content()
        }
    }
}

/** Where the card is: eased pull, how far it has left, and how much it still follows the finger. */
private class PreviewMotion(val pull: Float, val leave: Float, val follow: Float)

/** One back preview: where it started, which edge it came from and when you let go. */
private class PreviewState {
    var previewing by mutableStateOf(false)
    var released by mutableStateOf(false)
    var releasedAt by mutableFloatStateOf(1f)
    var direction by mutableFloatStateOf(0f)
    var startY by mutableFloatStateOf(0f)
    var rawDy by mutableFloatStateOf(0f)

    fun motion(fraction: Float): PreviewMotion {
        if (!released) return PreviewMotion(BackPreviewProgressEasing.transform(fraction), 0f, 1f)
        val held = minOf(fraction, releasedAt)
        val pull = BackPreviewProgressEasing.transform(held)
        return if (fraction > releasedAt + 0.001f) {
            val t = ((fraction - releasedAt) / (1f - releasedAt)).coerceIn(0f, 1f)
            PreviewMotion(pull, EmphasizedAccelerate.transform(t), 1f)
        } else {
            // Cancelling: the card settles back to the middle as the progress runs down.
            PreviewMotion(pull, 0f, held / releasedAt)
        }
    }

    fun reset() {
        previewing = false
        released = false
        releasedAt = 1f
        direction = 0f
        rawDy = 0f
    }
}
