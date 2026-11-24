package com.ay.briefyou.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.brief.ui.DialogConfirmButton
import com.ay.briefyou.ui.component.base.BYDialog
import com.ay.briefyou.ui.component.base.BYTextField2

/**
 * Brief You: a feed's name and, where the account allows it ([showUrl]), its address, in one
 * dialog. The app's dialog with filled, rounded fields; Save needs a name (and an address).
 */
@Composable
fun EditFeedDialog(
    visible: Boolean,
    name: String,
    url: String,
    showUrl: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    if (!visible) return
    val focusManager = LocalFocusManager.current
    val nameState = rememberTextFieldState(name)
    val urlState = rememberTextFieldState(url)
    val newName = nameState.text.toString().trim()
    val newUrl = urlState.text.toString().trim()
    val valid = newName.isNotEmpty() && (!showUrl || newUrl.isNotEmpty())
    val save = {
        if (valid) {
            focusManager.clearFocus()
            onConfirm(newName, if (showUrl) newUrl else url)
        }
    }
    BYDialog(
        visible = true,
        onDismissRequest = onDismissRequest,
        icon = { Icon(imageVector = Icons.Outlined.Edit, contentDescription = null) },
        title = { Text(stringResource(R.string.edit_feed)) },
        text = {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                BYTextField2(
                    state = nameState,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.name),
                    placeholder = stringResource(R.string.name),
                    keyboardOptions = KeyboardOptions(imeAction = if (showUrl) ImeAction.Next else ImeAction.Done),
                    onKeyboardAction = if (showUrl) null else KeyboardActionHandler { save() },
                )
                if (showUrl) {
                    Spacer(modifier = Modifier.height(12.dp))
                    BYTextField2(
                        state = urlState,
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.feed_url),
                        placeholder = stringResource(R.string.feed_url_placeholder),
                        autoFocus = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        onKeyboardAction = KeyboardActionHandler { save() },
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        },
        confirmButton = {
            DialogConfirmButton(enabled = valid, onClick = { save() }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) } },
    )
}
