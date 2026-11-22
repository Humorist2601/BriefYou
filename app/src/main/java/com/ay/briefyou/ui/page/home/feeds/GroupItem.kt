package com.ay.briefyou.ui.page.home.feeds

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Badge // Brief You
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
import com.ay.briefyou.ui.theme.ShapeTop32

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: Group,
    isExpanded: () -> Boolean,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onExpanded: () -> Unit = {},
    onLongClick: () -> Unit = {},
    count: Int = 0, // Brief You: the group's unread (or all/starred) count, next to its arrow
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
            com.ay.briefyou.brief.feeds.GroupIconTile(group.id, Modifier.padding(start = 14.dp)) // Brief You: its icon (a folder, or the one chosen)
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
            com.ay.briefyou.brief.filters.WatchedCount(com.ay.briefyou.brief.filters.WatchedWords.groupCounts[group.id] ?: 0) // Brief You: watched words
            if (count > 0) { // Brief You: the group's count in a pill, next to its arrow
                com.ay.briefyou.brief.feeds.CountPill(count, Modifier.padding(end = 10.dp))
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
}

@Composable
inline fun GroupWithFeedsContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Shape32, // Brief You: its place in the joined list
    top: androidx.compose.ui.unit.Dp = 16.dp, // Brief You: the gap above it
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow, // Brief You
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
