package com.ay.briefyou.ui.page.home.feeds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size // Brief You
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.ui.component.FeedIcon
import com.ay.briefyou.ui.component.base.BYExtensibleVisibility
import com.ay.briefyou.ui.page.home.feeds.drawer.feed.FeedOptionViewModel

@OptIn(
    ExperimentalFoundationApi::class,
)
@Composable
private fun FeedItemImpl(
    feed: Feed,
    isFirstItem: Boolean = false,
    isLastItem: () -> Boolean = { false },
    onLongClickCallback: (String) -> Unit = {},
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    // Brief You: the feeds of an open group are rows in the page's colour, joined like the groups
    // (16 dp at the ends of the list, 6 dp between), inset in the group's card.
    val last = isLastItem()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (isFirstItem) 16.dp else 6.dp,
        topEnd = if (isFirstItem) 16.dp else 6.dp,
        bottomStart = if (last) 16.dp else 6.dp,
        bottomEnd = if (last) 16.dp else 6.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = if (isFirstItem) 6.dp else 3.dp, bottom = if (last) 6.dp else 0.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = {
                onClick()
            }, onLongClick = {
                onLongClick()
                scope.launch {
                    onLongClickCallback(feed.id)
                }
            })
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            FeedIcon(feedName = feed.name, iconUrl = feed.icon, size = 28.dp)
            Text(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                text = feed.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        com.ay.briefyou.brief.filters.WatchedFeedCount(com.ay.briefyou.brief.filters.WatchedWords.feedCounts[feed.id] ?: 0) // Brief You: watched words
        if (feed.important != 0) {
            // Brief You: a chip as wide as the group's arrow chip, so the two line up
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = feed.important.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
)
@Composable
fun FeedItem(
    feed: Feed,
    isFirstItem: Boolean = false, // Brief You
    isLastItem: () -> Boolean = { false },
    isExpanded: () -> Boolean,
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    BYExtensibleVisibility(visible = isExpanded()) {
        FeedItemImpl(
            feed = feed,
            isFirstItem = isFirstItem,
            isLastItem = isLastItem,
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickCallback = { feedId ->
                scope.launch {
                    feedOptionViewModel.fetchFeed(feedId = feedId)
                }
            })
    }
}
