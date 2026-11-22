package com.ay.briefyou.brief.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.ay.briefyou.R

/**
 * A number setting as a sheet, like the speed one: the value large with − and + to step it, a
 * slider with a stop on every step and the range's ends under it. "Custom value" types a number (it can go past the
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
    // What is typed, with its cursor: it starts all selected, so the first digit replaces it, and
    // the cursor stays where you put it (a plain String put it back at the start).
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    val text = field.text
    fun setText(new: String) {
        field = androidx.compose.ui.text.input.TextFieldValue(new, androidx.compose.ui.text.TextRange(new.length))
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    fun stopTyping() {
        keyboard?.hide()
        focusManager.clearFocus()
        typing = false
    }
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(typing) { if (typing) focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = com.ay.briefyou.brief.ui.rememberFullSheetState(),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding()
        ) {
            SheetTitle(title)
            val steps = ((range.endInclusive - range.start) / step).roundToInt()
            val typed = text.toFloatOrNull()
            fun startTyping() {
                val now = format(current, decimals, "")
                field = androidx.compose.ui.text.input.TextFieldValue(now, androidx.compose.ui.text.TextRange(0, now.length))
                typing = true
            }
            // Typing goes into the big number itself; − and + then step the typed number.
            ValueStepper(
                value = if (typing) typed ?: current else current,
                label = { format(it, decimals, suffix) },
                canDown = if (typing) typed != null && typed - step >= 0f else current > range.start + step / 2,
                canUp = if (typing) typed != null else current < range.endInclusive - step / 2,
                onDown = {
                    if (typing) typed?.let { setText(format((it - step).coerceAtLeast(0f), decimals, "")) }
                    else {
                        current = snap((current - step).coerceIn(range.start, range.endInclusive), range.start, step)
                        onSave(current)
                    }
                },
                onUp = {
                    if (typing) typed?.let { setText(format(it + step, decimals, "")) }
                    else {
                        current = snap((current + step).coerceIn(range.start, range.endInclusive), range.start, step)
                        onSave(current)
                    }
                },
                downDescription = stringResource(R.string.sheet_step_down),
                upDescription = stringResource(R.string.sheet_step_up),
                onValueClick = { if (!typing) startTyping() },
                editor =
                    if (!typing) null
                    else {
                        {
                            NumberEditor(
                                field = field,
                                suffix = suffix,
                                decimals = decimals,
                                focus = focus,
                                onField = { new ->
                                    val clean = new.text.filter { it.isDigit() || (decimals > 0 && (it == '.' || it == ',')) }.replace(',', '.')
                                    field = if (clean == new.text) new else androidx.compose.ui.text.input.TextFieldValue(clean, androidx.compose.ui.text.TextRange(clean.length))
                                },
                                onDone = {
                                    text.toFloatOrNull()?.let { t ->
                                        current = t
                                        onSave(t)
                                        stopTyping()
                                    }
                                },
                            )
                        }
                    },
            )
            if (!typing) {
                Spacer(Modifier.height(16.dp))
                BriefSlider(
                    value = current.coerceIn(range.start, range.endInclusive),
                    onValueChange = {
                        val snapped = snap(it, range.start, step)
                        if (snapped != current) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            current = snapped
                        }
                    },
                    onValueChangeFinished = { onSave(current) },
                    // A stop on every step only while they can still be told apart.
                    steps = if (steps <= 40) steps - 1 else 0,
                    valueRange = range,
                )
                Row {
                    Text(format(range.start, decimals, ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(format(range.endInclusive, decimals, ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                val custom = stringResource(R.string.number_custom)
                val reset = stringResource(R.string.number_reset, resetLabel)
                JoinedChoices(
                    count = 2,
                    selected = -1,
                    onPick = {
                        if (it == 0) startTyping()
                        else if (onReset != null) {
                            onReset()
                            onDismiss()
                        } else {
                            current = default
                            onSave(default)
                        }
                    },
                ) { i, on ->
                    JoinedLabel(if (i == 0) custom else reset, on)
                }
            } else {
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
                val cancel = stringResource(R.string.cancel)
                val done = stringResource(R.string.number_done)
                JoinedChoices(
                    count = 2,
                    // Done is lit while the number can be saved.
                    selected = if (typed != null) 1 else -1,
                    onPick = {
                        if (it == 1) {
                            typed?.let { t ->
                                current = t
                                onSave(t)
                            }
                        }
                        if (it == 0 || typed != null) stopTyping()
                    },
                ) { i, on ->
                    JoinedLabel(if (i == 0) cancel else done, on)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The number typed straight into the stepper's pill, with the cursor, the keyboard and the unit after it. */
@Composable
private fun NumberEditor(
    field: androidx.compose.ui.text.input.TextFieldValue,
    suffix: String,
    decimals: Int,
    focus: FocusRequester,
    onField: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onDone: () -> Unit,
) {
    val style =
        MaterialTheme.typography.displaySmall.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    // The whole pill is the field (centred), with the unit drawn after the number but not typed:
    // a field sized to its text scrolled its digits out of sight as they were typed.
    val unit =
        androidx.compose.ui.text.input.VisualTransformation { typed ->
            androidx.compose.ui.text.input.TransformedText(
                androidx.compose.ui.text.AnnotatedString(typed.text + suffix),
                object : androidx.compose.ui.text.input.OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = offset
                    override fun transformedToOriginal(offset: Int): Int = minOf(offset, typed.text.length)
                },
            )
        }
    androidx.compose.foundation.text.BasicTextField(
        value = field,
        onValueChange = onField,
        singleLine = true,
        textStyle = style,
        visualTransformation = unit,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onPrimaryContainer),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
        // The keyboard's tick saves, like Done.
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
    )
}

private fun snap(v: Float, start: Float, step: Float): Float =
    (((v - start) / step).roundToInt() * step + start).let { (it * 1000).roundToInt() / 1000f }

private fun format(v: Float, decimals: Int, suffix: String): String =
    (if (decimals == 0) v.roundToInt().toString() else "%.${decimals}f".format(java.util.Locale.US, v)) + suffix
