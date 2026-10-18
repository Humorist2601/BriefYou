package me.ash.reader.ui.page.adaptive

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/*
 * An article opens out of its card in the list: the article shows through the card's outline,
 * which grows up and down (and out to the sides) until it fills the screen. Going back is the
 * same in reverse, and the predictive back gesture follows your finger, because both are driven
 * by the list-detail scaffold's own transition (which the back gesture scrubs).
 */

/** Where the article cards are on screen, so an article can open out of its card. */
object ArticleCardBounds {
    private val bounds = HashMap<String, Rect>()

    /** Called as cards are laid out (window coordinates). */
    fun update(articleId: String, rect: Rect) {
        if (bounds.size > 400) bounds.clear()
        bounds[articleId] = rect
    }

    /** The card of [articleId], if it has been on screen. */
    fun of(articleId: String?): Rect? = articleId?.let { bounds[it] }
}

/** Remembers where an article card is, for [articleReveal]. */
fun Modifier.trackArticleCard(articleId: String): Modifier =
    onGloballyPositioned { ArticleCardBounds.update(articleId, it.boundsInWindow()) }

/**
 * Shows the article through its card's outline as it opens (and closes): [articleId]'s card, or
 * a line across the middle of the screen when the card isn't known (opened from a notification).
 */
@Composable
fun AnimatedVisibilityScope.articleReveal(enabled: Boolean, articleId: String?): Modifier {
    val progress by
        transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) tween(OPEN_MS, easing = EmphasizedDecelerate)
                else tween(CLOSE_MS, easing = EmphasizedAccelerate)
            },
            label = "articleReveal",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }
    if (!enabled) return Modifier
    var paneOffset by remember { mutableStateOf(Offset.Zero) }
    val origin = ArticleCardBounds.of(articleId)
    return Modifier.onGloballyPositioned { paneOffset = it.positionInWindow() }
        .graphicsLayer {
            val r = progress
            if (r >= 1f) {
                clip = false
                alpha = 1f
                return@graphicsLayer
            }
            val from =
                origin?.translate(-paneOffset)
                    ?: Rect(0f, size.height / 2f, size.width, size.height / 2f)
            val rect =
                Rect(
                    left = lerp(from.left, 0f, r),
                    top = lerp(from.top, 0f, r),
                    right = lerp(from.right, size.width, r),
                    bottom = lerp(from.bottom, size.height, r),
                )
            shape = RevealShape(rect, lerp(CardCorner.toPx(), 0f, r))
            clip = true
            alpha = lerp(0.35f, 1f, (r * 1.6f).coerceAtMost(1f))
        }
}

private class RevealShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

private const val OPEN_MS = 450
private const val CLOSE_MS = 350
private val CardCorner = 24.dp
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
