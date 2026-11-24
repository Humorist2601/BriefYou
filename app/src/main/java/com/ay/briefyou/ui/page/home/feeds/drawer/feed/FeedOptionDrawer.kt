package com.ay.briefyou.ui.page.home.feeds.drawer.feed

import com.ay.briefyou.brief.feeds.FeedIconDialog // Brief You

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
import androidx.compose.material.icons.outlined.Edit // Brief You
import androidx.compose.foundation.layout.Row // Brief You
import androidx.compose.foundation.layout.size // Brief You
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
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.LocalOpenLink
import com.ay.briefyou.infrastructure.preference.LocalOpenLinkSpecificBrowser
import com.ay.briefyou.ui.component.EditFeedDialog
import com.ay.briefyou.ui.component.FeedIcon
import com.ay.briefyou.ui.component.RenameDialog
import com.ay.briefyou.ui.component.base.BottomDrawer
import com.ay.briefyou.ui.component.base.TextFieldDialog
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.openURL
import com.ay.briefyou.ui.ext.roundClick
import com.ay.briefyou.ui.ext.showToast
import com.ay.briefyou.ui.interaction.alphaIndicationClickable
import com.ay.briefyou.ui.page.home.feeds.FeedOptionView

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // Brief You
@Composable
fun FeedOptionDrawer(
    drawerState: com.ay.briefyou.brief.ui.SheetSwitch = com.ay.briefyou.brief.ui.rememberSheetSwitch(), // Brief You: Material 3 sheet
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val scope = rememberCoroutineScope()
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()
    val feed = feedOptionUiState.feed


    var showIconDialog by remember { mutableStateOf(false) } // Brief You
    val iconContext = LocalContext.current
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                feedOptionViewModel.setImageIcon(it) { msg -> iconContext.showToast(msg) }
            }
        }
    val feedPicture = remember(feed?.url, showIconDialog) { com.ay.briefyou.brief.feeds.FeedPictures.get(iconContext, feed?.url) } // Brief You: the feed's own picture, once noted
    if (showIconDialog) com.ay.briefyou.brief.ui.OptionSheetDialogs { // Brief You: above the sheet
        FeedIconDialog(
            onSymbol = { feedOptionViewModel.setSymbolIcon(it) }, // Brief You: Material icons
            onEmoji = { feedOptionViewModel.setEmojiIcon(it) }, // Brief You: or any emoji
            current = feed?.icon, // Brief You
            onImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onWebsiteIcon = { feedOptionViewModel.reloadIcon() },
            feedPicture = feedPicture, // Brief You: its own picture, when it has one
            onFeedPicture = { feedOptionViewModel.setFeedPicture(it) }, // Brief You
            onDismiss = { showIconDialog = false },
        )
    } // Brief You

    // Brief You: the app's Material 3 sheet: the feed's head, a scrolling body, the buttons pinned.
    val canEdit = feedOptionViewModel.rssService.get().updateSubscription // Brief You
    com.ay.briefyou.brief.ui.OptionSheet( // Brief You
        drawerState,
        head = { // Brief You: the feed, its address, and how to change its icon and name
            com.ay.briefyou.brief.ui.IdentityHead( // Brief You
                picture = { FeedIcon(feedName = feed?.name, iconUrl = feed?.icon, size = 56.dp) }, // Brief You
                title = feed?.name ?: stringResource(R.string.unknown), // Brief You
                fact = { // Brief You: the address; tap opens it
                    Text( // Brief You
                        modifier = Modifier.clickable { context.openURL(feed?.url, openLink, openLinkSpecificBrowser) }, // Brief You
                        text = feed?.url ?: stringResource(R.string.unknown), // Brief You
                        maxLines = 1, // Brief You
                        overflow = TextOverflow.Ellipsis, // Brief You
                    ) // Brief You
                }, // Brief You
                onEditIcon = { showIconDialog = true }, // Brief You
                onRename = if (canEdit) ({ feedOptionViewModel.showEditDialog() }) else null, // Brief You: name, and address where it can change
            ) // Brief You
        }, // Brief You
        foot = { // Brief You: stays at the bottom while the body scrolls
            com.ay.briefyou.brief.ui.SheetActions( // Brief You
                stringResource(R.string.clear_articles) to { feedOptionViewModel.showClearDialog() }, // Brief You
                if (feedOptionViewModel.rssService.get().deleteSubscription) stringResource(R.string.unsubscribe) to { feedOptionViewModel.showDeleteDialog() } else null, // Brief You
            ) // Brief You
        }, // Brief You
    ) { // Brief You
        Spacer(modifier = Modifier.height(18.dp)) // Brief You
        com.ay.briefyou.brief.feeds.FeedOptions( // Brief You
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
            settingsLabel = stringResource(R.string.sheet_open_and_notify), // Brief You
        ) // Brief You
        Spacer(modifier = Modifier.height(8.dp)) // Brief You
    } // Brief You
    content() // Brief You

    com.ay.briefyou.brief.ui.OptionSheetDialogs { // Brief You: above the sheet
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

    EditFeedDialog(
        visible = feedOptionUiState.editDialogVisible,
        name = feed?.name ?: "",
        url = feed?.url ?: "",
        showUrl = feedOptionViewModel.rssService.get().updateFeedUrl,
        onDismissRequest = { feedOptionViewModel.hideEditDialog() },
        onConfirm = { name, url ->
            feedOptionViewModel.editFeed(name, url) { message -> context.showToast(message) }
            if (name != feed?.name) context.showToast(context.getString(R.string.rename_toast, name))
            scope.launch { drawerState.hide() }
        }
    )
    } // Brief You
}
