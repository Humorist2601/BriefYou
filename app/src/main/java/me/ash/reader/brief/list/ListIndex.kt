package me.ash.reader.brief.list

import me.ash.reader.domain.model.article.ArticleFlowItem

/**
 * The list's own row number for the article list's item at [pagingIndex]. With the date headings
 * pinned (sticky), a day that starts with a gap above its heading has that gap as a row of its own
 * (ArticleList), so the list's rows run ahead of the article list's items; Read You's jump back to
 * the article after reading used the item number and landed a screen or so above it (each time you
 * came back, further down the list). Without pinned headings the numbers are the same. Brief You.
 */
fun lazyIndexOf(items: List<ArticleFlowItem?>, pagingIndex: Int, stickyHeaders: Boolean): Int {
    if (!stickyHeaders || pagingIndex <= 0) return pagingIndex
    var gaps = 0
    for (i in 0 until minOf(pagingIndex, items.size)) {
        val item = items[i]
        if (item is ArticleFlowItem.Date && item.showSpacer) gaps++
    }
    // The heading's gap row comes before the heading itself, so a heading's own row is one further.
    return pagingIndex + gaps + (if ((items.getOrNull(pagingIndex) as? ArticleFlowItem.Date)?.showSpacer == true) 1 else 0)
}
