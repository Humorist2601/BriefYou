package me.ash.reader.ui.component.base

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
    options: List<RadioDialogOption> = emptyList(),
    onDismissRequest: () -> Unit = {},
) {
    // Brief You: the choices as a sheet in the app's sheet design (a joined group, the chosen one
    // filled and ticked) instead of a radio list in a dialog. Every caller keeps working as before.
    if (!visible) return // Brief You
    me.ash.reader.brief.ui.ChoiceSheet( // Brief You
        title = title, // Brief You
        hint = description, // Brief You
        choices = options.map { me.ash.reader.brief.ui.Choice(it.text, it.selected, it.style, it.onClick) }, // Brief You
        onDismiss = onDismissRequest, // Brief You
    ) // Brief You
}

@Immutable
data class RadioDialogOption(
    val text: String = "",
    val style: TextStyle? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)
