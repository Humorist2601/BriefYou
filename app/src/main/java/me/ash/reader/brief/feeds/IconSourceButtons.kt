@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.feeds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R

/**
 * "Feed's picture" | "Website icon" as one joined two-part button, like the top bars' button
 * groups: pressed, a half widens and squeezes the other; the half in use is lit in the soft
 * accent colour; neither is lit when the feed uses an icon, emoji or image of its own
 * ([feedPicture] null). Used by Add a feed and Change icon. Brief You.
 */
@Composable
fun IconSourceButtons(
    feedPicture: Boolean?,
    onFeedPicture: () -> Unit,
    onWebsiteIcon: () -> Unit,
    modifier: Modifier = Modifier,
    /** Small previews of the two icons (a symbol shows when one isn't known). */
    feedPictureUrl: String? = null,
    websiteIconUrl: String? = null,
    feedName: String? = null,
) {
    val first = remember { MutableInteractionSource() }
    val second = remember { MutableInteractionSource() }
    val firstPressed by first.collectIsPressedAsState()
    val secondPressed by second.collectIsPressedAsState()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val firstWeight by animateFloatAsState(if (firstPressed) 1.15f else if (secondPressed) 0.85f else 1f, spatial, label = "first")
    val secondWeight by animateFloatAsState(if (secondPressed) 1.15f else if (firstPressed) 0.85f else 1f, spatial, label = "second")
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Half(Icons.Rounded.RssFeed, feedPictureUrl, feedName, stringResource(R.string.feed_icon_feed_picture), feedPicture == true, start = true, first, onFeedPicture, Modifier.weight(firstWeight))
        Half(Icons.Outlined.Public, websiteIconUrl, feedName, stringResource(R.string.feed_icon_website_short), feedPicture == false, start = false, second, onWebsiteIcon, Modifier.weight(secondWeight))
    }
}

@Composable
private fun Half(
    icon: ImageVector,
    preview: String?,
    feedName: String?,
    label: String,
    lit: Boolean,
    start: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<androidx.compose.ui.graphics.Color>()
    val container by
        animateColorAsState(
            if (lit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            effects,
            label = "container",
        )
    val content by
        animateColorAsState(
            if (lit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            effects,
            label = "content",
        )
    val outer = 20.dp
    val inner = 6.dp
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        shape =
            RoundedCornerShape(
                topStart = if (start) outer else inner,
                bottomStart = if (start) outer else inner,
                topEnd = if (start) inner else outer,
                bottomEnd = if (start) inner else outer,
            ),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier.height(40.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (preview != null) {
                me.ash.reader.ui.component.FeedIcon(feedName = feedName, iconUrl = preview, size = 22.dp)
            } else {
                Icon(icon, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
