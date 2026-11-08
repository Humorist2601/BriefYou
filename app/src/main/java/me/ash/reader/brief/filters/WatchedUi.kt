package me.ash.reader.brief.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The watched word in an unread article's title, as a small tertiary chip with an eye (by the
 * time in lists). Nothing when the title has none or the article is read.
 */
@Composable
fun WatchedChip(title: String, unread: Boolean, modifier: Modifier = Modifier) {
    if (!unread) return
    val words = WatchedWords.words
    val word = remember(title, words) { WatchedWords.match(title) } ?: return
    Spacer(Modifier.width(6.dp))
    Row(
        modifier =
            modifier.clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(start = 5.dp, end = 7.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = word,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/**
 * How many watched unread articles a feed or group has (home), the size of the unread count
 * badge next to it: a small tertiary badge with an eye.
 */
@Composable
fun WatchedCount(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    androidx.compose.material3.Badge(
        modifier = modifier.padding(end = 6.dp),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(10.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
