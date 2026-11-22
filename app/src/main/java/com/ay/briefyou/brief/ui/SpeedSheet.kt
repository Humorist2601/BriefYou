package com.ay.briefyou.brief.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import java.util.Locale

/** A speed as people read it: 0.8×, 1×, 1.2×, 2.5×. Read aloud and the podcast player both use it. */
fun speedLabel(speed: Float): String =
    String.format(Locale.US, "%.1f", speed).trimEnd('0').trimEnd('.') + "\u00D7"

/**
 * The speed sheet for read aloud and the podcast player, in the app's sheet design: title,
 * divider, the speed large in a pill with − and + around it, a slider of [speeds] between its
 * ends, and a joined row of quick picks. A tick is felt on each step. [onStep] runs on each step,
 * [onDone] when the finger lifts or a button is tapped (read aloud applies the speed then, so it
 * doesn't restart the sentence on every step). Brief You.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    title: String,
    speeds: List<Float>,
    speed: Float,
    onDismiss: () -> Unit,
    onStep: (Float) -> Unit = {},
    onDone: (Float) -> Unit = {},
) {
    var value by remember(speed) { mutableFloatStateOf(speed) }
    val haptic = LocalHapticFeedback.current
    val index = speeds.indexOfFirst { it >= value - 0.01f }.coerceAtLeast(0)
    fun pick(new: Float) {
        if (new == value) return
        value = new
        onStep(new)
        onDone(new)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = com.ay.briefyou.brief.ui.rememberFullSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(title)
            ValueStepper(
                value = value,
                label = ::speedLabel,
                canDown = index > 0,
                canUp = index < speeds.lastIndex,
                onDown = { pick(speeds[(index - 1).coerceAtLeast(0)]) },
                onUp = { pick(speeds[(index + 1).coerceAtMost(speeds.lastIndex)]) },
                downDescription = stringResource(R.string.sheet_step_down),
                upDescription = stringResource(R.string.sheet_step_up),
            )
            Spacer(Modifier.height(16.dp))
            BriefSlider(
                value = index.toFloat(),
                onValueChange = {
                    val step = speeds[Math.round(it).coerceIn(0, speeds.lastIndex)]
                    if (step != value) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        value = step
                        onStep(step)
                    }
                },
                onValueChangeFinished = { onDone(value) },
                valueRange = 0f..speeds.lastIndex.toFloat(),
                steps = speeds.size - 2,
            )
            Row {
                Text(speedLabel(speeds.first()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(speedLabel(speeds.last()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val picks = remember(speeds) { QUICK_PICKS.filter { p -> speeds.any { kotlin.math.abs(it - p) < 0.01f } } }
            if (picks.size > 1) {
                Spacer(Modifier.height(16.dp))
                SheetSection(stringResource(R.string.speed_quick_picks))
                JoinedChoices(
                    count = picks.size,
                    selected = picks.indexOfFirst { kotlin.math.abs(it - value) < 0.01f },
                    onPick = { pick(picks[it]) },
                ) { i, on ->
                    JoinedLabel(speedLabel(picks[i]), on)
                }
            }
        }
    }
}

/** The speeds people use most, offered as one tap (those the list has). */
private val QUICK_PICKS = listOf(1f, 1.2f, 1.4f, 2f, 3f)
