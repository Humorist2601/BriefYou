package com.ay.briefyou.ui.component.base

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioDialog(
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    title: String = "",
    description: String? = null,
    /** The setting's own icon, on the dialog's cookie (see `SettingIcons`). */
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    options: List<RadioDialogOption> = emptyList(),
    onDismissRequest: () -> Unit = {},
) {
    // Brief You: a setting's choices are a dialog (a short list you pick from and are done): the
    // setting's icon, its title, and the choices as one joined group, the chosen one ticked. Every
    // caller keeps working as before.
    com.ay.briefyou.brief.ui.ChoiceDialog(
        visible = visible,
        title = title,
        hint = description,
        icon = icon,
        choices = options.map { com.ay.briefyou.brief.ui.Choice(it.text, it.selected, it.style, it.onClick) },
        onDismiss = onDismissRequest,
    )
}

@Immutable
data class RadioDialogOption(
    val text: String = "",
    val style: TextStyle? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)
