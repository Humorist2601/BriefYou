package me.ash.reader.brief.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The app's big number tile (Filters, Reading stats): its icon on a cookie, the number in the wide
 * font and a label, all left-aligned, with Triage's press (squeeze, tighter corners, the cookie
 * turns by one bump).
 *
 * [selected] null: a plain tile, always primary. True or false: one of a set you choose from; the
 * chosen one is tertiary (like the stats chart's bars), the others a quiet surface.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val press = rememberPressFeel(rest = 28.dp, held = 18.dp, bumps = 9)
    val container by
        animateColorAsState(
            when (selected) {
                null -> MaterialTheme.colorScheme.primaryContainer
                true -> MaterialTheme.colorScheme.tertiaryContainer
                false -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            label = "statTile",
        )
    val content by
        animateColorAsState(
            when (selected) {
                null -> MaterialTheme.colorScheme.onPrimaryContainer
                true -> MaterialTheme.colorScheme.onTertiaryContainer
                false -> MaterialTheme.colorScheme.onSurface
            },
            label = "statTileContent",
        )
    Surface(
        onClick = {
            if (selected == false) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        interactionSource = press.interaction,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(press.corner),
        modifier =
            with(press) { modifier.squeeze() }
                .then(
                    if (selected != null) Modifier.semantics { this.selected = selected } else Modifier
                ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .graphicsLayer { rotationZ = press.turn }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -press.turn },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = GoogleSansWideFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
