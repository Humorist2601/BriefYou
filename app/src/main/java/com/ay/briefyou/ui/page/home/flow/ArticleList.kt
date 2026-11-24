package com.ay.briefyou.ui.page.home.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.remember // Brief You
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.ay.briefyou.domain.data.Diff
import com.ay.briefyou.domain.model.article.ArticleFlowItem
import com.ay.briefyou.domain.model.article.ArticleWithFeed

@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.ArticleList(
    pagingItems: LazyPagingItems<ArticleFlowItem>,
    diffMap: Map<String, Diff>,
    isShowFeedIcon: Boolean,
    isShowStickyHeader: Boolean,
    isSwipeEnabled: () -> Boolean = { false },
    isMenuEnabled: Boolean = true,
    onClick: (ArticleWithFeed, Int) -> Unit = { _, _ -> },
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null, // Brief You: for the pinned day's line
    animateRows: Boolean = false, // Brief You: rows fade and glide while the results change (a search)
) {
    // Brief You: while searching, rows that stay glide to their new place, new ones fade in and
    // the ones that no longer match fade out, instead of the list jumping.
    val rowMotion: androidx.compose.foundation.lazy.LazyItemScope.() -> Modifier = {
        if (animateRows)
            Modifier.animateItem(
                fadeInSpec = androidx.compose.animation.core.tween(220, delayMillis = 40),
                placementSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f),
                fadeOutSpec = androidx.compose.animation.core.tween(120),
            )
        else Modifier
    }
    // https://issuetracker.google.com/issues/193785330
    // FIXME: Using sticky header with paging-compose need to iterate through the entire list
    //  to figure out where to add sticky headers, which significantly impacts the performance
    if (!isShowStickyHeader) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey(::key),
            contentType = pagingItems.itemContentType(::contentType),
        ) { index ->
            when (val item = pagingItems[index]) {
                is ArticleFlowItem.Article -> {
                    // Brief You: a reload that brings the same article keeps the row as it is (articles
                    // are compared as objects, so a reload rebuilt every row on screen).
                    val articleWithFeed = remember(item.articleWithFeed) { item.articleWithFeed } // Brief You
                    val article = articleWithFeed.article // Brief You
                    SwipeableArticleItem(
                        modifier = rowMotion(),
                        articleWithFeed = articleWithFeed, // Brief You
                        isUnread = diffMap[article.id]?.isUnread ?: article.isUnread,
                        onClick = { onClick(it, index) },
                        isSwipeEnabled = isSwipeEnabled,
                        isMenuEnabled = isMenuEnabled,
                        onToggleStarred = onToggleStarred,
                        onToggleRead = onToggleRead,
                        onMarkAboveAsRead =
                            if (index == 1) null
                            else onMarkAboveAsRead, // index == 0 -> ArticleFlowItem.Date
                        onMarkBelowAsRead =
                            if (index == pagingItems.itemCount - 1) null else onMarkBelowAsRead,
                        onShare = onShare,
                    )
                }

                is ArticleFlowItem.Date -> {
                    if (item.showSpacer) {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    StickyHeader(item.date, isShowFeedIcon, com.ay.briefyou.brief.list.dayCount(pagingItems, index)) // Brief You: with the day's count
                }

                else -> {}
            }
        }
    } else {
        for (index in 0 until pagingItems.itemCount) {
            when (val item = pagingItems.peek(index)) {
                is ArticleFlowItem.Article -> {
                    item(key = key(item), contentType = contentType(item)) {
                        val articleWithFeed = remember(item.articleWithFeed) { item.articleWithFeed } // Brief You: see above
                        val article = articleWithFeed.article // Brief You
                        SwipeableArticleItem(
                            modifier = rowMotion(),
                            articleWithFeed = articleWithFeed, // Brief You
                            isUnread = diffMap[article.id]?.isUnread ?: article.isUnread,
                            onClick = { onClick(it, index) },
                            isSwipeEnabled = isSwipeEnabled,
                            isMenuEnabled = isMenuEnabled,
                            onToggleStarred = onToggleStarred,
                            onToggleRead = onToggleRead,
                            onMarkAboveAsRead =
                                if (index == 1) null
                                else onMarkAboveAsRead, // index == 0 -> ArticleFlowItem.Date
                            onMarkBelowAsRead =
                                if (index == pagingItems.itemCount - 1) null else onMarkBelowAsRead,
                            onShare = onShare,
                        )
                    }
                }

                is ArticleFlowItem.Date -> {
                    if (item.showSpacer) {
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                    stickyHeader(key = key(item), contentType = contentType(item)) {
                        val under = com.ay.briefyou.brief.list.rememberScrolledUnder(listState, key(item)) // Brief You: articles passing under it
                        StickyHeader(item.date, isShowFeedIcon, com.ay.briefyou.brief.list.dayCount(pagingItems, index), line = under) // Brief You: with the day's count, and a line while articles pass under it
                    }
                }

                else -> {}
            }
        }
    }
}

private fun key(item: ArticleFlowItem): String {
    return when (item) {
        is ArticleFlowItem.Article -> item.articleWithFeed.article.id
        is ArticleFlowItem.Date -> item.date
    }
}

private fun contentType(item: ArticleFlowItem): Int {
    return when (item) {
        is ArticleFlowItem.Article -> CONTENT_TYPE_ARTICLE
        is ArticleFlowItem.Date -> CONTENT_TYPE_DATE_HEADER
    }
}

const val CONTENT_TYPE_ARTICLE = 1
const val CONTENT_TYPE_DATE_HEADER = 2
