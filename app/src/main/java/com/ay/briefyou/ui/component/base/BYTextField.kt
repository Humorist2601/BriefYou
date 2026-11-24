package com.ay.briefyou.ui.component.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.room.util.copy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ay.briefyou.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BYOutlineTextField(
    modifier: Modifier = Modifier,
    requestFocus: Boolean = true,
    readOnly: Boolean = false,
    value: String,
    label: String = "",
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    placeholder: String = "",
    errorMessage: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val keyboardOptions =
        keyboardOptions.let {
            if (it.imeAction == ImeAction.Unspecified) it.copy(imeAction = ImeAction.Next) else it
        }

    val state = rememberTextFieldState(value)
    val textFlow = snapshotFlow { state.text }

    LaunchedEffect(textFlow) { textFlow.collect { onValueChange(it.toString()) } }

    BYOutlinedTextField2(
        state = state,
        modifier = modifier,
        readOnly = readOnly,
        label = label,
        singleLine = singleLine,
        isPassword = isPassword,
        placeholder = placeholder,
        errorMessage = errorMessage,
        autoFocus = requestFocus,
        keyboardOptions = keyboardOptions,
    )
}

@Composable
fun BYTextField2(
    value: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: String = "",
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    placeholder: String = "",
    errorMessage: String = "",
    autoFocus: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {

    val keyboardOptions =
        keyboardOptions.let {
            if (it.imeAction == ImeAction.Unspecified) it.copy(imeAction = ImeAction.Next) else it
        }

    val state = rememberTextFieldState(value)
    val textFlow = snapshotFlow { state.text }

    LaunchedEffect(textFlow) { textFlow.collect { onValueChange(it.toString()) } }

    BYTextField2(
        state = state,
        modifier = modifier,
        readOnly = readOnly,
        label = label,
        singleLine = singleLine,
        isPassword = isPassword,
        placeholder = placeholder,
        errorMessage = errorMessage,
        autoFocus = autoFocus,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
    )
}

@Composable
fun BYTextField2(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: String = "",
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    placeholder: String = "",
    errorMessage: String = "",
    autoFocus: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    val focusRequester = remember { FocusRequester() }

    val keyboardOptions =
        keyboardOptions.let {
            if (it.imeAction == ImeAction.Unspecified) it.copy(imeAction = ImeAction.Next) else it
        }

    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(100) // ???
            focusRequester.requestFocus()
        }
    }

    var showPassword by remember { mutableStateOf(false) }
    ChipLabelField(
        state = state,
        // The hint is the label: faint in the empty field, a chip on its edge once you type.
        label = label.ifEmpty { placeholder },
        placeholder = if (label.isEmpty()) "" else placeholder,
        modifier = modifier,
        focusRequester = focusRequester,
        readOnly = readOnly,
        singleLine = singleLine,
        isPassword = isPassword,
        showPassword = showPassword,
        errorMessage = errorMessage,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        trailing = {
            if (isPassword) {
                PasswordVisibilityButton(showPassword = showPassword, onValueChange = { showPassword = it })
            } else if (state.text.isNotEmpty()) {
                ClearButton(state)
            } else {
                PasteButton(state)
            }
        },
    )
}

/**
 * Brief You's text field (Material 3 Expressive): a tonal rounded box with an outline. The label is
 * the faint hint in the empty box; when you focus it or type, it rises onto the top edge as a small
 * chip (purple while focused, red on an error, quiet otherwise). The outline thickens and takes the
 * accent colour, with springs; an error message sits under the box.
 */
@Composable
private fun ChipLabelField(
    state: TextFieldState,
    label: String,
    placeholder: String,
    modifier: Modifier,
    focusRequester: FocusRequester,
    readOnly: Boolean,
    singleLine: Boolean,
    isPassword: Boolean,
    showPassword: Boolean,
    errorMessage: String,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    trailing: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val error = errorMessage.isNotEmpty()
    val floated = focused || state.text.isNotEmpty()
    val rise by animateFloatAsState(
        if (floated) 1f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "fieldLabel",
    )
    val borderColor by animateColorAsState(
        when {
            error -> scheme.error
            focused -> scheme.primary
            else -> scheme.outline
        },
        label = "fieldBorder",
    )
    val borderWidth by animateDpAsState(if (focused || error) 2.5.dp else 1.dp, label = "fieldBorderWidth")
    val chipBg by animateColorAsState(
        when {
            error -> scheme.error
            focused -> scheme.primary
            else -> scheme.surfaceContainerHighest
        },
        label = "fieldChip",
    )
    val chipFg by animateColorAsState(
        when {
            error -> scheme.onError
            focused -> scheme.onPrimary
            else -> scheme.onSurfaceVariant
        },
        label = "fieldChipText",
    )
    var boxHeight by remember { mutableIntStateOf(0) }
    var labelHeight by remember { mutableIntStateOf(0) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface)

    Column(modifier.padding(top = 8.dp)) {
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 60.dp)
                .onSizeChanged { boxHeight = it.height }
                .background(scheme.surfaceContainerHighest, shape)
                .border(borderWidth, borderColor, shape)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 18.dp)) {
                    if (placeholder.isNotEmpty() && focused && state.text.isEmpty()) {
                        Text(placeholder, style = textStyle, color = scheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    val fieldModifier =
                        Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }
                    if (isPassword) {
                        BasicSecureTextField(
                            state = state,
                            modifier = fieldModifier,
                            readOnly = readOnly,
                            textStyle = textStyle,
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = keyboardOptions,
                            onKeyboardAction = onKeyboardAction,
                            textObfuscationMode =
                                if (showPassword) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                        )
                    } else {
                        BasicTextField(
                            state = state,
                            modifier = fieldModifier,
                            readOnly = readOnly,
                            textStyle = textStyle,
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = keyboardOptions,
                            onKeyboardAction = onKeyboardAction,
                            lineLimits =
                                if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(),
                        )
                    }
                }
                trailing?.invoke()
            }
            // The label: a hint in the box until it rises onto the edge as a chip.
            Box(
                Modifier.align(Alignment.TopStart)
                    .graphicsLayer {
                        val restY = (boxHeight - labelHeight) / 2f
                        val upY = -labelHeight * 0.75f / 2f
                        translationX = lerp(6.dp.toPx(), 14.dp.toPx(), rise)
                        translationY = lerp(restY, upY, rise)
                        scaleX = lerp(1f, 0.75f, rise)
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .onSizeChanged { labelHeight = it.height }
                    .drawBehind {
                        drawRoundRect(
                            chipBg.copy(alpha = chipBg.alpha * rise),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = lerpColor(scheme.onSurfaceVariant, chipFg, rise),
                    maxLines = 1,
                )
            }
        }
        if (error) {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun BYOutlinedTextField2(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: String = "",
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    placeholder: String = "",
    errorMessage: String = "",
    autoFocus: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    // Brief You: one field style everywhere (outlined, a rounded rectangle)
    BYTextField2(
        state = state,
        modifier = modifier,
        readOnly = readOnly,
        label = label,
        singleLine = singleLine,
        isPassword = isPassword,
        placeholder = placeholder,
        errorMessage = errorMessage,
        autoFocus = autoFocus,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
    )
}

/** A field that shows a value and is changed by its button (a client certificate), in the same style as the others. */
@Composable
fun BYReadOnlyField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val state = rememberTextFieldState(value)
    LaunchedEffect(value) { if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value) }
    ChipLabelField(
        state = state,
        label = label,
        placeholder = "",
        modifier = modifier,
        focusRequester = remember { FocusRequester() },
        readOnly = true,
        singleLine = true,
        isPassword = false,
        showPassword = false,
        errorMessage = "",
        keyboardOptions = KeyboardOptions.Default,
        onKeyboardAction = null,
        trailing = trailingIcon,
    )
}

@Composable
fun textFieldColors(): TextFieldColors =
    TextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
    )

@Composable
private fun PasteButton(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            scope.launch {
                val clipboardText = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text
                if (!clipboardText.isNullOrBlank()) {
                    state.setTextAndPlaceCursorAtEnd(clipboardText.toString())
                }
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentPaste,
            contentDescription = stringResource(R.string.paste),
            tint = tint,
        )
    }
}

@Composable
fun ClearButton(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(onClick = { state.clearText() }, modifier = modifier) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.clear),
            tint = tint,
        )
    }
}

@Composable
fun PasswordVisibilityButton(
    showPassword: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(onClick = { onValueChange(!showPassword) }, modifier = modifier) {
        Icon(
            imageVector =
                if (showPassword) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = if (showPassword) "Show password" else "Hide password",
            tint = tint,
        )
    }
}
