package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
fun RYDialog(
    modifier: Modifier = Modifier,
    visible: Boolean,
    properties: DialogProperties = DialogProperties(),
    onDismissRequest: () -> Unit = {},
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    if (visible) {
        AlertDialog(
            properties = properties.keyboardSafe(),
            modifier = modifier.imePadding(),
            onDismissRequest = onDismissRequest,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    }
}

/**
 * Dialog settings for dialogs with text fields. The dialog's window stays full screen and still
 * while the keyboard opens (the dialog moves up inside it, use with `Modifier.imePadding()`), so
 * the text cursor's handle shows under the cursor. When the window itself was moved up for the
 * keyboard, the handle was placed where the field used to be, far below it.
 */
fun DialogProperties.keyboardSafe(): DialogProperties =
    DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        securePolicy = securePolicy,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        decorFitsSystemWindows = false,
    )
