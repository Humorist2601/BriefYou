package com.ay.briefyou.ui.page.home.feeds.accounts

import com.ay.briefyou.brief.ui.sheetScrollFade
import com.ay.briefyou.brief.ui.sheetMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.account.Account
import kotlinx.coroutines.launch

/**
 * The accounts as a sheet like the app's others: icon head, the accounts as one joined group (the
 * current one filled and ticked; tapping it opens its settings), and Add account / Manage pinned below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsTab(
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    currentAccountId: Int?,
    accounts: List<Account>,
    onAccountSwitch: (Account) -> Unit = {},
    onClickSettings: () -> Unit = {},
    onClickManage: () -> Unit = {},
    onDismissRequest: () -> Unit = {},
) {
    if (!visible) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = com.ay.briefyou.brief.ui.rememberFullSheetState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Slide down first, like a swipe does, then act.
    fun close(then: () -> Unit) {
        scope.launch { state.hide() }.invokeOnCompletion { then() }
    }
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = state,
        shape = com.ay.briefyou.brief.ui.SheetShape,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets.navigationBars },
    ) {
        Column(modifier = Modifier.sheetMaxHeight().padding(horizontal = 16.dp)) {
            com.ay.briefyou.brief.ui.SheetTitle(
                stringResource(R.string.accounts),
                stringResource(R.string.accounts_hint),
                icon = Icons.Outlined.People,
            )
            Spacer(Modifier.height(8.dp))
            val listScroll = rememberScrollState()
            Column(
                modifier = Modifier.weight(1f, fill = false).sheetScrollFade(listScroll).verticalScroll(listScroll),
                verticalArrangement = Arrangement.spacedBy(com.ay.briefyou.brief.ui.Group.Gap),
            ) {
                accounts.forEachIndexed { i, account ->
                    val selected = account.id == currentAccountId
                    com.ay.briefyou.brief.ui.ChoiceRow(
                        title = account.name,
                        subtitle = account.type.toDesc(context),
                        selected = selected,
                        shape = com.ay.briefyou.brief.ui.groupShape(i, accounts.size),
                        onClick = { if (selected) close(onClickSettings) else onAccountSwitch(account) },
                        leading = {
                            IconContainer(selected = selected, size = 40.dp) {
                                AccountIcon(account = account, selected = selected)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { close(onClickManage) },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.accounts_add))
                }
                FilledTonalButton(
                    onClick = { close(onClickSettings) },
                    shape = CircleShape,
                    enabled = currentAccountId != null,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Outlined.Settings, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.accounts_manage))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IconContainer(
    modifier: Modifier = Modifier,
    selected: Boolean,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryFixed
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AccountIcon(account: Account, selected: Boolean) {
    val icon = account.type.toIcon().takeIf { it is ImageVector }?.let { it as ImageVector }
    val iconPainter = account.type.toIcon().takeIf { it is Painter }?.let { it as Painter }
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryFixed
        else MaterialTheme.colorScheme.onSurfaceVariant

    if (icon != null) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = icon,
            contentDescription = account.name,
            tint = contentColor,
        )
    } else {
        iconPainter?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = it,
                contentDescription = account.name,
                tint = contentColor,
            )
        }
    }
}
