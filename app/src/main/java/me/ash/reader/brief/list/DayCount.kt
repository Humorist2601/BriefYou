package me.ash.reader.brief.list

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
