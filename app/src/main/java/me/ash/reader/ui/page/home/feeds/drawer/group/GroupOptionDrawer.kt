package me.ash.reader.ui.page.home.feeds.drawer.group

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.DeleteSweep // Brief You
import androidx.compose.material.icons.outlined.RemoveCircleOutline // Brief You
import androidx.compose.ui.draw.clip // Brief You
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue // Brief You
import androidx.compose.runtime.setValue // Brief You
import androidx.compose.runtime.mutableStateOf // Brief You
import androidx.compose.runtime.remember // Brief You
import androidx.compose.foundation.clickable // Brief You
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.group.Group
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.BottomDrawer
import me.ash.reader.ui.component.base.RYSelectionChip
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.*
import me.ash.reader.ui.interaction.alphaIndicationClickable

@Composable
fun GroupOptionDrawer(
    drawerState: me.ash.reader.brief.ui.SheetSwitch = me.ash.reader.brief.ui.rememberSheetSwitch(), // Brief You: Material 3 sheet
    viewModel: GroupOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupOptionUiState = viewModel.groupOptionUiState.collectAsStateValue()
    val group = groupOptionUiState.group
    val toastString = stringResource(R.string.rename_toast, groupOptionUiState.newName)
    // Brief You: the group's icon, chosen like a feed's (a Material icon, an image, or back to the folder).
    var showIconDialog by remember { mutableStateOf(false) } // Brief You
    val iconScope = rememberCoroutineScope() // Brief You
    val imagePicker = // Brief You
        androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> // Brief You
            val id = group?.id // Brief You
            if (uri != null && id != null) iconScope.launch { // Brief You
                runCatching { me.ash.reader.brief.feeds.GroupIcons.setImage(context, id, uri) }.onFailure { context.showToast(it.message) } // Brief You
            } // Brief You
        } // Brief You
    if (showIconDialog) me.ash.reader.brief.ui.OptionSheetDialogs { // Brief You: above the sheet
        me.ash.reader.brief.feeds.FeedIconDialog( // Brief You
            onSymbol = { n -> group?.id?.let { me.ash.reader.brief.feeds.GroupIcons.setSymbol(context, it, n) } }, // Brief You: Material icons
            current = group?.id?.let { me.ash.reader.brief.feeds.GroupIcons.icons[it] }, // Brief You
            onImage = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, // Brief You
            onWebsiteIcon = { group?.id?.let { me.ash.reader.brief.feeds.GroupIcons.clear(context, it) } }, // Brief You
            onDismiss = { showIconDialog = false }, // Brief You
            resetLabel = stringResource(R.string.group_icon_folder), // Brief You
            resetIcon = Icons.Outlined.Folder, // Brief You
        ) // Brief You
    } // Brief You

    // Brief You: the app's Material 3 sheet (was Read You's Material 2 one), in the app's sheet design.
    me.ash.reader.brief.ui.OptionSheet(drawerState) { // Brief You
        me.ash.reader.brief.ui.SheetHeader( // Brief You
            icon = { me.ash.reader.brief.feeds.GroupIconTile(group?.id.orEmpty(), Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).clickable { showIconDialog = true }, size = 48.dp) }, // Brief You: tap to change
            title = { // Brief You: tap the name to rename
                Text( // Brief You
                    modifier = Modifier.alphaIndicationClickable { if (viewModel.rssService.get().updateSubscription) viewModel.showRenameDialog() }, // Brief You
                    text = group?.name ?: stringResource(R.string.unknown), // Brief You
                    style = MaterialTheme.typography.titleLarge, // Brief You
                    color = MaterialTheme.colorScheme.onSurface, // Brief You
                    maxLines = 1, // Brief You
                    overflow = TextOverflow.Ellipsis, // Brief You
                ) // Brief You
            }, // Brief You
            subtitle = { // Brief You
                Text( // Brief You
                    text = stringResource(R.string.group_option_tips), // Brief You
                    style = MaterialTheme.typography.bodyMedium, // Brief You
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Brief You
                ) // Brief You
            }, // Brief You
        ) // Brief You
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { // Brief You
            me.ash.reader.brief.ui.SheetRows(listOf( // Brief You: each opens its "apply to all feeds" question
                me.ash.reader.brief.ui.SheetRow(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.parse_full_content)) { viewModel.showAllParseFullContentDialog() }, // Brief You
                me.ash.reader.brief.ui.SheetRow(Icons.Outlined.OpenInBrowser, stringResource(R.string.open_in_browser)) { viewModel.showAllOpenInBrowserDialog() }, // Brief You
                me.ash.reader.brief.ui.SheetRow(Icons.Outlined.Notifications, stringResource(R.string.allow_notification)) { viewModel.showAllAllowNotificationDialog() }, // Brief You
            )) // Brief You
            Spacer(modifier = Modifier.height(12.dp)) // Brief You
            me.ash.reader.brief.ui.SheetRows(buildList { // Brief You
                add(me.ash.reader.brief.ui.SheetRow(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_articles)) { viewModel.showClearDialog() }) // Brief You
                if (viewModel.rssService.get().deleteSubscription && group?.id != context.currentAccountId.getDefaultGroupId()) // Brief You
                    add(me.ash.reader.brief.ui.SheetRow(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.delete_group), danger = true) { viewModel.showDeleteDialog() }) // Brief You
            }) // Brief You
            if (viewModel.rssService.get().moveSubscription && groupOptionUiState.groups.size != 1) { // Brief You
                Spacer(modifier = Modifier.height(20.dp)) // Brief You
                me.ash.reader.brief.ui.SheetSection(stringResource(R.string.move_to_group)) // Brief You
                me.ash.reader.brief.feeds.GroupChips( // Brief You: moves all its feeds there
                    groups = groupOptionUiState.groups.filter { it.id != group?.id }, // Brief You
                    selectedGroupId = "", // Brief You
                    onGroupClick = { id -> groupOptionUiState.groups.firstOrNull { it.id == id }?.let { viewModel.showAllMoveToGroupDialog(it) } }, // Brief You
                ) // Brief You
            } // Brief You
        } // Brief You
    } // Brief You
    content() // Brief You

    me.ash.reader.brief.ui.OptionSheetDialogs { // Brief You: above the sheet
    ClearGroupDialog(
        groupName = group?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })
    DeleteGroupDialog(
        groupName = group?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })
    AllAllowNotificationDialog(
        groupName = group?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })
    AllParseFullContentDialog(
        groupName = group?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })
    AllOpenInBrowserDialog(
        groupName = group?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })
    AllMoveToGroupDialog(
        groupName = group?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })
    RenameDialog(
        visible = groupOptionUiState.renameDialogVisible,
        value = groupOptionUiState.newName,
        onValueChange = {
            viewModel.inputNewName(it)
        },
        onDismissRequest = {
            viewModel.hideRenameDialog()
        },
        onConfirm = {
            viewModel.rename()
            scope.launch { drawerState.hide() }
            context.showToast(toastString)
        }
    )
    } // Brief You
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Preset(
    viewModel: GroupOptionViewModel,
    group: Group?,
    context: Context,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        RYSelectionChip(
            modifier = Modifier,
            content = stringResource(R.string.allow_notification),
            selected = false,
            selectedIcon = {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.allow_notification),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
            },
        ) {
            viewModel.showAllAllowNotificationDialog()
        }
        RYSelectionChip(
            modifier = Modifier,
            content = stringResource(R.string.parse_full_content),
            selected = false,
            selectedIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Article,
                    contentDescription = stringResource(R.string.parse_full_content),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
            },
        ) {
            viewModel.showAllParseFullContentDialog()
        }
        RYSelectionChip(
            modifier = Modifier,
            content = stringResource(R.string.open_in_browser),
            selected = false,
            selectedIcon = {
                Icon(
                    imageVector = Icons.Outlined.OpenInBrowser,
                    contentDescription = stringResource(R.string.open_in_browser),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
            },
        ) {
            viewModel.showAllOpenInBrowserDialog()
        }
        RYSelectionChip(
            modifier = Modifier,
            content = stringResource(R.string.clear_articles),
            selected = false,
        ) {
            viewModel.showClearDialog()
        }
        if (viewModel.rssService.get().deleteSubscription && group?.id != context.currentAccountId.getDefaultGroupId()) {
            RYSelectionChip(
                modifier = Modifier,
                content = stringResource(R.string.delete_group),
                selected = false,
            ) {
                viewModel.showDeleteDialog()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowGroups(
    groupOptionUiState: GroupOptionUiState,
    group: Group?,
    groupOptionViewModel: GroupOptionViewModel,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        groupOptionUiState.groups.forEach {
            if (it.id != group?.id) {
                RYSelectionChip(
                    modifier = Modifier,
                    content = it.name,
                    selected = false,
                ) {
                    groupOptionViewModel.showAllMoveToGroupDialog(it)
                }
            }
        }
    }
}

@Composable
private fun LazyRowGroups(
    groupOptionUiState: GroupOptionUiState,
    group: Group?,
    groupOptionViewModel: GroupOptionViewModel,
) {
    LazyRow {
        items(groupOptionUiState.groups) {
            if (it.id != group?.id) {
                RYSelectionChip(
                    modifier = Modifier,
                    content = it.name,
                    selected = false,
                ) {
                    groupOptionViewModel.showAllMoveToGroupDialog(it)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
    }
}
