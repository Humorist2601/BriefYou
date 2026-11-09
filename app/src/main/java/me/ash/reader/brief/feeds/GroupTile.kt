package me.ash.reader.brief.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.ui.component.FeedIcon

/**
 * A group's icon on the feeds page: its first feed's icon on a small rounded tile, so groups are
 * told apart at a glance; the group's first letter when none of its feeds has an icon.
 */
@Composable
fun GroupIconTile(groupName: String, feed: Feed?, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (feed?.icon != null) {
            FeedIcon(feedName = feed.name, iconUrl = feed.icon, size = 22.dp)
        } else {
            Text(
                text = groupName.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** A count in a small pill (a group's unread count on the feeds page). */
@Composable
fun CountPill(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
