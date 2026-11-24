package com.ay.briefyou.ui.page.home.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.ui.component.base.BYTextField2
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * The list's search: the same field as every other text box in the app (a tonal rounded box whose
 * hint rises onto the top edge as a label chip), with a close button at its end. The results follow
 * the typing a moment after the last key, so the list changes once per word, not once per letter.
 */
@OptIn(FlowPreview::class)
@Composable
fun SearchBar(
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val state = rememberTextFieldState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(Unit) {
        snapshotFlow { state.text.toString() }.drop(1).debounce(250).collect { latestOnChange(it) }
    }

    BYTextField2(
        state = state,
        modifier =
            Modifier.background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp, bottom = 10.dp),
        label = placeholder,
        singleLine = true,
        autoFocus = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = KeyboardActionHandler { focusManager.clearFocus() },
        customTrailing = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
