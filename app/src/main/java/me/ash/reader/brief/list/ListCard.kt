package me.ash.reader.brief.list

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.ash.reader.brief.ui.Group
import me.ash.reader.domain.model.article.ArticleFlowItem

/**
 * Where an article sits in its day's card on the article list: the first of the day gets the
 * card's big top corners, the last its big bottom corners, the rest the small inner ones, so each
 * day reads as one joined card like the app's other lists. Null: not in a list (an article shown
 * on its own keeps Read You's look).
 */
data class ListCardPosition(val first: Boolean, val last: Boolean)

val LocalListCard = compositionLocalOf<ListCardPosition?> { null }

/**
 * Whether the list shows one feed only: its rows then leave out the feed's name and icon, which
 * would be the same on every row (the page's title already names the feed).
 */
val LocalSingleFeedList = compositionLocalOf { false }

/** The card position of the article at [index] of [items], from its neighbours. */
fun listCardPosition(items: LazyPagingItems<ArticleFlowItem>, index: Int): ListCardPosition {
    val before = if (index > 0) items.peek(index - 1) else null
    val after = if (index + 1 < items.itemCount) items.peek(index + 1) else null
    return ListCardPosition(first = before !is ArticleFlowItem.Article, last = after !is ArticleFlowItem.Article)
}

/** The row's card shape for [position]. */
fun listCardShape(position: ListCardPosition): RoundedCornerShape {
    val top = if (position.first) Group.Outer else Group.Inner
    val bottom = if (position.last) Group.Outer else Group.Inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Sets an article row into its day's card: the page's margins, the gap and the card's corners. */
@Composable
fun Modifier.listCard(): Modifier {
    val position = LocalListCard.current ?: return this
    return this.padding(horizontal = 16.dp, vertical = Group.Gap / 2).clip(listCardShape(position))
}
