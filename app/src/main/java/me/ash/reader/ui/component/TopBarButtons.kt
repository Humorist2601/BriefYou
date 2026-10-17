@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.component

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Top bar buttons in Material 3 Expressive style: tonal (filled with the accent's soft container
 * color) instead of bare icons, and connected into one pill when several sit together. Pressing a
 * button makes it a little wider and rounder, the "shape morph" of Expressive button groups.
 */

/** One button of a [TopBarButtonGroup]. [selected] shows it filled in the accent color. */
data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private val ButtonHeight = 40.dp
private val ButtonWidth = 48.dp
private val PressedExtraWidth = 8.dp
private val OuterCorner = 20.dp
private val InnerCorner = 6.dp
private val GroupGap = 2.dp

/** A single tonal circle button (Settings, Back) with an optional red dot. */
@Composable
fun TonalCircleButton(
    icon: ImageVector,
    description: String,
    showBadge: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BadgedBox(
        modifier = modifier.padding(horizontal = 8.dp),
        badge = {
            if (showBadge) {
                Badge(
                    modifier = Modifier.size(8.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        Segment(
            action = TopBarAction(icon, description, onClick = onClick),
            leadingCorner = OuterCorner,
            trailingCorner = OuterCorner,
        )
    }
}

/**
 * Several tonal buttons connected into one pill: rounded at the outer ends, slightly rounded
 * between buttons, with a small gap. A single action is a circle.
 */
@Composable
fun TopBarButtonGroup(actions: List<TopBarAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(GroupGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            Segment(
                action = action,
                leadingCorner = if (index == 0) OuterCorner else InnerCorner,
                trailingCorner = if (index == actions.lastIndex) OuterCorner else InnerCorner,
            )
        }
    }
}

@Composable
private fun Segment(action: TopBarAction, leadingCorner: Dp, trailingCorner: Dp) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()

    // Pressed: wider, and the inner corners round up to match the outer ones.
    val width by animateDpAsState(if (pressed) ButtonWidth + PressedExtraWidth else ButtonWidth, spatial, label = "width")
    val leading by animateDpAsState(if (pressed) OuterCorner else leadingCorner, spatial, label = "leading")
    val trailing by animateDpAsState(if (pressed) OuterCorner else trailingCorner, spatial, label = "trailing")
    val container by
        animateColorAsState(
            if (action.selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            effects,
            label = "container",
        )
    val content by
        animateColorAsState(
            if (action.selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
            effects,
            label = "content",
        )

    Surface(
        shape =
            RoundedCornerShape(
                topStart = leading,
                bottomStart = leading,
                topEnd = trailing,
                bottomEnd = trailing,
            ),
        color = container,
        contentColor = content,
        modifier = Modifier.size(width = width, height = ButtonHeight),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize().clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Button,
                    onClickLabel = action.description,
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                    action.onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.description,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
