package com.ay.briefyou.brief.ui

import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
 * and ticked. Tapping one applies it and the sheet slides away. Replaces the original radio dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoiceSheet(title: String, hint: String?, choices: List<Choice>, onDismiss: () -> Unit) {
    val state = com.ay.briefyou.brief.ui.rememberFullSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(shape = com.ay.briefyou.brief.ui.SheetShape, onDismissRequest = onDismiss, sheetState = state) {
        Column(modifier = Modifier.sheetMaxHeight().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(title, hint, icon = Icons.Outlined.Checklist)
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier.weight(1f, fill = false).sheetScrollFade(scroll).verticalScroll(scroll),
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

/**
 * A setting's choices as a dialog: its own icon on the cookie, the title, an optional muted line,
 * and the choices as one joined group (the chosen one filled and ticked) that scrolls inside the
 * dialog, fading at its edges, when there are many. A tap applies the choice and closes it.
 */
@Composable
fun ChoiceDialog(
    visible: Boolean,
    title: String,
    hint: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    choices: List<Choice>,
    onDismiss: () -> Unit,
) {
    LingerDialog(visible) {
        InWindowAlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                androidx.compose.material3.Icon(
                    icon ?: Icons.Outlined.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            },
            title = { androidx.compose.material3.Text(title) },
            text = {
                Column {
                    if (hint != null) {
                        androidx.compose.material3.Text(
                            hint,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    val scroll = rememberScrollState()
                    val maxList = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
                    Column(
                        modifier = Modifier.heightIn(max = maxList).sheetScrollFade(scroll).verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(Group.Gap),
                    ) {
                        choices.forEachIndexed { i, choice ->
                            ChoiceRow(
                                title = choice.text,
                                selected = choice.selected,
                                titleStyle = choice.style,
                                shape = groupShape(i, choices.size),
                                restColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                                onClick = {
                                    choice.onClick()
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.ay.briefyou.R.string.cancel))
                }
            },
            confirmButton = {},
        )
    }
}
