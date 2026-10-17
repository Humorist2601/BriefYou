package me.ash.reader.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent

/*
 * "Depth slide": how screens come and go in the app (Triage keeps its own pull and toss).
 *
 * Screens are cards stacked on top of each other. A new screen slides in from halfway across and
 * grows from a slightly smaller rounded card into the full screen, its corners squaring off as it
 * lands. The screen it covers drifts a quarter of the way the other way, shrinks a little, rounds its
 * corners and dims, so it reads as sitting deeper. Going back plays this in reverse.
 *
 * The predictive back gesture drives the same thing with your finger: the screen slides towards the
 * side you swipe from and shrinks into a rounded card while the one underneath comes forward and
 * brightens. The corners morph early, so the card shape shows right at the start of the gesture.
 *
 * The motion follows Material 3 Expressive's emphasized easing curves.
 */

/** Material 3's emphasized easings: quick to respond, long and gentle to settle. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Starts late: used so a screen only fades once it is well on its way out. */
private val LateEasing = Easing { ((it - 0.45f) / 0.55f).coerceIn(0f, 1f) }

const val DEPTH_DURATION = 450

/** How far a card is pushed back: its corners, and how dark the screen underneath gets. */
private val DEPTH_CORNER = 32.dp
private const val DEPTH_DIM_DARK = 0.45f
private const val DEPTH_DIM_LIGHT = 0.28f

/**
 * Whether the transition running now goes back (the screen entering is the one underneath) or
 * forwards (the screen leaving is the one underneath). Set by the transition specs below and read
 * by [DepthPage] while drawing, so it is a plain field rather than Compose state.
 */
internal object DepthDirection {
    @Volatile var back: Boolean = false
}

/* ---- The four halves, also used by the article list and reader panes. ---- */

/** A new screen coming in on top: from halfway across, growing out of a smaller card. */
fun depthEnterForward(): EnterTransition =
    slideInHorizontally(tween(DEPTH_DURATION, easing = EmphasizedDecelerate)) { (it * 0.5f).toInt() } +
        scaleIn(tween(DEPTH_DURATION, easing = EmphasizedDecelerate), initialScale = 0.92f) +
        fadeIn(tween(DEPTH_DURATION / 2, easing = LinearEasing))

/** The screen being covered: drifts back a quarter of the way and shrinks a little. */
fun depthExitForward(): ExitTransition =
    slideOutHorizontally(tween(DEPTH_DURATION, easing = Emphasized)) { -(it * 0.25f).toInt() } +
        scaleOut(tween(DEPTH_DURATION, easing = Emphasized), targetScale = 0.95f)

/** Going back, the screen underneath comes forward again. */
fun depthEnterBack(): EnterTransition =
    slideInHorizontally(tween(DEPTH_DURATION, easing = EmphasizedDecelerate)) { -(it * 0.25f).toInt() } +
        scaleIn(tween(DEPTH_DURATION, easing = EmphasizedDecelerate), initialScale = 0.95f)

/** Going back, the top screen slides away, shrinking into a card, and fades as it goes. */
fun depthExitBack(): ExitTransition =
    slideOutHorizontally(tween(DEPTH_DURATION, easing = EmphasizedAccelerate)) { (it * 0.5f).toInt() } +
        scaleOut(tween(DEPTH_DURATION, easing = EmphasizedAccelerate), targetScale = 0.92f) +
        fadeOut(tween(DEPTH_DURATION, easing = LateEasing))

/* ---- The transitions of the navigation host. ---- */

fun depthForward(): ContentTransform {
    DepthDirection.back = false
    return depthEnterForward() togetherWith depthExitForward()
}

fun depthBack(): ContentTransform {
    DepthDirection.back = true
    return depthEnterBack() togetherWith depthExitBack()
}

/**
 * The predictive back gesture. The progress follows your finger directly, so these curves respond
 * from the first pixel instead of the slow start of the accelerating ones above. The screen moves
 * towards the side you swipe from ([swipeEdge]).
 */
fun depthPredictiveBack(swipeEdge: Int): ContentTransform {
    DepthDirection.back = true
    val dir = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1f else 1f
    val follow = tween<Float>(DEPTH_DURATION, easing = FastOutSlowInEasing)
    val followOffset = tween<IntOffset>(DEPTH_DURATION, easing = FastOutSlowInEasing)
    return (slideInHorizontally(followOffset) { -(dir * it * 0.25f).toInt() } +
        scaleIn(follow, initialScale = 0.95f)) togetherWith
        (slideOutHorizontally(followOffset) { (dir * it * 0.5f).toInt() } +
            scaleOut(follow, targetScale = 0.88f) +
            fadeOut(tween(DEPTH_DURATION, easing = LateEasing)))
}

/**
 * Wraps a screen of the navigation host: while it moves, its corners round off (and square again
 * as it lands), and when it is the screen underneath, it dims. Both follow the same timeline as the
 * slide, so they also follow the predictive back gesture.
 */
@Composable
fun DepthPage(content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    // The corners morph in the first part of the motion, so the card shape is there early.
    val corner =
        transition.animateFloat(
            transitionSpec = { tween(DEPTH_DURATION / 2, easing = FastOutSlowInEasing) },
            label = "depthCorner",
        ) {
            if (it == EnterExitState.Visible) 0f else 1f
        }
    val depth =
        transition.animateFloat(
            transitionSpec = { tween(DEPTH_DURATION, easing = LinearEasing) },
            label = "depthDim",
        ) {
            if (it == EnterExitState.Visible) 0f else 1f
        }
    val dim =
        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DEPTH_DIM_DARK else DEPTH_DIM_LIGHT

    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val c = corner.value * DEPTH_CORNER.toPx()
                    if (c > 0.5f) {
                        shape = RoundedCornerShape(c)
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    } else {
                        clip = false
                    }
                }
                .drawWithContent {
                    drawContent()
                    val d = depth.value
                    if (d > 0.001f) {
                        // Underneath: leaving while going forwards, or entering while going back.
                        val leaving = transition.targetState == EnterExitState.PostExit
                        val underneath = if (DepthDirection.back) !leaving else leaving
                        if (underneath) drawRect(Color.Black, alpha = dim * d)
                    }
                }
    ) {
        content()
    }
}
