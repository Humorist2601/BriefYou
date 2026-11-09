package me.ash.reader.ui.page.home.feeds

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Badge // Brief You
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Brief You
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.ui.page.home.feeds.drawer.group.GroupOptionViewModel
import me.ash.reader.ui.theme.Shape32
import me.ash.reader.ui.theme.ShapeTop32

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: Group,
    isExpanded: () -> Boolean,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onExpanded: () -> Unit = {},
    onLongClick: () -> Unit = {},
    count: Int = 0, // Brief You: the group's unread (or all/starred) count, next to its arrow
    iconFeed: me.ash.reader.domain.model.feed.Feed? = null, // Brief You: its first feed, for the group's icon tile
    groupOnClick: () -> Unit = {},
) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    groupOnClick()
                },
                onLongClick = {
                    groupOptionViewModel.fetchGroup(groupId = group.id)
                    onLongClick()
                }
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh) // Brief You: the group's row in the joined list
            .padding(vertical = 12.dp) // Brief You: was 22 above and below
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            me.ash.reader.brief.feeds.GroupIconTile(group.name, iconFeed, Modifier.padding(start = 14.dp)) // Brief You: an icon tile
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp), // Brief You: after the tile
                text = group.name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp), // Brief You: 16 sp, like every row
                color = MaterialTheme.colorScheme.onSurface, // Brief You
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            me.ash.reader.brief.filters.WatchedCount(me.ash.reader.brief.filters.WatchedWords.groupCounts[group.id] ?: 0) // Brief You: watched words
            if (count > 0) { // Brief You: the group's count in a pill, next to its arrow
                me.ash.reader.brief.feeds.CountPill(count, Modifier.padding(end = 10.dp))
            }
            Row(
                modifier = Modifier
                    .padding(end = 20.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest) // Brief You: shows on the row
                    .clickable { onExpanded() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isExpanded()) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (isExpanded()) R.string.expand_less else R.string.expand_more),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
inline fun GroupWithFeedsContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Shape32, // Brief You: its place in the joined list
    top: androidx.compose.ui.unit.Dp = 16.dp, // Brief You: the gap above it
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(top = top) // Brief You
            .padding(horizontal = 16.dp)
            .clip(shape) // Brief You
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = { content() }
    )
}
