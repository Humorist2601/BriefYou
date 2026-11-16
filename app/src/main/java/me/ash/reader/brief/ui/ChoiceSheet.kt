package me.ash.reader.brief.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One choice of a [ChoiceSheet]. */
class Choice(val text: String, val selected: Boolean, val style: TextStyle? = null, val onClick: () -> Unit)

/**
 * A setting's choices as a sheet, like every other sheet: title (with a muted line when the
 * setting explains itself), divider, and the choices as one joined group, the chosen one filled
 * and ticked. Tapping one applies it and the sheet slides away. Replaces Read You's radio dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(title: String, hint: String?, choices: List<Choice>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(title, hint)
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Group.Gap),
            ) {
                choices.forEachIndexed { i, choice ->
                    ChoiceRow(
                        title = choice.text,
                        selected = choice.selected,
                        titleStyle = choice.style,
                        shape = groupShape(i, choices.size),
                        onClick = {
                            choice.onClick()
                            scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
                        },
                    )
                }
            }
        }
    }
}
