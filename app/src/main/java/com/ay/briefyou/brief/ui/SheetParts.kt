package com.ay.briefyou.brief.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import com.ay.briefyou.R
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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

/**
 * The state every Brief You sheet uses: opens fully, no half-way stop (Material 3 1.5 replaces
 * the old rememberModalBottomSheetState(skipPartiallyExpanded = true) with this).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun rememberFullSheetState(): androidx.compose.material3.SheetState =
    androidx.compose.material3.rememberBottomSheetState(
        initialValue = androidx.compose.material3.SheetValue.Hidden,
        enabledValues =
            setOf(androidx.compose.material3.SheetValue.Hidden, androidx.compose.material3.SheetValue.Expanded),
    )

/**
 * A slider in the app's style (Material 3 1.5's state-based slider, used like the old one: the
 * value comes in, changes go out).
 */
@Composable
fun BriefSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val state = androidx.compose.runtime.remember(steps, valueRange) { androidx.compose.material3.SliderState(value, steps, valueRange) }
    state.value = value
    androidx.compose.material3.Slider(
        state = state,
        onValueChange = onValueChange,
        modifier = modifier,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/** The top corners every Brief You sheet shares: the Banner corner, as the Home hero card. */
val SheetShape = RoundedCornerShape(topStart = Corner.Banner, topEnd = Corner.Banner)

/**
 * An icon on a nine-sided cookie, as the Home hero's. The head of every sheet and dialog uses it
 * (accent colours by default, red for something that deletes).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookieIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size / 2))
    }
}

/** How far a [SheetSection] label is inset: 14 dp in the option sheets, whose cards sit 16 dp from the edge. */
/** The colour of a tile that is off: a dialog (itself `surfaceContainerHigh`) provides `surfaceContainerHighest`. */
val LocalQuietColor = compositionLocalOf<Color?> { null }

val LocalSheetLabelInset = compositionLocalOf { 4.dp }

/**
 * The head of a sheet about one thing with a name (a group, a feed): one card, the Tile corner,
 * with its picture (a cookie or the feed's round icon, [picture], 56 dp), the name, a [fact] line,
 * and the ways to change it: a pencil badge on the picture ([onEditIcon]) and a round pencil
 * ([onRename]) at the end, which are left out where the thing can't be changed.
 */
@Composable
fun IdentityHead(
    picture: @Composable () -> Unit,
    title: String,
    fact: @Composable () -> Unit,
    onEditIcon: (() -> Unit)?,
    onRename: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corner.Tile))
                .background(scheme.surfaceContainerHigh)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                modifier =
                    Modifier.matchParentSize()
                        .clip(CircleShape)
                        .then(
                            if (onEditIcon != null) {
                                Modifier.clickable(onClickLabel = stringResource(R.string.feed_icon_change), onClick = onEditIcon)
                            } else Modifier
                        ),
                contentAlignment = Alignment.Center,
            ) {
                picture()
            }
            if (onEditIcon != null) {
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
                            .offset(2.dp, 2.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .border(2.dp, scheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    if (onRename != null) Modifier.clip(RoundedCornerShape(Corner.Inner)).clickable(onClick = onRename)
                    else Modifier,
            )
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides scheme.onSurfaceVariant
            ) {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium) { fact() }
            }
        }
        if (onRename != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest)
                        .clickable(onClick = onRename),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.rename),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Fades a sheet's scrolling body into the head above and the buttons below while there is more to
 * scroll that way, with the same S-curve as Home's list ([edgeFadeBrush]), never a hard cut.
 */
/** No sheet is taller than 70% of the screen: what is above that scrolls inside the sheet. */
@Composable
fun Modifier.sheetMaxHeight(): Modifier =
    this.then(
        Modifier.heightIn(
            max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.7f).dp
        )
    )

@Composable
fun Modifier.sheetScrollFade(state: ScrollState, height: Dp = 24.dp): Modifier {
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    return drawWithContent {
        drawContent()
        val h = height.toPx()
        if (state.canScrollBackward) {
            drawRect(edgeFadeBrush(color, solidAtBottom = false, height = h), size = Size(size.width, h))
        }
        if (state.canScrollForward) {
            translate(top = size.height - h) {
                drawRect(edgeFadeBrush(color, solidAtBottom = true, height = h), size = Size(size.width, h))
            }
        }
    }
}

/** A sheet's head: a cookie icon (when there is one), the title and an optional muted line. No divider. */
@Composable
fun SheetTitle(title: String, hint: String? = null, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            CookieIcon(icon)
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(18.dp))
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
    /** The colour of a row that isn't chosen; a dialog (itself `surfaceContainerHigh`) passes `surfaceContainerHighest`. */
    restColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val scheme = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) scheme.primaryContainer else restColor, effects, label = "choice")
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
    /** Tapping the number (a number sheet starts typing then). */
    onValueClick: (() -> Unit)? = null,
    /** Shown in the pill in place of the number while it is being typed. */
    editor: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepButton(Icons.Rounded.Remove, downDescription, canDown, onDown)
        val pill = Modifier.weight(1f).height(72.dp)
        val pillContent: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                if (editor != null) editor()
                else AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        val up = targetState > initialState
                        // A plain roll up or down (no scaling about the centre, which with a text that changes
                        // width looked diagonal); the size changes at once and the text is centred.
                        (fadeIn(androidx.compose.animation.core.tween(180)) +
                            slideInVertically(androidx.compose.animation.core.tween(220)) { if (up) it else -it }) togetherWith
                            (fadeOut(androidx.compose.animation.core.tween(120)) +
                                slideOutVertically(androidx.compose.animation.core.tween(220)) { if (up) -it else it }) using
                            androidx.compose.animation.SizeTransform(clip = false) { _, _ -> androidx.compose.animation.core.snap() }
                    },
                    label = "stepper",
                ) {
                    Text(label(it), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        if (onValueClick != null && editor == null) {
            Surface(
                onClick = onValueClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = pill,
                content = pillContent,
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = pill,
                content = pillContent,
            )
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
