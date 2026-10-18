package me.ash.reader.ui.page.adaptive

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/*
 * On a phone, an article grows out of its card in the list: the card (its background and rounded
 * shape) grows up and down into the full page while the list item fades out and the article
 * fades in on top. Going back is the same in reverse, and the predictive back gesture follows
 * your finger, because Compose's shared element motion is driven by the list-detail scaffold's
 * own transition, which the back gesture scrubs.
 */

/** The shared element scope (around the whole app) and the list pane's scope, for the cards. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalArticleSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalArticleListPaneScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private data class ArticleCardKey(val articleId: String)

/** An article card in the list: the start (and end) of its article's open motion. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.articleCard(articleId: String): Modifier {
    val shared = LocalArticleSharedScope.current ?: return this
    val pane = LocalArticleListPaneScope.current ?: return this
    return with(shared) {
        this@articleCard.sharedBounds(
            sharedContentState = rememberSharedContentState(ArticleCardKey(articleId)),
            animatedVisibilityScope = pane,
            enter = fadeIn(tween(FADE_MS)),
            exit = fadeOut(tween(FADE_MS)),
            boundsTransform = ArticleBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(CardCorner)),
        )
    }
}

/**
 * The reading page: grows out of [articleId]'s card. The article keeps its full-page layout and is
 * scaled to cover the growing card (no re-layout of the article on every frame).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.articlePage(
    articleId: String?,
    shared: SharedTransitionScope,
    pane: AnimatedVisibilityScope,
    enabled: Boolean,
): Modifier {
    if (!enabled || articleId == null) return this
    return with(shared) {
        this@articlePage.sharedBounds(
            sharedContentState = rememberSharedContentState(ArticleCardKey(articleId)),
            animatedVisibilityScope = pane,
            enter = fadeIn(tween(FADE_MS, delayMillis = FADE_DELAY_MS)),
            exit = fadeOut(tween(FADE_MS)),
            boundsTransform = ArticleBounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Crop, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(CardCorner)),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private val ArticleBounds = BoundsTransform { _, _ -> tween(OPEN_MS, easing = Emphasized) }

private const val OPEN_MS = 450
private const val FADE_MS = 200
private const val FADE_DELAY_MS = 120

/** How long the list stays shown under an opening article. */
const val ARTICLE_OPEN_MS = OPEN_MS

private val CardCorner = 24.dp
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
