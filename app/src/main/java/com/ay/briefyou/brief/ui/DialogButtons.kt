package com.ay.briefyou.brief.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** True inside a dialog that deletes or clears something: its icon and its main button turn red. */
internal val LocalDialogDanger = compositionLocalOf { false }

/**
 * The main button of a dialog: round and filled in the accent colour, or in red when the dialog
 * is a [danger] one (delete, clear). The other button stays a plain text button.
 */
@Composable
fun DialogConfirmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = LocalDialogDanger.current,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors =
            if (danger) ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
            else ButtonDefaults.buttonColors(),
        content = content,
    )
}

/** Marks everything inside as a dialog that deletes something (see [LocalDialogDanger]). */
@Composable
internal fun DangerDialog(danger: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDialogDanger provides danger, content = content)
}
