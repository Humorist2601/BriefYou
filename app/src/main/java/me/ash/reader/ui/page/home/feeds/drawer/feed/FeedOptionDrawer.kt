package me.ash.reader.ui.page.home.feeds.drawer.feed

import me.ash.reader.brief.feeds.FeedIconDialog // Brief You

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep // Brief You
import androidx.compose.material.icons.outlined.RemoveCircleOutline // Brief You
import androidx.compose.foundation.combinedClickable // Brief You
import androidx.compose.foundation.rememberScrollState // Brief You
import androidx.compose.foundation.verticalScroll // Brief You
import androidx.compose.foundation.shape.CircleShape // Brief You
import androidx.compose.ui.draw.clip // Brief You
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.ui.component.ChangeUrlDialog
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.BottomDrawer
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.roundClick
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.page.home.feeds.FeedOptionView

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // Brief You
@Composable
fun FeedOptionDrawer(
    drawerState: me.ash.reader.brief.ui.SheetSwitch = me.ash.reader.brief.ui.rememberSheetSwitch(), // Brief You: Material 3 sheet
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val scope = rememberCoroutineScope()
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()
    val feed = feedOptionUiState.feed
    val toastString = stringResource(R.string.rename_toast, feedOptionUiState.newName)


    var showIconDialog by remember { mutableStateOf(false) } // Brief You
    val iconContext = LocalContext.current
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                feedOptionViewModel.setImageIcon(it) { msg -> iconContext.showToast(msg) }
            }
        }
    if (showIconDialog) me.ash.reader.brief.ui.OptionSheetDialogs { // Brief You: above the sheet
        FeedIconDialog(
            onSymbol = { feedOptionViewModel.setSymbolIcon(it) }, // Brief You: Material icons
            current = feed?.icon, // Brief You
            onImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onWebsiteIcon = { feedOptionViewModel.reloadIcon() },
            onDismiss = { showIconDialog = false },
        )
    } // Brief You

    // Brief You: the app's Material 3 sheet (was Read You's Material 2 one), in the app's sheet design.
    me.ash.reader.brief.ui.OptionSheet(drawerState) { // Brief You
        me.ash.reader.brief.ui.SheetHeader( // Brief You
            icon = { // Brief You: tap the icon to change it
                FeedIcon(modifier = Modifier.clip(CircleShape).clickable { showIconDialog = true }, feedName = feed?.name, iconUrl = feed?.icon, size = 48.dp) // Brief You
            }, // Brief You
            title = { // Brief You: tap the name to rename
                Text( // Brief You
                    modifier = Modifier.alphaIndicationClickable { if (feedOptionViewModel.rssService.get().updateSubscription) feedOptionViewModel.showRenameDialog() }, // Brief You
                    text = feed?.name ?: stringResource(R.string.unknown), // Brief You
                    style = MaterialTheme.typography.titleLarge, // Brief You
                    color = MaterialTheme.colorScheme.onSurface, // Brief You
                    maxLines = 1, // Brief You
                    overflow = TextOverflow.Ellipsis, // Brief You
                ) // Brief You
            }, // Brief You
            subtitle = { // Brief You: the address; tap opens it, hold edits it
                Text( // Brief You
                    modifier = Modifier.combinedClickable( // Brief You
                        onClick = { context.openURL(feed?.url, openLink, openLinkSpecificBrowser) }, // Brief You
                        onLongClick = { // Brief You
                            if (feedOptionViewModel.rssService.get().updateSubscription) { // Brief You
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) // Brief You
                                feedOptionViewModel.showFeedUrlDialog() // Brief You
                            } // Brief You
                        }, // Brief You
                    ), // Brief You
                    text = feed?.url ?: stringResource(R.string.unknown), // Brief You
                    style = MaterialTheme.typography.bodyMedium, // Brief You
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Brief You
                    maxLines = 1, // Brief You
                    overflow = TextOverflow.Ellipsis, // Brief You
                ) // Brief You
            }, // Brief You
        ) // Brief You
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { // Brief You
            me.ash.reader.brief.feeds.FeedOptions( // Brief You
                fullContent = feed?.isFullContent ?: false, // Brief You
                browser = feed?.isBrowser ?: false, // Brief You
                notification = feed?.isNotification ?: false, // Brief You
                onFullContent = { feedOptionViewModel.changeParseFullContentPreset() }, // Brief You
                onBrowser = { feedOptionViewModel.changeOpenInBrowserPreset() }, // Brief You
                onNotification = { feedOptionViewModel.changeAllowNotificationPreset() }, // Brief You
                groups = feedOptionUiState.groups, // Brief You
                selectedGroupId = feed?.groupId ?: "", // Brief You
                onGroupClick = { feedOptionViewModel.selectedGroup(it) }, // Brief You
                onAddNewGroup = { feedOptionViewModel.showNewGroupDialog() }, // Brief You
                showGroup = feedOptionViewModel.rssService.get().moveSubscription, // Brief You
                isMoveToGroup = true, // Brief You
                actions = buildList { // Brief You
                    add(me.ash.reader.brief.ui.SheetRow(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_articles)) { feedOptionViewModel.showClearDialog() }) // Brief You
                    if (feedOptionViewModel.rssService.get().deleteSubscription) add(me.ash.reader.brief.ui.SheetRow(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.unsubscribe), danger = true) { feedOptionViewModel.showDeleteDialog() }) // Brief You
                }, // Brief You
            ) // Brief You
        } // Brief You
    } // Brief You
    content() // Brief You

    me.ash.reader.brief.ui.OptionSheetDialogs { // Brief You: above the sheet
    DeleteFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })

    ClearFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = { scope.launch { drawerState.hide() } })

    TextFieldDialog(
        visible = feedOptionUiState.newGroupDialogVisible,
        title = stringResource(R.string.create_new_group),
        icon = Icons.Outlined.CreateNewFolder,
        value = feedOptionUiState.newGroupContent,
        placeholder = stringResource(R.string.name),
        onValueChange = {
            feedOptionViewModel.inputNewGroup(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideNewGroupDialog()
        },
        onConfirm = {
            feedOptionViewModel.addNewGroup()
        }
    )

    RenameDialog(
        visible = feedOptionUiState.renameDialogVisible,
        value = feedOptionUiState.newName,
        onValueChange = {
            feedOptionViewModel.inputNewName(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideRenameDialog()
        },
        onConfirm = {
            feedOptionViewModel.renameFeed()
            scope.launch { drawerState.hide() }
            context.showToast(toastString)
        }
    )

    ChangeUrlDialog(
        visible = feedOptionUiState.changeUrlDialogVisible,
        value = feedOptionUiState.newUrl,
        onValueChange = {
            feedOptionViewModel.inputNewUrl(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideFeedUrlDialog()
        },
        onConfirm = {
            feedOptionViewModel.changeFeedUrl()
            scope.launch { drawerState.hide() }
        }
    )
    } // Brief You
}
