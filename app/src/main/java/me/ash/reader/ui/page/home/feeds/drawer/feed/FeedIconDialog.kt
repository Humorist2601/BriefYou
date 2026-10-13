package me.ash.reader.ui.page.home.feeds.drawer.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ash.reader.R

private val QUICK_EMOJI =
    listOf("📰", "🗞️", "💻", "🧪", "🎮", "🎬", "🎵", "📚", "🌍", "💰", "⚽", "🍳", "✈️", "🚀", "🎨", "🧠")

/** Change a feed's icon: pick an emoji, choose an image, or go back to the website's icon. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedIconDialog(
    onEmoji: (String) -> Unit,
    onImage: () -> Unit,
    onWebsiteIcon: () -> Unit,
    onDismiss: () -> Unit,
) {
    var emoji by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_icon_change)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.feed_icon_emoji),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QUICK_EMOJI.forEach { e ->
                        Surface(
                            onClick = {
                                onEmoji(e)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(e, fontSize = 22.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                    Icon(Icons.Outlined.Public, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.feed_icon_website))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ai_settings_cancel)) }
        },
    )
}
