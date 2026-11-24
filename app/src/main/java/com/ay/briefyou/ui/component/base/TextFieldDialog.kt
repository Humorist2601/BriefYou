package com.ay.briefyou.ui.component.base

import com.ay.briefyou.brief.ui.DialogConfirmButton

import com.ay.briefyou.brief.ui.InWindowAlertDialog // Brief You

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import com.ay.briefyou.R

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    visible: Boolean,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    title: String = "",
    icon: ImageVector? = null,
    value: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    errorText: String = "",
    dismissText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onValueChange: (String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
) {
    val focusManager = LocalFocusManager.current
    val textFieldState = rememberTextFieldState(value)

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text }.collect { onValueChange(it.toString()) }
    }

    BYDialog(
        modifier = modifier,
        properties = properties,
        visible = visible,
        onDismissRequest = onDismissRequest,
        // Brief You: no icon slot at all without an icon (an empty one would draw an empty cookie)
        icon = icon?.let { vector -> { Icon(imageVector = vector, contentDescription = title) } },
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            ClipboardTextField(
                state = textFieldState,
                modifier = modifier.fillMaxWidth(),
                readOnly = readOnly,
                singleLine = singleLine,
                placeholder = placeholder,
                isPassword = isPassword,
                errorText = errorText,
                imeAction = imeAction,
                onConfirm = onConfirm,
            )
        },
        confirmButton = {
            DialogConfirmButton(
                enabled = value.isNotBlank(),
                onClick = {
                    focusManager.clearFocus()
                    onConfirm(value)
                },
            ) {
                Text(
                    text = confirmText,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(text = dismissText) } },
    )
}

@Composable
fun TextFieldDialog(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    visible: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    title: String = "",
    icon: ImageVector? = null,
    placeholder: String = "",
    isPassword: Boolean = false,
    errorText: String = "",
    dismissText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onDismissRequest: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
) {
    BYDialog(
        modifier = modifier,
        properties = properties,
        visible = visible,
        onDismissRequest = onDismissRequest,
        // Brief You: no icon slot at all without an icon (an empty one would draw an empty cookie)
        icon = icon?.let { vector -> { Icon(imageVector = vector, contentDescription = title) } },
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            ClipboardTextField(
                state = textFieldState,
                modifier = modifier.fillMaxWidth(),
                readOnly = readOnly,
                singleLine = singleLine,
                placeholder = placeholder,
                isPassword = isPassword,
                errorText = errorText,
                imeAction = imeAction,
                onConfirm = onConfirm,
            )
        },
        confirmButton = {
            DialogConfirmButton(
                enabled = textFieldState.text.isNotBlank(),
                onClick = {
                    onConfirm(textFieldState.text.toString())
                },
            ) {
                Text(
                    text = confirmText,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(text = dismissText) } },
    )
}

@Composable
fun TextFieldDialog(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    title: String = "",
    icon: ImageVector? = null,
    placeholder: String = "",
    isPassword: Boolean = false,
    errorText: String = "",
    dismissText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onDismissRequest: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
) {
    val focusManager = LocalFocusManager.current

    InWindowAlertDialog( // Brief You
        modifier = modifier,
        properties = properties,
        onDismissRequest = onDismissRequest,
        // Brief You: no icon slot at all without an icon (an empty one would draw an empty cookie)
        icon = icon?.let { vector -> { Icon(imageVector = vector, contentDescription = title) } },
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            ClipboardTextField(
                state = textFieldState,
                modifier = modifier.fillMaxWidth(),
                readOnly = readOnly,
                singleLine = singleLine,
                placeholder = placeholder,
                isPassword = isPassword,
                errorText = errorText,
                imeAction = imeAction,
                onConfirm = onConfirm,
            )
        },
        confirmButton = {
            DialogConfirmButton(
                enabled = textFieldState.text.isNotBlank(),
                onClick = {
                    focusManager.clearFocus()
                    onConfirm(textFieldState.text.toString())
                },
            ) {
                Text(
                    text = confirmText,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(text = dismissText) } },
    )
}
