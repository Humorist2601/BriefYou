package com.ay.briefyou.ui.page.home.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Brief You
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.group.Group
import com.ay.briefyou.domain.model.group.GroupWithFeed
import com.ay.briefyou.ui.page.home.feeds.drawer.group.GroupOptionViewModel
import com.ay.briefyou.ui.theme.Shape32

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: Group,
    isExpanded: () -> Boolean,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onExpanded: () -> Unit = {},
    onLongClick: () -> Unit = {},
    count: Int = 0, // Brief You: the group's unread (or all/starred) count, big on its card
    feeds: List<com.ay.briefyou.domain.model.feed.Feed> = emptyList(), // Brief You: for the icon stack
    groupOnClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    groupOnClick()
                },
                onLongClick = {
                    groupOptionViewModel.fetchGroup(groupId = group.id, feedCount = feeds.size) // Brief You: its feed count for the sheet
                    onLongClick()
                }
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh) // Brief You: the group's row in the joined list
            .padding(horizontal = 14.dp, vertical = 10.dp), // Brief You: the card of the home screen
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.ay.briefyou.brief.feeds.GroupIconPlain(group.id, MaterialTheme.colorScheme.primary) // Brief You: its icon, plain
                Text(
                    text = group.name.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                com.ay.briefyou.brief.filters.WatchedCount(com.ay.briefyou.brief.filters.WatchedWords.groupCounts[group.id] ?: 0) // Brief You: watched words
            }
            Text(
                text = count.toString(), // Brief You: the number only; the page says what is counted
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        com.ay.briefyou.brief.feeds.FeedIconStack(feeds, MaterialTheme.colorScheme.surfaceContainerHigh)
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest) // Brief You: shows on the row
                .clickable { onExpanded() },
            contentAlignment = Alignment.Center,
        ) {
            // Brief You: one arrow that turns over, instead of two that swap.
            val turn by androidx.compose.animation.core.animateFloatAsState(if (isExpanded()) 180f else 0f, label = "group arrow")
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = stringResource(if (isExpanded()) R.string.expand_less else R.string.expand_more),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
    }
}

@Composable
inline fun GroupWithFeedsContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Shape32, // Brief You: its place in the joined list
    top: androidx.compose.ui.unit.Dp = 16.dp, // Brief You: the gap above it
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh, // Brief You: the same colour as the group's own row, so the open group is one card
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(top = top) // Brief You
            .padding(horizontal = 16.dp)
            .clip(shape) // Brief You
            .background(color),
        content = { content() }
    )
}
