package me.ash.reader.brief.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * Pieces every Brief You sheet is built from, so they all look and move alike: the head (title,
 * a muted line, the divider), a joined list of choices, a joined row of buttons, and a value with
 * − and + around it.
 */

/** A sheet's head without an icon: the title, an optional muted line, then the divider. */
@Composable
fun SheetTitle(title: String, hint: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    if (hint != null) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(14.dp))
}

/**
 * One row of a joined list of choices: the chosen one filled in the accent tone with a tick, the
 * others plain. [shape] comes from [groupShape].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceRow(
    title: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleStyle: TextStyle? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh, effects, label = "choice")
    val content by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurface, effects, label = "choiceText")
    val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.98f)
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        interactionSource = press.interaction,
        modifier = with(press) { Modifier.fillMaxWidth().squeeze() },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.merge(titleStyle),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) content else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * A joined row of [count] buttons, one of them [selected] (−1 for none), like the sleep timer's:
 * the chosen one filled in the accent colour. [item] draws a button's label.
 */
@Composable
fun JoinedChoices(
    count: Int,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable RowScope.(index: Int, on: Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Group.Gap)) {
        repeat(count) { i ->
            val on = i == selected
            val outer = 26.dp
            val inner = 10.dp
            val shape =
                RoundedCornerShape(
                    topStart = if (i == 0) outer else inner,
                    bottomStart = if (i == 0) outer else inner,
                    topEnd = if (i == count - 1) outer else inner,
                    bottomEnd = if (i == count - 1) outer else inner,
                )
            val container by animateColorAsState(if (on) scheme.primary else scheme.surfaceContainerHigh, label = "joined")
            val content by animateColorAsState(if (on) scheme.onPrimary else scheme.onSurface, label = "joinedText")
            val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.94f)
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onPick(i)
                },
                shape = shape,
                color = container,
                contentColor = content,
                interactionSource = press.interaction,
                modifier = with(press) { Modifier.weight(1f).height(52.dp).squeeze() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item(i, on)
                }
            }
        }
    }
}

/** A label for [JoinedChoices]: bolder on the chosen one. */
@Composable
fun JoinedLabel(text: String, on: Boolean, style: TextStyle? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.merge(style),
        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A value shown large in a pill, with − and + to step it (a new value rolls in from the side it
 * moved to). The buttons fade when there is no step left that way.
 */
@Composable
fun ValueStepper(
    value: Float,
    label: (Float) -> String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    downDescription: String,
    upDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepButton(Icons.Rounded.Remove, downDescription, canDown, onDown)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f).height(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        val up = targetState > initialState
                        (fadeIn() + scaleIn(initialScale = 0.7f) + slideInVertically { if (up) it else -it }) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.7f) + slideOutVertically { if (up) -it / 2 else it / 2 })
                    },
                    label = "stepper",
                ) {
                    Text(label(it), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        StepButton(Icons.Rounded.Add, upDescription, canUp, onUp)
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val press = rememberPressFeel(rest = 0.dp, held = 0.dp, squeezeTo = 0.9f)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(Corner.Small),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = press.interaction,
        modifier = with(press) { Modifier.size(56.dp).squeeze() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f),
            )
        }
    }
}
