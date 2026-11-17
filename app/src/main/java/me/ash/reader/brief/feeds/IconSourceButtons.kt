@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.feeds

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.Dp
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
    val feedPainter = feedPictureUrl?.let { coil3.compose.rememberAsyncImagePainter(it) }
    val failed = feedPainter?.state?.collectAsState()?.value is coil3.compose.AsyncImagePainter.State.Error
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
    val feedLabel = stringResource(R.string.feed_icon_feed_picture)
    val websiteLabel = stringResource(R.string.feed_icon_website_short)
    // Pressed, a half widens and squeezes the other, but only as far as the other's label still
    // fits: the labels keep their size and are never cut short.
    val style = MaterialTheme.typography.labelLarge
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun need(label: String) =
        with(density) { measurer.measure(label, style).size.width.toDp() } + HALF_PADDING * 2 + PREVIEW_SIZE + PREVIEW_GAP
    val feedNeeds = remember(feedLabel, style) { need(feedLabel) }
    val websiteNeeds = remember(websiteLabel, style) { need(websiteLabel) }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val total = maxWidth - GAP
        // Halves are equal, unless a narrow screen can't fit a label in half: then each gets
        // its share of the room by how much it needs.
        val roomy = total / 2 >= feedNeeds && total / 2 >= websiteNeeds
        val firstBase = if (roomy) total / 2 else total * (feedNeeds / (feedNeeds + websiteNeeds))
        val secondBase = total - firstBase
        val grow = total / 2 * 0.08f
        fun widthOf(pressed: Boolean, otherPressed: Boolean, base: Dp, ownNeeds: Dp, otherNeeds: Dp): Dp =
            when {
                pressed -> minOf(base + grow, total - otherNeeds).coerceAtLeast(base)
                otherPressed -> maxOf(base - grow, ownNeeds).coerceAtMost(base)
                else -> base
            }
        val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
        val firstWidth by animateDpAsState(widthOf(firstPressed, secondPressed, firstBase, feedNeeds, websiteNeeds), spatial, label = "first")
        val secondWidth by animateDpAsState(widthOf(secondPressed, firstPressed, secondBase, websiteNeeds, feedNeeds), spatial, label = "second")
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            Half(Icons.Rounded.RssFeed, feedPainter, null, feedName, feedLabel, feedPicture == true, true, first, onFeedPicture, Modifier.width(firstWidth))
            Half(Icons.Outlined.Public, null, websiteIconUrl, feedName, websiteLabel, feedPicture == false, false, second, onWebsiteIcon, Modifier.width(secondWidth))
        }
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
            modifier = Modifier.padding(horizontal = HALF_PADDING),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The icon itself; the symbol while it loads or when it can't be loaded (an empty
            // dark circle looked broken).
            val painter = loadedPreview ?: preview?.let { coil3.compose.rememberAsyncImagePainter(it) }
            val loaded = (painter as? coil3.compose.AsyncImagePainter)?.state?.collectAsState()?.value is coil3.compose.AsyncImagePainter.State.Success
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
            Spacer(Modifier.width(PREVIEW_GAP))
            // Shrinks a little instead of being cut short while the other half is pressed.
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

private val HALF_PADDING = 10.dp
private val PREVIEW_SIZE = 22.dp
private val PREVIEW_GAP = 8.dp
private val GAP = 2.dp
