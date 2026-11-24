package com.ay.briefyou.ui.page.home.feeds.drawer.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ay.briefyou.R
import com.ay.briefyou.brief.ui.ChoiceRow
import com.ay.briefyou.brief.ui.DialogConfirmButton
import com.ay.briefyou.brief.ui.nestedGroupShape
import com.ay.briefyou.ui.component.base.BYDialog
import com.ay.briefyou.ui.ext.collectAsStateValue

/**
 * "Apply to all feeds in this group" for one setting: the app's dialog (a cookie icon, a round
 * main button) with Disable and Enable as two joined cards, Enable explaining itself.
 */
@Composable
private fun ApplyToAllDialog(
    visible: Boolean,
    icon: ImageVector,
    title: String,
    groupName: String,
    enableDescription: String,
    onDismiss: () -> Unit,
    onApply: (Boolean) -> Unit,
) {
    if (!visible) return
    var enabled by remember { mutableStateOf(true) }
    BYDialog(
        visible = true,
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.group_configuration_description, title, groupName))
                Spacer(modifier = Modifier.height(16.dp))
                ChoiceRow(
                    title = stringResource(R.string.disable),
                    selected = !enabled,
                    shape = nestedGroupShape(0, 2),
                    onClick = { enabled = false },
                    restColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(modifier = Modifier.height(3.dp))
                ChoiceRow(
                    title = stringResource(R.string.enable),
                    subtitle = enableDescription,
                    selected = enabled,
                    shape = nestedGroupShape(1, 2),
                    onClick = { enabled = true },
                    restColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(onClick = { onApply(enabled) }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AllAllowNotificationDialog(
    groupName: String,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onConfirm: () -> Unit,
) {
    val groupOptionUiState = groupOptionViewModel.groupOptionUiState.collectAsStateValue()
    ApplyToAllDialog(
        visible = groupOptionUiState.allAllowNotificationDialogVisible,
        icon = Icons.Outlined.Notifications,
        title = stringResource(R.string.notifications),
        groupName = groupName,
        enableDescription = stringResource(R.string.notifications_desc),
        onDismiss = { groupOptionViewModel.hideAllAllowNotificationDialog() },
        onApply = { enabled ->
            groupOptionViewModel.allAllowNotification(enabled) {
                groupOptionViewModel.hideAllAllowNotificationDialog()
                onConfirm()
            }
        },
    )
}

@Composable
fun AllOpenInBrowserDialog(
    groupName: String,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onConfirm: () -> Unit,
) {
    val groupOptionUiState = groupOptionViewModel.groupOptionUiState.collectAsStateValue()
    ApplyToAllDialog(
        visible = groupOptionUiState.allOpenInBrowserDialogVisible,
        icon = Icons.Outlined.OpenInBrowser,
        title = stringResource(R.string.open_in_browser),
        groupName = groupName,
        enableDescription = stringResource(R.string.open_in_browser_desc),
        onDismiss = { groupOptionViewModel.hideAllOpenInBrowserDialog() },
        onApply = { enabled ->
            groupOptionViewModel.allOpenInBrowser(enabled) {
                groupOptionViewModel.hideAllOpenInBrowserDialog()
                onConfirm()
            }
        },
    )
}

@Composable
fun AllParseFullContentDialog(
    groupName: String,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onConfirm: () -> Unit,
) {
    val groupOptionUiState = groupOptionViewModel.groupOptionUiState.collectAsStateValue()
    ApplyToAllDialog(
        visible = groupOptionUiState.allParseFullContentDialogVisible,
        icon = Icons.AutoMirrored.Outlined.Article,
        title = stringResource(R.string.parse_full_content),
        groupName = groupName,
        enableDescription = stringResource(R.string.parse_full_content_desc),
        onDismiss = { groupOptionViewModel.hideAllParseFullContentDialog() },
        onApply = { enabled ->
            groupOptionViewModel.allParseFullContent(enabled) {
                groupOptionViewModel.hideAllParseFullContentDialog()
                onConfirm()
            }
        },
    )
}
