@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.adaptive

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.motion.BackPreviewProgressEasing
import me.ash.reader.ui.motion.EmphasizedAccelerate
import me.ash.reader.ui.motion.backPreview
import me.ash.reader.ui.motion.backPreviewCorner
import me.ash.reader.ui.motion.backPreviewScrim
import me.ash.reader.ui.motion.drawBackPreviewShadow

/*
 * The back preview (see BackPreview.kt) for closing an article on a phone, where the list and the
 * article share one adaptive layout that shows one at a time.
 *
 * While going back, that layout draws the article *under* the list, so the article cannot become a
 * card where it is. Instead the article keeps a picture of itself in a [GraphicsLayer]. When a back
 * gesture starts, the picture stops updating, the layout moves to the list underneath, and the
 * picture is drawn on top as the card. Let go and the card leaves to the right and the layout
 * finishes going back; cancel and the card settles, the layout returns to the article and the
 * picture goes away.
 */

/** The state of one back preview on an article. */
@Stable
class ArticleBackPreviewState {
    /** The card is on screen, from the first touch until the article or the list is back. */
    var active by mutableStateOf(false)
        internal set

    /** The eased gesture progress. */
    var pull by mutableFloatStateOf(0f)
        internal set

    var leave by mutableFloatStateOf(0f)
        internal set

    var direction by mutableFloatStateOf(0f)
        internal set

    var rawDy by mutableFloatStateOf(0f)
        internal set

    internal var startY = Float.NaN

    internal fun reset() {
        active = false
        pull = 0f
        leave = 0f
        direction = 0f
        rawDy = 0f
        startY = Float.NaN
    }
}

/**
 * Draws the article and keeps a picture of it in [layer]; while a preview is showing, the picture
 * is left as it was (it is what the card shows).
 */
fun Modifier.recordForBackPreview(
    layer: GraphicsLayer,
    state: ArticleBackPreviewState,
    enabled: Boolean,
): Modifier =
    if (!enabled) this
    else
        drawWithContent {
            if (!state.active) {
                layer.record { this@drawWithContent.drawContent() }
            }
            drawLayer(layer)
        }

/**
 * Handles the back gesture on an open article and shows the back preview. Phones only ([enabled]
 * is false with two panes). Call it after the layout, so it gets the gesture before the layout's
 * own back handling.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T> ArticleBackPreviewHandler(
    enabled: Boolean,
    state: ArticleBackPreviewState,
    navigator: ThreePaneScaffoldNavigator<T>,
    backBehavior: BackNavigationBehavior,
) {
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    PredictiveBackHandler(enabled = enabled && navigator.canNavigateBack(backBehavior)) { events ->
        try {
            events.collect { event ->
                if (!state.active) {
                    state.startY = event.touchY
                    state.direction =
                        when (event.swipeEdge) {
                            BackEventCompat.EDGE_LEFT -> 1f
                            BackEventCompat.EDGE_RIGHT -> -1f
                            else -> 0f
                        }
                    // Freeze the picture first, then bring the list in underneath it.
                    state.active = true
                    withFrameNanos {}
                    navigator.seekBack(backBehavior, fraction = 1f)
                }
                state.pull = BackPreviewProgressEasing.transform(event.progress)
                state.rawDy = event.touchY - state.startY
            }
            if (!state.active) {
                // The back key or a button: no gesture, so go back the usual way.
                navigator.navigateBack(backBehavior)
                return@PredictiveBackHandler
            }
            // Let go: the card leaves to the right, the way the article came in.
            animate(
                0f,
                1f,
                animationSpec = tween(300, easing = EmphasizedAccelerate),
            ) { v, _ ->
                state.leave = v
            }
            navigator.navigateBack(backBehavior)
            state.reset()
        } catch (e: CancellationException) {
            if (state.active) {
                scope.launch {
                    coroutineScope {
                        launch { animate(state.pull, 0f, animationSpec = settleSpec) { v, _ -> state.pull = v } }
                        launch { animate(state.rawDy, 0f, animationSpec = settleSpec) { v, _ -> state.rawDy = v } }
                    }
                    // The article is back under the card before the card goes away.
                    navigator.seekBack(backBehavior, fraction = 0f)
                    withFrameNanos {}
                    withFrameNanos {}
                    state.reset()
                }
            }
            throw e
        }
    }
}

/** The card (the picture of the article) over the dimmed list. */
@Composable
fun ArticleBackPreviewCard(layer: GraphicsLayer, state: ArticleBackPreviewState) {
    if (!state.active) return
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        modifier =
            Modifier.fillMaxSize().drawBehind {
                // The list behind: dimmed, brightening as you pull and as the card leaves.
                val alpha = backPreviewScrim(dark) * (1f - 0.4f * state.pull) * (1f - state.leave)
                if (alpha > 0.001f) drawRect(Color.Black, alpha = alpha)
            }
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        backPreview(state.pull, state.leave, state.direction, state.rawDy)
                    }
                    .drawBehind {
                        drawBackPreviewShadow(state.pull, backPreviewCorner(state.pull), dark)
                    }
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            shape = RoundedCornerShape(backPreviewCorner(state.pull))
                            clip = true
                        }
                        .drawBehind { drawLayer(layer) }
            )
        }
    }
}
