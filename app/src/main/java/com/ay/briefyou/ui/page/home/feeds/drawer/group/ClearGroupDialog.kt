package com.ay.briefyou.ui.page.home.feeds.drawer.group

import com.ay.briefyou.brief.ui.DialogConfirmButton

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ay.briefyou.R
import com.ay.briefyou.ui.component.base.BYDialog
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.showToast

@Composable
fun ClearGroupDialog(
    groupName: String,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val groupOptionUiState = groupOptionViewModel.groupOptionUiState.collectAsStateValue()
    val scope = rememberCoroutineScope()
    val toastString = stringResource(R.string.clear_articles_in_group_toast, groupName)

    BYDialog(
        danger = true, // Brief You: red icon and button
        visible = groupOptionUiState.clearDialogVisible,
        onDismissRequest = {
            groupOptionViewModel.hideClearDialog()
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.clear_articles),
            )
        },
        title = {
            Text(text = stringResource(R.string.clear_articles))
        },
        text = {
            Text(text = stringResource(R.string.clear_articles_group_tips, groupName))
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = {
                    groupOptionViewModel.clear {
                        groupOptionViewModel.hideClearDialog()
                        onConfirm()
                        context.showToast(toastString)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.clear),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    groupOptionViewModel.hideClearDialog()
                }
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        },
    )
}
