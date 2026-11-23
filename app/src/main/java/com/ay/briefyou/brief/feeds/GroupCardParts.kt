package com.ay.briefyou.brief.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ay.briefyou.domain.model.feed.Feed
import com.ay.briefyou.ui.component.FeedIcon

private val StackCircle = 28.dp
private val StackStep = 18.dp

/**
 * The feeds of a group as overlapping round icons on its card: up to three, from the feeds with the
 * most unread. A group of more than three shows two icons and a "+N" circle, so it is never wider
 * than three circles.
 */
@Composable
fun FeedIconStack(feeds: List<Feed>, ring: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    if (feeds.isEmpty()) return
    val shown = remember(feeds) { feeds.sortedWith(compareByDescending<Feed> { it.important }.thenBy { it.name }) }
    val icons = if (shown.size > 3) shown.take(2) else shown
    val more = if (shown.size > 3) shown.size - 2 else 0
    val circles = icons.size + if (more > 0) 1 else 0
    Box(modifier.width(StackCircle + StackStep * (circles - 1)).height(StackCircle)) {
        icons.forEachIndexed { i, feed ->
            Box(
                Modifier.offset(x = StackStep * i)
                    .size(StackCircle)
                    .clip(CircleShape)
                    .background(ring)
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                FeedIcon(feedName = feed.name, iconUrl = feed.icon, size = StackCircle - 4.dp)
            }
        }
        if (more > 0) {
            Box(
                Modifier.offset(x = StackStep * icons.size)
                    .size(StackCircle)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$more",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                )
            }
        }
    }
}
