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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
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
    /** The feed's picture can't be loaded: the choice goes (only the website's icon is offered). */
    onFeedPictureFailed: () -> Unit = {},
) {
    // A picture that can't be loaded isn't offered (it would be a broken icon).
    val feedPainter = feedPictureUrl?.let { coil.compose.rememberAsyncImagePainter(it) }
    val failed = feedPainter?.state is coil.compose.AsyncImagePainter.State.Error
    androidx.compose.runtime.LaunchedEffect(failed) { if (failed) onFeedPictureFailed() }
    val alone = remember { MutableInteractionSource() }
    if (failed) {
        Row(modifier = modifier.fillMaxWidth()) {
            Half(Icons.Outlined.Public, null, websiteIconUrl, feedName, stringResource(R.string.feed_icon_website_short), feedPicture == false, null, alone, onWebsiteIcon, Modifier.weight(1f))
        }
        return
    }
    val first = remember { MutableInteractionSource() }
    val second = remember { MutableInteractionSource() }
    val firstPressed by first.collectIsPressedAsState()
    val secondPressed by second.collectIsPressedAsState()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val firstWeight by animateFloatAsState(if (firstPressed) 1.08f else if (secondPressed) 0.92f else 1f, spatial, label = "first")
    val secondWeight by animateFloatAsState(if (secondPressed) 1.08f else if (firstPressed) 0.92f else 1f, spatial, label = "second")
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Half(Icons.Rounded.RssFeed, feedPainter, null, feedName, stringResource(R.string.feed_icon_feed_picture), feedPicture == true, true, first, onFeedPicture, Modifier.weight(firstWeight))
        Half(Icons.Outlined.Public, null, websiteIconUrl, feedName, stringResource(R.string.feed_icon_website_short), feedPicture == false, false, second, onWebsiteIcon, Modifier.weight(secondWeight))
    }
}

@Composable
private fun Half(
    icon: ImageVector,
    /** A painter already loading the preview, or [preview] to load. */
    loadedPreview: androidx.compose.ui.graphics.painter.Painter?,
    preview: String?,
    feedName: String?,
    label: String,
    lit: Boolean,
    /** The first half (true), the second (false), or the only one (null). */
    start: Boolean?,
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
                topStart = if (start != false) outer else inner,
                bottomStart = if (start != false) outer else inner,
                topEnd = if (start != true) outer else inner,
                bottomEnd = if (start != true) outer else inner,
            ),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The icon itself; the symbol while it loads or when it can't be loaded (an empty
            // dark circle looked broken).
            val painter = loadedPreview ?: preview?.let { coil.compose.rememberAsyncImagePainter(it) }
            val loaded = (painter as? coil.compose.AsyncImagePainter)?.state is coil.compose.AsyncImagePainter.State.Success
            androidx.compose.foundation.layout.Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (!loaded) Icon(icon, null, Modifier.size(18.dp))
                if (painter != null) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // Shrinks a little instead of being cut short while the other half is pressed.
            androidx.compose.foundation.text.BasicText(
                label,
                style = MaterialTheme.typography.labelLarge.copy(color = androidx.compose.material3.LocalContentColor.current),
                maxLines = 1,
                autoSize =
                    androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = 11.sp,
                        maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                    ),
            )
        }
    }
}
