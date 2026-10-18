package me.ash.reader.ui.page.adaptive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.android.awaitFrame

/*
 * Brief You: on a phone, an article opens out of its card in the list. The card splits open in
 * place: its top edge moves up and its bottom edge moves down until it fills the screen, showing
 * the article inside as it opens (no zoom), while the list behind stays still and dims a little.
 * Going back (back button, top bar arrow, or the predictive back gesture, which follows your
 * finger) is the same in reverse, closing into the card.
 *
 * The list-detail scaffold moves the list off screen as soon as the article opens, so the list
 * behind is a still picture of it taken when the card is tapped; when the article has closed back
 * into its card, the real list takes its place.
 */

/** Where the article cards are on screen (window coordinates), so an article opens out of its card. */
object ArticleCardBounds {
    private val bounds = HashMap<String, Rect>()

    fun update(articleId: String, rect: Rect) {
        if (bounds.size > 400) bounds.clear()
        bounds[articleId] = rect
    }

    fun of(articleId: String?): Rect? = articleId?.let { bounds[it] }
}

/** Remembers where an article card is on screen. */
fun Modifier.trackArticleCard(articleId: String): Modifier =
    onGloballyPositioned { ArticleCardBounds.update(articleId, it.boundsInWindow()) }

/** The open and close motion of one article page. */
@Stable
class ArticleReveal {
    /** 0 = closed into the card, 1 = open (full screen). */
    val progress = Animatable(1f)

    /** The still picture of the list shown behind while opening or closing (null: none). */
    var listPicture by mutableStateOf<ImageBitmap?>(null)
        private set

    /** The card the article opens from and closes into (window coordinates). */
    var card by mutableStateOf<Rect?>(null)

    /** Whether going back can close into the card (there is a picture of the list). */
    val canClose: Boolean
        get() = listPicture != null

    /** Opens out of [cardRect], over a still picture of the list taken from [listLayer]. */
    suspend fun prepareOpen(listLayer: GraphicsLayer, cardRect: Rect?) {
        listPicture = runCatching { listLayer.toImageBitmap() }.getOrNull()
        card = cardRect
        progress.snapTo(if (listPicture != null) 0f else 1f)
    }

    suspend fun open() {
        if (listPicture == null) return
        progress.animateTo(1f, tween(OPEN_MS, easing = EmphasizedDecelerate))
    }

    /** Closes into the card; then [navigateBack] puts the real list back. */
    suspend fun close(navigateBack: suspend () -> Unit) {
        progress.animateTo(0f, tween(CLOSE_MS, easing = EmphasizedAccelerate))
        navigateBack()
        // Let the real list draw before the picture goes, so nothing flashes in between.
        awaitFrame()
        awaitFrame()
        listPicture = null
        progress.snapTo(1f)
    }

    /** Back to fully open (a back gesture that was cancelled). */
    suspend fun settleOpen() {
        progress.animateTo(1f, tween(200, easing = EmphasizedDecelerate))
    }

    /** Forget the picture (the list is showing again some other way). */
    fun clear() {
        listPicture = null
    }
}

/** Records the list as it is drawn, so a still picture of it can be taken when a card is tapped. */
fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** Behind the article page while it opens or closes: the still list, dimming as the page opens. */
@Composable
fun ArticleRevealBackdrop(reveal: ArticleReveal) {
    val picture = reveal.listPicture ?: return
    val scrim = MaterialTheme.colorScheme.scrim
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = reveal.progress.value }
                    .background(scrim.copy(alpha = 0.32f))
        )
    }
}

/**
 * The article page, shown through its card's outline as it opens or closes: the outline's top
 * edge moves up and its bottom edge down (its sides out to the screen's), corners squaring off.
 */
fun Modifier.articleReveal(reveal: ArticleReveal): Modifier {
    var paneOffset = Offset.Zero
    return onGloballyPositioned { paneOffset = it.positionInWindow() }
        .graphicsLayer {
            val p = reveal.progress.value
            val card = reveal.card
            if (p >= 1f || reveal.listPicture == null) {
                clip = false
                return@graphicsLayer
            }
            val from =
                card?.translate(-paneOffset)
                    ?: Rect(0f, size.height / 2f, size.width, size.height / 2f)
            val rect =
                Rect(
                    left = lerp(from.left, 0f, p),
                    top = lerp(from.top, 0f, p),
                    right = lerp(from.right, size.width, p),
                    bottom = lerp(from.bottom, size.height, p),
                )
            shape = RevealShape(rect, lerp(CardCorner.toPx(), 0f, p))
            clip = true
        }
}

private class RevealShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

private const val OPEN_MS = 450
private const val CLOSE_MS = 350
private val CardCorner = 16.dp
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
