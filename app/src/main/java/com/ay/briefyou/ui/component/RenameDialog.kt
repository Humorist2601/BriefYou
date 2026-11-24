package com.ay.briefyou.ui.component

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import com.ay.briefyou.R
import com.ay.briefyou.ui.component.base.TextFieldDialog

@Composable
fun RenameDialog(
    visible: Boolean = false,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
) {
    com.ay.briefyou.brief.ui.LingerDialog(visible) {
        val textFieldState = rememberTextFieldState(value)
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.text }.collect { onValueChange(it.toString()) }
        }
        TextFieldDialog(
            textFieldState = textFieldState,
            title = stringResource(R.string.rename),
            icon = Icons.Outlined.Edit,
            placeholder = stringResource(R.string.name),
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
        )
    }
}
