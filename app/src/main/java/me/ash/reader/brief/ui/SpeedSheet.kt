package me.ash.reader.brief.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import java.util.Locale

/** A speed as people read it: 0.8×, 1×, 1.2×, 2.5×. Read aloud and the podcast player both use it. */
fun speedLabel(speed: Float): String =
    String.format(Locale.US, "%.1f", speed).trimEnd('0').trimEnd('.') + "\u00D7"

/**
 * The speed sheet for read aloud and the podcast player, in the app's sheet design: title,
 * divider, a slider of [speeds] between its ends, and the speed, large (a new one rolls in). A
 * tick is felt on each step. [onStep] runs on each step, [onDone] when the finger lifts (read
 * aloud applies the speed then, so it doesn't restart the sentence on every step). Brief You.
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            val index = speeds.indexOfFirst { it >= value - 0.01f }.coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(speedLabel(speeds.first()), style = MaterialTheme.typography.labelLarge)
                Slider(
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
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(speedLabel(speeds.last()), style = MaterialTheme.typography.labelLarge)
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val up = targetState > initialState
                    (fadeIn() + scaleIn(initialScale = 0.7f) + slideInVertically { if (up) it else -it }) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.7f) + slideOutVertically { if (up) -it / 2 else it / 2 })
                },
                label = "speed",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(speedLabel(it), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
