package com.ay.briefyou.domain.model.article

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.ay.briefyou.infrastructure.android.AndroidStringsHelper

/**
 * Provide paginated and inserted separator data types for article list view.
 *
 * @see com.ay.briefyou.ui.page.home.flow.ArticleList
 */
sealed class ArticleFlowItem {

    /**
     * The [Article] item.
     *
     * @see com.ay.briefyou.ui.page.home.flow.ArticleItem
     */
    class Article(val articleWithFeed: ArticleWithFeed) : ArticleFlowItem()

    /**
     * The feed publication date separator between [Article] items.
     *
     * @see com.ay.briefyou.ui.page.home.flow.StickyHeader
     */
    class Date(val date: String, val showSpacer: Boolean) : ArticleFlowItem()
}

/**
 * Mapping [ArticleWithFeed] list to [ArticleFlowItem] list.
 */
fun PagingData<ArticleWithFeed>.mapPagingFlowItem(androidStringsHelper: AndroidStringsHelper): PagingData<ArticleFlowItem> =
    map {
        // Brief You: the row's reading time, worked out here in the background (once per article)
        com.ay.briefyou.brief.reading.ListReadingTime.ensure(it.article.id, it.article.rawDescription)
        ArticleFlowItem.Article(it.apply {
            article.dateString = androidStringsHelper.formatAsString(
                date = article.date,
                onlyHourMinute = true
            )
        })
    }.insertSeparators { before, after ->
        val beforeDate =
            androidStringsHelper.formatAsString(before?.articleWithFeed?.article?.date)
        val afterDate =
            androidStringsHelper.formatAsString(after?.articleWithFeed?.article?.date)
        if (beforeDate != afterDate) {
            afterDate?.let { ArticleFlowItem.Date(it, beforeDate != null) }
        } else {
            null
        }
    }
