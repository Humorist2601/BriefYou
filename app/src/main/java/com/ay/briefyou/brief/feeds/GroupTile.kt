package com.ay.briefyou.brief.feeds

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ay.briefyou.ui.component.FeedIcon

/**
 * A group's icon on the feeds page: the icon chosen for it (an emoji or an image, from the group's
 * options) on a small rounded tile, or a folder.
 * A chosen Material icon is drawn in the tile's colours, like the folder.
 */
@Composable
fun GroupIconTile(groupId: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { GroupIcons.load(context) }
    val icon = GroupIcons.icons[groupId]
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 3))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val symbol = SymbolIcons.of(icon)
        if (symbol != null) {
            Icon(
                symbol,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.55f),
            )
        } else if (icon != null) {
            FeedIcon(feedName = null, iconUrl = icon, size = size * 0.6f)
        } else {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size * 0.55f),
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

/**
 * A group's icon as a plain symbol before its name on the feeds page (the home screen's group
 * cards): the chosen Material icon or the folder in [tint], or the chosen emoji or image as it is.
 */
@Composable
fun GroupIconPlain(
    groupId: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { GroupIcons.load(context) }
    val icon = GroupIcons.icons[groupId]
    val symbol = SymbolIcons.of(icon)
    if (symbol != null) {
        Icon(symbol, contentDescription = null, tint = tint, modifier = modifier.size(size))
    } else if (icon != null) {
        FeedIcon(feedName = null, iconUrl = icon, size = size, modifier = modifier)
    } else {
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
