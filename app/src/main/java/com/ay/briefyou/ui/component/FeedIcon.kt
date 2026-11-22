package com.ay.briefyou.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Bookmark // Brief You
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ay.briefyou.R
import com.ay.briefyou.ui.component.base.Base64Image
import com.ay.briefyou.ui.component.base.BYAsyncImage

/** Feed icons stored as "emoji:😀" are custom emoji icons (Brief You). */
const val EMOJI_PREFIX = "emoji:"

/** The icon of Read later's Saved Articles feed: a bookmark, drawn like the letter icons (Brief You). */
const val SAVED_ARTICLES_ICON = "icon:bookmark"

@Composable
fun FeedIcon(
    modifier: Modifier = Modifier,
    feedName: String? = "",
    iconUrl: String?,
    size: Dp = 20.dp,
    placeholderIcon: ImageVector? = null,
) {
    val symbol = com.ay.briefyou.brief.feeds.SymbolIcons.of(iconUrl) // Brief You: a chosen Material icon
    if (iconUrl == SAVED_ARTICLES_ICON || symbol != null) { // Brief You: Saved Articles, or a chosen Material icon
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary), // Brief You
            contentAlignment = Alignment.Center, // Brief You
        ) { // Brief You
            Icon( // Brief You
                imageVector = if (iconUrl == SAVED_ARTICLES_ICON) androidx.compose.material.icons.Icons.Rounded.Bookmark else symbol ?: androidx.compose.material.icons.Icons.Rounded.Bookmark, // Brief You: Saved Articles keeps its filled bookmark
                contentDescription = feedName, // Brief You
                tint = MaterialTheme.colorScheme.onPrimary, // Brief You
                modifier = Modifier.size(size * 0.6f), // Brief You
            ) // Brief You
        } // Brief You
    } else if (iconUrl != null && iconUrl.startsWith(EMOJI_PREFIX)) { // Brief You
        // On a circle in the feed's name colour (taken from the emoji itself), like the website
        // icons and the Material icons are round.
        val accent = com.ay.briefyou.brief.feeds.rememberFeedAccent(iconUrl) ?: MaterialTheme.colorScheme.primary
        // A soft tint of the colour (not a strong one): the emoji's own colours are what the
        // circle is taken from, so a strong circle swallowed the emoji.
        val base = MaterialTheme.colorScheme.surfaceContainerHighest
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(accent.copy(alpha = 0.3f).compositeOver(base)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = iconUrl.removePrefix(EMOJI_PREFIX),
                fontSize = (size.value * 0.45f).sp,
                lineHeight = (size.value * 0.55f).sp,
                maxLines = 1,
            )
        }
    } else if (iconUrl.isNullOrEmpty()) {
        if (placeholderIcon == null) {
            FontIcon(modifier, size, feedName ?: "")
        } else {
            ImageIcon(modifier, placeholderIcon, feedName ?: "")
        }
    }
    // e.g. image/gif;base64,R0lGODlh...
    else if (iconUrl.startsWith("image/") && iconUrl.contains(";base64,")) { // Brief You: no pattern built on every redraw
        Base64Image(
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            base64Uri = iconUrl,
            onEmpty = { FontIcon(modifier, size, feedName ?: "") },
        )
    } else {
        // Brief You: decoded at the size it's shown (icons are often 512 px or more)
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
        BYAsyncImage(
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentDescription = feedName ?: "",
            data = iconUrl,
            size = coil3.size.Size(px, px), // Brief You
            placeholder = null,
        )
    }
}

@Composable
private fun ImageIcon(modifier: Modifier, placeholderIcon: ImageVector, feedName: String) {
    Icon(
        modifier = modifier,
        imageVector = placeholderIcon,
        contentDescription = feedName,
    )
}

@Composable
private fun FontIcon(modifier: Modifier, size: Dp, feedName: String) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = feedName.ifEmpty { " " }.first().toString(),
            style = MaterialTheme.typography.bodyMedium.merge(
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        )
    }
}

@Preview
@Composable
fun FeedIconPrev() {
    FeedIcon(feedName = stringResource(R.string.preview_feed_name), iconUrl = null)
}
