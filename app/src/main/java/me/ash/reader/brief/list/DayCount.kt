package me.ash.reader.brief.list

import androidx.compose.runtime.getValue
import androidx.paging.compose.LazyPagingItems
import me.ash.reader.domain.model.article.ArticleFlowItem

/**
 * How many articles the day starting at [index] (a date header) has, for its label: the articles
 * up to the next header. A day longer than what has loaded so far shows what's loaded and a "+"
 * ([DayCount.more]); the number fills in as the list loads further.
 */
data class DayCount(val count: Int, val more: Boolean)

fun dayCount(items: LazyPagingItems<ArticleFlowItem>, index: Int): DayCount {
    var count = 0
    var i = index + 1
    while (i < items.itemCount) {
        if (items.peek(i) is ArticleFlowItem.Date) return DayCount(count, more = false)
        count++
        i++
    }
    return DayCount(count, more = !items.loadState.append.endOfPaginationReached)
}

/**
 * Whether articles are passing under a pinned day header (the one with [headerKey]): the list's
 * first visible row is past the header's own place. False while the header scrolls with the list.
 */
@androidx.compose.runtime.Composable
fun rememberScrolledUnder(listState: androidx.compose.foundation.lazy.LazyListState?, headerKey: Any): Boolean {
    if (listState == null) return false
    val under by
        androidx.compose.runtime.remember(listState, headerKey) {
            androidx.compose.runtime.derivedStateOf {
                // A header pinned far above its own place (a long day) isn't among the visible
                // rows any more, though it's still on screen: then it's certainly scrolled under.
                val h = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == headerKey }?.index
                h == null ||
                    listState.firstVisibleItemIndex > h ||
                    (listState.firstVisibleItemIndex == h && listState.firstVisibleItemScrollOffset > 0)
            }
        }
    return under
}
