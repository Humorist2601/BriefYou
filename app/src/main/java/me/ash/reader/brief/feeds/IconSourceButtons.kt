package me.ash.reader.brief.feeds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.ui.rememberPressFeel

/**
 * "Feed's picture" | "Website icon" as one joined two-part button (like the video player's
 * joined buttons): the half in use is lit in the soft accent colour and its inner corners round
 * off; neither is lit when the feed uses an icon, emoji or image of its own ([feedPicture] null).
 * Used by Add a feed and Change icon. Brief You.
 */
@Composable
fun IconSourceButtons(
    feedPicture: Boolean?,
    onFeedPicture: () -> Unit,
    onWebsiteIcon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Half(Icons.Rounded.RssFeed, stringResource(R.string.feed_icon_feed_picture), feedPicture == true, start = true, onFeedPicture, Modifier.weight(1f))
        Half(Icons.Outlined.Public, stringResource(R.string.feed_icon_website_short), feedPicture == false, start = false, onWebsiteIcon, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Half(icon: ImageVector, label: String, lit: Boolean, start: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.95f)
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<androidx.compose.ui.graphics.Color>()
    val inner by animateDpAsState(if (lit) 12.dp else 6.dp, spatial, label = "inner corner")
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
        interactionSource = press.interaction,
        modifier = with(press) { modifier.height(40.dp).squeeze() },
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
