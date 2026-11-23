package com.ay.briefyou.brief.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Whether an option sheet is open. [hide] slides it down first, like a swipe down does, instead of
 * it vanishing. Replaces the original Material 2 sheet state, with the same show / hide / isVisible.
 */
@Stable
class SheetSwitch {
    var isVisible by mutableStateOf(false)
        private set
    internal var closing by mutableStateOf(false)

    fun show() {
        closing = false
        isVisible = true
    }

    fun hide() {
        if (isVisible) closing = true
    }

    internal fun gone() {
        closing = false
        isVisible = false
    }
}

@Composable fun rememberSheetSwitch(): SheetSwitch = remember { SheetSwitch() }

/**
 * The app's bottom sheet (Material 3, like Triage's places, the gist and the summary) for a
 * [SheetSwitch]. Content gets 24 dp sides; dialogs opened from it should be drawn in their own
 * window (see [OptionSheetDialogs]) so they show above the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(switch: SheetSwitch, content: @Composable ColumnScope.() -> Unit) {
    if (!switch.isVisible) return
    val state = com.ay.briefyou.brief.ui.rememberFullSheetState()
    LaunchedEffect(switch.closing) {
        if (switch.closing) {
            state.hide()
            switch.gone()
        }
    }
    // Not lifted by the keyboard: a sheet has no text box, and a rename dialog's keyboard
    // shouldn't push the sheet up behind it.
    ModalBottomSheet(
        onDismissRequest = { switch.gone() },
        sheetState = state,
        shape = SheetShape,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets.navigationBars },
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), content = content)
    }
}

/**
 * Dialogs opened from an [OptionSheet]: the sheet is its own window, so dialogs drawn in the app's
 * window would sit under it. Here they get their own window instead, above the sheet.
 */
@Composable
fun OptionSheetDialogs(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDialogHost provides null, content = content)
}

/** A sheet's head: icon, title and a muted line under it, then the divider (the app's sheet design). */
@Composable
fun SheetHeader(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            title()
            subtitle?.invoke()
        }
    }
    Spacer(Modifier.height(18.dp))
}

/** A small heading over a part of a sheet ("Reading", "Move to group"), in the accent colour. */
@Composable
fun SheetSection(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * A setting as a tile, like the sort sheet's: lit and round when [on], a rounded square when off.
 * [on] null: a tile that only acts (a group's "apply to all feeds", which asks first).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetTile(icon: ImageVector, label: String, on: Boolean?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lit = on == true
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<androidx.compose.ui.graphics.Color>()
    val corner by androidx.compose.animation.core.animateDpAsState(if (lit) 30.dp else 16.dp, spatial, label = "tileCorner")
    val container by
        androidx.compose.animation.animateColorAsState(
            if (lit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            effects,
            label = "tileColor",
        )
    val content by
        androidx.compose.animation.animateColorAsState(
            if (lit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            effects,
            label = "tileContent",
        )
    val press = rememberPressFeel(rest = 0.dp, held = 0.dp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                with(press) { Modifier.squeeze() }
                    .size(60.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))
                    .background(container)
                    .then(
                        if (on != null) {
                            Modifier.toggleable(
                                value = on,
                                interactionSource = press.interaction,
                                indication = androidx.compose.material3.ripple(),
                                role = androidx.compose.ui.semantics.Role.Switch,
                                onValueChange = { onClick() },
                            )
                        } else {
                            Modifier.clickable(
                                interactionSource = press.interaction,
                                indication = androidx.compose.material3.ripple(),
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = onClick,
                            )
                        }
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Two actions side by side at the bottom of a sheet; the second in red when [dangerSecond]. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetActions(
    first: Pair<String, () -> Unit>,
    second: Pair<String, () -> Unit>?,
    dangerSecond: Boolean = true,
) {
    // Round tonal buttons; the second one in the soft red when it deletes.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilledTonalButton(
            onClick = first.second,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Text(first.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (second != null) {
            androidx.compose.material3.FilledTonalButton(
                onClick = second.second,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.weight(1f).height(48.dp),
                colors =
                    if (dangerSecond) {
                        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text(second.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

