package com.ay.briefyou.brief.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ay.briefyou.brief.tour.TransientHint

/**
 * The pieces of the app's menus (DESIGN.md §10): a menu has no box of its own; it is a few cards,
 * a strip of round buttons and rows with a text, each drawn on `surfaceContainerHigh` (the screen behind is dimmed); the round buttons rest on `surfaceContainerLow`. In the image viewer they are dark grey and nothing is dimmed.
 */

/**
 * The image viewer's menus are always dark grey, whatever the theme: the picture sits on black and
 * is not dimmed behind a menu.
 */
val LocalMenuOnImage = androidx.compose.runtime.compositionLocalOf { false }

private val ImageMenuCard = androidx.compose.ui.graphics.Color(0xFF2B2930)
private val ImageMenuButton = androidx.compose.ui.graphics.Color(0xFF1D1B20)
private val ImageMenuOn = androidx.compose.ui.graphics.Color(0xFF4F378B)
private val ImageMenuOnText = androidx.compose.ui.graphics.Color(0xFFEADDFF)
private val ImageMenuText = androidx.compose.ui.graphics.Color(0xFFE6E0E9)

@Composable
private fun menuCardColor() =
    if (LocalMenuOnImage.current) ImageMenuCard else MaterialTheme.colorScheme.surfaceContainerHigh

/** A card holding a row of [MenuRoundButton]s, centred. */
@Composable
fun MenuStrip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = menuCardColor(),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/** Which round button's hint is showing: only one at a time. */
private object ActiveHint {
    var key by mutableStateOf<Any?>(null)
}

/**
 * A 52 dp round button with only an icon; [selected] (on) is the primary container. A long press
 * shows its [description] in a hint bubble for a moment.
 */
@Composable
fun MenuRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    var hint by remember { mutableStateOf(false) }
    val me = remember { Any() }
    val onImage = LocalMenuOnImage.current
    Box(modifier = modifier.size(52.dp)) {
        Surface(
            shape = CircleShape,
            color =
                if (onImage) (if (selected) ImageMenuOn else ImageMenuButton)
                else if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor =
                if (onImage) (if (selected) ImageMenuOnText else ImageMenuText)
                else if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ActiveHint.key = me // any hint still showing fades away
                            hint = true
                        },
                    ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
            }
        }
        if (hint) TransientHint(description, active = ActiveHint.key === me) {
            hint = false
            if (ActiveHint.key === me) ActiveHint.key = null
        }
    }
}

/** A single row of its own card: an icon and a text. */
@Composable
fun MenuRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = menuCardColor(),
        shadowElevation = 1.dp,
        contentColor = if (LocalMenuOnImage.current) ImageMenuText else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** The gap between a menu's pieces: the same 3 dp as between the rows of a joined group. */
@Composable
fun MenuGap() {
    Spacer(Modifier.height(Group.Gap))
}
