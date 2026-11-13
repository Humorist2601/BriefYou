package me.ash.reader.brief.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.ash.reader.R

/**
 * A number setting as a sheet, like the read-aloud speed one: the range's ends, a slider with a
 * tick on every step, and the value below. "Custom value" types a number (it can go past the
 * slider's end); "Reset to …" goes back to the default. The setting is saved as the slider is let
 * go, and by Reset and Done.
 *
 * [suffix] follows the number (" dp", "×"); [decimals] is how many digits the number shows. A
 * setting that follows another by default passes [onReset] (and its own [resetLabel]); the sheet
 * then closes after Reset.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NumberSheet(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    default: Float,
    suffix: String = "",
    decimals: Int = 0,
    resetLabel: String = format(default, decimals, suffix),
    onReset: (() -> Unit)? = null,
    onSave: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(typing) { if (typing) focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding()
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(format(range.start, decimals, ""), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = current.coerceIn(range.start, range.endInclusive),
                    onValueChange = {
                        val snapped = snap(it, range.start, step)
                        if (snapped != current) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            current = snapped
                        }
                    },
                    onValueChangeFinished = { onSave(current) },
                    steps = ((range.endInclusive - range.start) / step).roundToInt() - 1,
                    valueRange = range,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(format(range.endInclusive, decimals, ""), style = MaterialTheme.typography.labelLarge)
            }
            if (!typing) {
                Text(
                    format(current, decimals, suffix),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            text = format(current, decimals, "")
                            typing = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.number_custom), maxLines = 1)
                    }
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            if (onReset != null) {
                                onReset()
                                onDismiss()
                            } else {
                                current = default
                                onSave(default)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.number_reset, resetLabel), maxLines = 1)
                    }
                }
            } else {
                val typed = text.toFloatOrNull()
                TextField(
                    value = text,
                    onValueChange = { new ->
                        text = new.filter { it.isDigit() || (decimals > 0 && (it == '.' || it == ',')) }.replace(',', '.')
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                    suffix = { Text(suffix.trim()) },
                    keyboardOptions =
                        KeyboardOptions(keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number),
                    modifier = Modifier.align(Alignment.CenterHorizontally).focusRequester(focus),
                )
                Text(
                    if (typed != null && (typed < range.start || typed > range.endInclusive))
                        stringResource(
                            R.string.number_past_slider,
                            format(range.start, decimals, ""),
                            format(range.endInclusive, decimals, ""),
                        )
                    else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { typing = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.cancel), maxLines = 1)
                    }
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        enabled = typed != null,
                        onClick = {
                            typed?.let {
                                current = it
                                onSave(it)
                            }
                            typing = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.number_done), maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun snap(v: Float, start: Float, step: Float): Float =
    (((v - start) / step).roundToInt() * step + start).let { (it * 1000).roundToInt() / 1000f }

private fun format(v: Float, decimals: Int, suffix: String): String =
    (if (decimals == 0) v.roundToInt().toString() else "%.${decimals}f".format(java.util.Locale.US, v)) + suffix
