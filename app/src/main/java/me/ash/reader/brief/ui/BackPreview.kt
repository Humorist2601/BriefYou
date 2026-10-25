package me.ash.reader.brief.ui

import android.graphics.Path
import android.os.Build
import android.view.RoundedCorner
import android.view.animation.PathInterpolator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
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
import kotlinx.coroutines.flow.collectLatest

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
 * When you let go (450 ms, on the emphasized curve): the screen behind grows back to full screen,
 * the dim fades away, and the screen you leave goes back to full size while sliding a further
 * 96 dp to the right, fading out within the first fifth of the animation.
 *
 * How it works: which screen is which comes from the back stack when the gesture starts (the top
 * one is leaving, the one under it is behind), never from the navigation host's own transition,
 * whose states and stacking order during a gesture do not say which screen is which. The progress
 * comes from the gesture while you swipe, and from this file's own animation after you let go. The
 * host's transition draws nothing; it only keeps both screens around.
 *
 * While you swipe, while a cancelled gesture settles, and after you let go until the screen you
 * leave has faded out, the screen you are leaving draws everything: the backdrop, a picture of the
 * screen behind (which then draws nothing itself), and itself on top. So it does not matter which
 * of the two the host stacks on top. (When the screen behind drew its own backdrop right after you
 * let go, it covered the fading screen you leave whenever the host stacked it on top: a brief dark
 * flash.) Once the screen you leave has faded out, the screen behind draws itself.
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
 * How long the navigation host keeps both screens during a predictive back. The host's own
 * transition draws nothing (see [backPreviewPopTransform]); it only keeps the screens around while
 * this file animates them with its own clock.
 */
const val BACK_PREVIEW_DURATION = 1000

/** After you let go and it goes back: 450 ms, as in the system. */
private const val COMMIT_DURATION = 450

/** After you let go and it does not go back. */
private const val CANCEL_DURATION = 300

/** The part of the commit animation in which the screen you leave fades out. */
private const val LEAVING_FADE = 0.2f

internal enum class BackPhase {
    Idle,
    /** Your finger is down. */
    Dragging,
    /** You let go and it went back. */
    Committing,
    /** You let go and it did not go back. */
    Cancelling,
}

/** One back gesture. Only one runs at a time. Written by [BackPreviewController]. */
internal object BackPreviewState {
    var phase by mutableStateOf(BackPhase.Idle)
    /** The screen you are leaving and the screen behind it (their navigation keys). */
    var leavingKey by mutableStateOf<Any?>(null)
    var behindKey by mutableStateOf<Any?>(null)
    /** The raw gesture progress, 0 to 1. */
    var progress by mutableFloatStateOf(0f)
    /** The raw gesture progress when you let go. */
    var releasedAt by mutableFloatStateOf(0f)
    /** How far the commit animation is, 0 to 1 (linear). */
    var settle by mutableFloatStateOf(0f)
    /** True when the gesture came from the right edge. */
    var fromRight by mutableStateOf(false)
    var startY = 0f
    var rawDy by mutableFloatStateOf(0f)

    /** The screen behind has drawn its picture in this gesture (until then there is nothing to show). */
    var pictureReady = false

    /**
     * Screens the host still shows after a gesture although they should be gone (the one you left
     * after going back, the one behind after a cancel). They are not drawn and can't be touched
     * until the host lets them go.
     */
    val collapsedKeys = mutableStateListOf<Any>()

    /** The screens the host is showing right now, and whether its transition is moving them. */
    val composed = HashMap<Any, Boolean>()
}

/**
 * The navigation host's transition for a predictive back: it draws nothing and only keeps both
 * screens on screen. Everything you see is drawn by [BackPreviewPage].
 */
fun backPreviewPopTransform(): ContentTransform =
    fadeIn(animationSpec = tween(BACK_PREVIEW_DURATION), initialAlpha = 1f) togetherWith
        fadeOut(animationSpec = tween(BACK_PREVIEW_DURATION), targetAlpha = 1f)

/**
 * True while a back gesture runs or settles. The navigation host's transitions use it so that,
 * whatever transition the host picks for the gesture, it is [backPreviewPopTransform].
 */
@Composable
fun rememberBackPreviewRunning(): () -> Boolean {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    return remember(dispatcher) {
        {
            BackPreviewState.phase != BackPhase.Idle ||
                dispatcher?.transitionState?.value is NavigationEventTransitionState.InProgress
        }
    }
}

/**
 * Follows the back gesture for the navigation host showing [backStack]. Put it next to the host,
 * once.
 */
@Composable
fun BackPreviewController(backStack: List<Any>) {
    val dispatcher =
        LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    LaunchedEffect(dispatcher, backStack) {
        val s = BackPreviewState
        dispatcher.transitionState.collectLatest { gesture ->
            if (gesture is NavigationEventTransitionState.InProgress) {
                val event = gesture.latestEvent
                if (s.phase != BackPhase.Dragging) {
                    if (backStack.size < 2) return@collectLatest
                    val leaving = backStack.last()
                    val behind = backStack[backStack.size - 2]
                    // Only when the host follows this gesture: it then shows both screens and moves
                    // them. Other gestures (Triage, the article pane, an open search) don't.
                    if (s.composed[leaving] != true || !s.composed.containsKey(behind)) {
                        return@collectLatest
                    }
                    s.collapsedKeys.remove(leaving)
                    s.collapsedKeys.remove(behind)
                    s.leavingKey = leaving
                    s.behindKey = behind
                    s.fromRight = event.swipeEdge == NavigationEvent.EDGE_RIGHT
                    s.startY = event.touchY
                    s.settle = 0f
                    s.progress = event.progress
                    s.rawDy = 0f
                    s.pictureReady = false
                    s.phase = BackPhase.Dragging
                }
                s.progress = event.progress
                s.rawDy = event.touchY - s.startY
            } else if (s.phase == BackPhase.Dragging) {
                s.releasedAt = s.progress
                // Going back takes the screen off the back stack; give it a few frames.
                var committed = false
                for (i in 0 until 6) {
                    withFrameNanos {}
                    if (backStack.lastOrNull() != s.leavingKey) {
                        committed = true
                        break
                    }
                }
                val leftOver = if (committed) s.leavingKey else s.behindKey
                try {
                    if (committed) {
                        s.phase = BackPhase.Committing
                        animate(
                            0f,
                            1f,
                            animationSpec = tween(COMMIT_DURATION, easing = LinearEasing),
                        ) { value, _ ->
                            s.settle = value
                        }
                    } else {
                        s.phase = BackPhase.Cancelling
                        animate(
                            s.progress,
                            0f,
                            animationSpec = tween(CANCEL_DURATION, easing = FastOutSlowInEasing),
                        ) { value, _ ->
                            s.progress = value
                        }
                    }
                } finally {
                    if (leftOver != null && s.composed.containsKey(leftOver)) {
                        s.collapsedKeys.add(leftOver)
                    }
                    s.phase = BackPhase.Idle
                    s.leavingKey = null
                    s.behindKey = null
                    s.progress = 0f
                    s.settle = 0f
                }
            }
        }
    }
}

/** Where a screen is: its scale (about its centre), offsets, and opacity. */
private class Placement(val scale: Float, val x: Float, val y: Float, val alpha: Float = 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The screens' positions before you let go, for a back gesture at [raw] progress. [width] and
 * [height] are the screen size, [margin] and [offset] are 8 dp and 96 dp in px.
 */
private fun preCommit(
    raw: Float,
    behind: Boolean,
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
    return if (behind) {
        Placement(scale, -offset, y)
    } else {
        // From the left edge the card slides right, to 8 dp from the edge; from the right it stays.
        val shift = if (BackPreviewState.fromRight) 0f else (width * (1f - MAX_SCALE) / 2f - margin)
        Placement(scale, shift * p, y)
    }
}

/** Where a screen is right now. [behind] is the screen you go back to. */
private fun placement(
    behind: Boolean,
    width: Float,
    height: Float,
    margin: Float,
    offset: Float,
): Placement {
    val s = BackPreviewState
    return when (s.phase) {
        BackPhase.Idle -> Placement(1f, 0f, 0f)
        BackPhase.Dragging -> preCommit(s.progress, behind, width, height, margin, offset, s.rawDy)
        BackPhase.Cancelling -> {
            // Everything runs back to where it started, the finger offset with it.
            val follow = if (s.releasedAt > 0f) (s.progress / s.releasedAt).coerceIn(0f, 1f) else 0f
            preCommit(s.progress, behind, width, height, margin, offset, s.rawDy * follow)
        }
        BackPhase.Committing -> {
            // From where things were to the end, on the emphasized curve.
            val linear = s.settle
            val t = EmphasizedEasing.transform(linear)
            val from = preCommit(s.releasedAt, behind, width, height, margin, offset, s.rawDy)
            if (behind) {
                Placement(lerp(from.scale, 1f, t), lerp(from.x, 0f, t), lerp(from.y, 0f, t))
            } else {
                // Back to full size, its left edge moving a further 96 dp right, fading out quickly.
                val left = width * (1f - from.scale) / 2f + from.x
                Placement(
                    scale = lerp(from.scale, 1f, t),
                    x = lerp(from.x, left + offset, t),
                    y = lerp(from.y, 0f, t),
                    alpha = max(1f - linear / LEAVING_FADE, 0f),
                )
            }
        }
    }
}

/** How much of the dim is left: all of it until you let go, then it fades away with the rest. */
private fun dimFade(): Float {
    val s = BackPreviewState
    return if (s.phase == BackPhase.Committing) 1f - EmphasizedEasing.transform(s.settle) else 1f
}

/**
 * The screens of the navigation host (its back stack), provided by the app's navigation
 * (AppEntry).
 */
val LocalBackStack = compositionLocalOf<List<Any>> { emptyList() }

/**
 * Where the screen behind keeps a picture of itself during a back gesture, for the screen you are
 * leaving to draw. Provided by the app's navigation (AppEntry).
 */
val LocalBackPreviewPicture = staticCompositionLocalOf<GraphicsLayer?> { null }

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

private enum class Role {
    None,
    Leaving,
    Behind,
}

/**
 * Wraps the screen with navigation key [key] so the predictive back gesture plays stock Android's
 * back animation on it.
 */
@Composable
fun BackPreviewPage(key: Any, content: @Composable () -> Unit) {
    val s = BackPreviewState
    val transition = LocalNavAnimatedContentScope.current.transition
    val moving = transition.currentState != transition.targetState
    SideEffect { s.composed[key] = moving }
    DisposableEffect(key) {
        onDispose {
            s.composed.remove(key)
            s.collapsedKeys.remove(key)
        }
    }

    val phase = s.phase
    val role =
        when {
            phase == BackPhase.Idle -> Role.None
            key == s.leavingKey -> Role.Leaving
            key == s.behindKey -> Role.Behind
            else -> Role.None
        }
    val fadedOut by remember {
        derivedStateOf { s.phase == BackPhase.Committing && s.settle >= LEAVING_FADE }
    }
    val isTop = LocalBackStack.current.lastOrNull() == key
    // Not drawn and can't be touched (but keeps its state).
    val collapsed =
        when (role) {
            Role.Leaving -> fadedOut
            Role.Behind -> phase == BackPhase.Cancelling
            Role.None -> !isTop && key in s.collapsedKeys
        }
    // The screen behind, while you swipe and until the screen you leave has faded out: it keeps a
    // picture of itself for the leaving screen to draw, and draws nothing itself.
    val recording =
        role == Role.Behind &&
            (phase == BackPhase.Dragging || (phase == BackPhase.Committing && !fadedOut))
    // The screen you leave, while you swipe, while a cancel settles, and while it fades out after
    // going back: draws the backdrop and the screen behind too.
    val drawsBehind =
        role == Role.Leaving &&
            (phase == BackPhase.Dragging ||
                phase == BackPhase.Cancelling ||
                (phase == BackPhase.Committing && !fadedOut))
    // The screen behind, once the screen you leave has faded out: draws itself, growing back to
    // full screen.
    val growing = role == Role.Behind && phase == BackPhase.Committing && fadedOut

    val picture = LocalBackPreviewPicture.current
    val density = LocalDensity.current
    val margin = with(density) { MARGIN.toPx() }
    val offset = with(density) { ENTERING_START_OFFSET.toPx() }
    val corner = displayCornerRadius()
    val backdrop = MaterialTheme.colorScheme.surface
    val scrim = if (backdrop.luminance() < 0.5f) MAX_SCRIM_DARK else MAX_SCRIM_LIGHT

    // The layout is the same whether or not a back gesture runs, so the screen keeps its state;
    // the positions are read while drawing only, so following the finger does not recompose it.
    Box(
        modifier =
            Modifier.fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (collapsed) {
                        layout(0, 0) {}
                    } else {
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }
                .drawWithContent {
                    if (recording && picture != null) {
                        picture.record { this@drawWithContent.drawContent() }
                        s.pictureReady = true
                    } else if (!recording) {
                        drawContent()
                    }
                }
                // Behind everything (drawn before the screen is moved and shrunk, so it fills the
                // whole display): the backdrop the two cards sit on, dimmed like the screen behind.
                .drawBehind {
                    // Until the screen behind has drawn its picture there is nothing to show behind
                    // (and the screen you leave still covers everything).
                    if (drawsBehind && s.pictureReady) {
                        val dim = scrim * dimFade()
                        drawRect(backdrop)
                        if (dim > 0.001f) drawRect(Color.Black, alpha = dim)
                        val p = placement(true, size.width, size.height, margin, offset)
                        withTransform({
                            translate(p.x, p.y)
                            scale(p.scale, p.scale, center)
                        }) {
                            val card =
                                androidx.compose.ui.graphics.Path().apply {
                                    addRoundRect(RoundRect(size.toRect(), CornerRadius(corner)))
                                }
                            clipPath(card) {
                                if (picture != null) drawLayer(picture)
                                if (dim > 0.001f) drawRect(Color.Black, alpha = dim)
                            }
                        }
                    } else if (growing) {
                        drawRect(backdrop)
                        val alpha = scrim * dimFade()
                        if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
                    }
                }
                // The card's fade (only the screen you leave fades, after it went back).
                .graphicsLayer {
                    alpha =
                        if (role == Role.Leaving || growing) {
                            placement(growing, size.width, size.height, margin, offset).alpha
                        } else {
                            1f
                        }
                }
                // The card: shrunk, moved and rounded while drawing only (the layout doesn't move).
                .drawWithContent {
                    if (role == Role.Leaving || growing) {
                        val p = placement(growing, size.width, size.height, margin, offset)
                        withTransform({
                            translate(p.x, p.y)
                            scale(p.scale, p.scale, center)
                        }) {
                            val card =
                                androidx.compose.ui.graphics.Path().apply {
                                    addRoundRect(RoundRect(size.toRect(), CornerRadius(corner)))
                                }
                            clipPath(card) { this@drawWithContent.drawContent() }
                        }
                    } else {
                        drawContent()
                    }
                }
                .drawWithContent {
                    drawContent()
                    // The screen behind is dimmed, and the dim fades away after it went back.
                    if (growing) {
                        val alpha = scrim * dimFade()
                        if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
                    }
                }
    ) {
        content()
    }
}
