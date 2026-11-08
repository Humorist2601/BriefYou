package me.ash.reader.brief.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The big page title (Feeds' account name, "Settings" and its pages) moving into the top bar as
 * you scroll, like the article list: the big title scrolls up under the bar while the same title
 * fades into the bar in small text, following the scroll. The big title reports where it is; the
 * top bar shows the small one.
 */

/** One screen's title: its text, and how much of the big one is still below the top bar. */
@Stable
class CollapsingTitle {
    var text by mutableStateOf<String?>(null)

    /** 1 while the big title is fully below the top bar, 0 once it has gone under it. */
    var shown by mutableFloatStateOf(1f)

    /** The big title that last reported (Feeds has one on each of its pages). */
    internal var reporter: Any? = null

    /** Where the top bar ends, in window pixels. */
    var barBottom by mutableFloatStateOf(0f)
}

/** Provided by the screen's scaffold; null where nothing shows a small title. */
val LocalCollapsingTitle = staticCompositionLocalOf<CollapsingTitle?> { null }

/** Put on the big title: tells the top bar how far it has scrolled under it. */
@Composable
fun Modifier.reportsCollapsingTitle(text: String): Modifier {
    val title = LocalCollapsingTitle.current ?: return this
    val me = androidx.compose.runtime.remember { Any() }
    DisposableEffect(title, text) {
        title.text = text
        // Scrolled far away (the list let go of it): the small title stays in the bar. Only for
        // the big title on screen, not one on a page sliding away beside it.
        onDispose { if (title.reporter === me) title.shown = 0f }
    }
    return onGloballyPositioned { coordinates ->
        // A page beside the one on screen (a pager) has nothing visible: it doesn't count.
        if (coordinates.boundsInWindow().width <= 0f) return@onGloballyPositioned
        title.reporter = me
        val height = coordinates.size.height.toFloat().coerceAtLeast(1f)
        val bottom = coordinates.positionInWindow().y + height
        title.shown = ((bottom - title.barBottom) / height).coerceIn(0f, 1f)
    }
}

/** Updates [CollapsingTitle.barBottom]: put on the top bar. */
fun Modifier.measuresTitleBar(title: CollapsingTitle): Modifier = onGloballyPositioned {
    title.barBottom = it.positionInWindow().y + it.size.height
}

/** The small title in the top bar, fading and sliding in as the big one goes under the bar. */
@Composable
fun CollapsedTitle() {
    val title = LocalCollapsingTitle.current ?: return
    val text = title.text ?: return
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Like the article list's collapsed title.
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        modifier =
            Modifier.graphicsLayer {
                val hidden = title.shown
                alpha = 1f - hidden
                translationY = hidden * 10.dp.toPx()
            },
    )
}
