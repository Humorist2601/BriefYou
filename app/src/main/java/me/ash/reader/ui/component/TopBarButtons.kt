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
 * Top bar buttons in Material 3 Expressive style: a barely-there tonal shape instead of bare icons, and connected into one pill when several sit together. A single
 * circle becomes a squircle when pressed; in a pill, the pressed button widens and squeezes the
 * others (the shape morph of Expressive buttons and button groups).
 */

/** One button of a [TopBarButtonGroup]. [selected] shows it in the soft accent tone. */
data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private val ButtonHeight = 40.dp
private val ButtonWidth = 48.dp
private val PressedExtraWidth = 12.dp
private val CircleSize = 40.dp
private val SquircleCorner = 12.dp
private val OuterCorner = 20.dp
private val InnerCorner = 6.dp
private val GroupGap = 2.dp

/** A single tonal circle button (Settings, Back) with an optional red dot. Pressed, it becomes a squircle. */
@Composable
fun TonalCircleButton(
    icon: ImageVector,
    description: String,
    showBadge: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val corner by animateDpAsState(if (pressed) SquircleCorner else CircleSize / 2, spatial, label = "corner")
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
            interaction = interaction,
            width = CircleSize,
            height = CircleSize,
            leadingCorner = corner,
            trailingCorner = corner,
        )
    }
}

/**
 * Several tonal buttons connected into one pill: rounded at the outer ends, slightly rounded
 * between buttons, with a small gap. Pressing one makes it wider and squeezes the others, so the
 * pill keeps its length (the Expressive button group). A single action works like
 * [TonalCircleButton].
 */
@Composable
fun TopBarButtonGroup(actions: List<TopBarAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    if (actions.size == 1) {
        val a = actions.first()
        TonalCircleButton(icon = a.icon, description = a.description, modifier = modifier, onClick = a.onClick)
        return
    }
    val interactions = remember(actions.size) { List(actions.size) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState().value }
    val pressedIndex = pressed.indexOf(true)
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(GroupGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            val target =
                when {
                    pressedIndex < 0 -> ButtonWidth
                    index == pressedIndex -> ButtonWidth + PressedExtraWidth
                    else -> ButtonWidth - PressedExtraWidth / (actions.size - 1)
                }
            val width by animateDpAsState(target, spatial, label = "width")
            Segment(
                action = action,
                interaction = interactions[index],
                width = width,
                height = ButtonHeight,
                leadingCorner = if (index == 0) OuterCorner else InnerCorner,
                trailingCorner = if (index == actions.lastIndex) OuterCorner else InnerCorner,
            )
        }
    }
}

@Composable
private fun Segment(
    action: TopBarAction,
    interaction: MutableInteractionSource,
    width: Dp,
    height: Dp,
    leadingCorner: Dp,
    trailingCorner: Dp,
) {
    val view = LocalView.current
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by
        animateColorAsState(
            // A barely-there tone, so the buttons don't pull the eye; an action that is on
            // (searching, marking as read) gets the soft accent tone.
            if (action.selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            effects,
            label = "container",
        )
    val content by
        animateColorAsState(
            if (action.selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            effects,
            label = "content",
        )

    Surface(
        shape =
            RoundedCornerShape(
                topStart = leadingCorner,
                bottomStart = leadingCorner,
                topEnd = trailingCorner,
                bottomEnd = trailingCorner,
            ),
        color = container,
        contentColor = content,
        modifier = Modifier.size(width = width, height = height),
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
