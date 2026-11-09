package me.ash.reader.brief.feeds

import me.ash.reader.brief.ui.InWindowAlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import me.ash.reader.R

/**
 * Change a feed's or group's icon: pick a Material icon, type any emoji, choose an image, or go
 * back to the
 * website's icon (a group: the folder).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedIconDialog(
    onSymbol: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onImage: () -> Unit,
    onWebsiteIcon: () -> Unit,
    onDismiss: () -> Unit,
    /** The icon in use now (`icon:<name>` marks its tile). */
    current: String? = null,
    // A group's dialog: "Folder icon" in place of the website's icon.
    resetLabel: String? = null,
    resetIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Public,
) {
    InWindowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_icon_change)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.feed_icon_symbols),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                // Six across, four and a half rows tall: the cut row and the fading edges show
                // there's more to scroll to.
                val gridState = rememberLazyGridState()
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val tile = (maxWidth - 6.dp * 5) / 6
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = gridState,
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = tile * 4.5f + 6.dp * 4)
                            .fadingEdges(gridState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(SymbolIcons.all, key = { it.first }) { (name, vector) ->
                        val selected = current == SymbolIcons.PREFIX + name
                        Surface(
                            onClick = {
                                onSymbol(name)
                                onDismiss()
                            },
                            // The chosen one is a circle in the accent colour; the rest rounded tiles.
                            shape = if (selected) CircleShape else RoundedCornerShape(12.dp),
                            color =
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor =
                                if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.aspectRatio(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(vector, contentDescription = name, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                }
                Spacer(Modifier.height(12.dp))
                // Any emoji, typed or picked from the keyboard's emoji page.
                var emoji by rememberSaveable { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(8) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.feed_icon_emoji_hint)) },
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = emoji.isNotBlank(),
                        onClick = {
                            onEmoji(emoji)
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.feed_icon_use))
                    }
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        onImage()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.feed_icon_image))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        onWebsiteIcon()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(resetIcon, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(resetLabel ?: stringResource(R.string.feed_icon_website))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_settings_cancel)) }
        },
    )
}

/** Fades the grid's top and bottom edge out while there's more to scroll that way. */
private fun Modifier.fadingEdges(state: LazyGridState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = 28.dp.toPx()
            if (state.canScrollBackward) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = edge),
                    blendMode = BlendMode.DstIn,
                    size = size.copy(height = edge),
                )
            }
            if (state.canScrollForward) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - edge, endY = size.height),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - edge),
                    blendMode = BlendMode.DstIn,
                    size = size.copy(height = edge),
                )
            }
        }
