package me.ash.reader.brief.feeds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import me.ash.reader.ui.component.base.RYTextField2
import me.ash.reader.R
import me.ash.reader.brief.ui.OptionSheetDialogs
import me.ash.reader.brief.ui.SheetHeader
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.ClipboardTextField
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeState
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel

/**
 * Add feed, as the app's bottom sheet (was Read You's dialog): paste an address and search, then
 * the found feed's settings in the same design as a feed's options sheet, and Subscribe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddFeedSheet(viewModel: SubscribeViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val uiState = viewModel.subscribeUiState.collectAsStateValue()
    val state = viewModel.subscribeState.collectAsStateValue()

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let { uri ->
                context.contentResolver.openInputStream(uri)?.let { input -> viewModel.importFromInputStream(input) }
            }
        }

    // Kept on screen while it slides down after closing, showing what it last showed.
    val visible = state is SubscribeState.Visible
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mounted by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<SubscribeState?>(null) }
    SideEffect { if (visible) last = state }
    LaunchedEffect(visible) {
        if (visible) mounted = true
        else if (mounted) {
            sheetState.hide()
            mounted = false
        }
    }
    val shown = if (visible) state else last
    if (!(visible || mounted) || shown == null || shown !is SubscribeState.Visible) return

    if (visible) {
        DisposableEffect(Unit) { onDispose { viewModel.cancelSearch() } }
    }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The keyboard rises with the sheet: the address box is focused as soon as the sheet starts
    // opening (the sheet's content pads for the keyboard frame by frame, so they move together).
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        snapshotFlow { sheetState.targetValue == SheetValue.Expanded }.first { it }
        if (viewModel.subscribeState.value is SubscribeState.Idle) runCatching { focus.requestFocus() }
    }
    // And falls with it: whenever the sheet starts closing (a swipe, the scrim, back or a button),
    // the keyboard goes down at the same time instead of staying up after the sheet has gone.
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.targetValue }.collect {
            if (it == SheetValue.Hidden && mounted) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }

    // The keyboard goes down first, then the sheet, so they don't fight over the sheet's place.
    // Cancel / Import: the keyboard slides down first (the sheet's content following it), then the
    // sheet. Closing both at once removed the sheet's window under the keyboard, so the keyboard
    // vanished instead of sliding.
    val closeScope = rememberCoroutineScope()
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    fun close() {
        closeScope.launch {
            focusManager.clearFocus()
            keyboard?.hide()
            withTimeoutOrNull(600) { snapshotFlow { ime.getBottom(density) }.first { it == 0 } }
            viewModel.hideDrawer()
        }
    }

    // The content follows the keyboard frame by frame (padding for it inside the sheet) instead of
    // the sheet being moved to a new place when the keyboard has opened.
    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = sheetState,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) },
    ) {
      androidx.compose.foundation.layout.Box(
          Modifier.windowInsetsPadding(
              androidx.compose.foundation.layout.WindowInsets.ime.union(androidx.compose.foundation.layout.WindowInsets.navigationBars)
          )
      ) {
        AnimatedContent(
            targetState = shown as SubscribeState,
            transitionSpec = {
                (slideInHorizontally { w -> w } + fadeIn()).togetherWith(slideOutHorizontally { w -> -w } + fadeOut()) using null
            },
            contentKey = { it is SubscribeState.Configure },
            label = "addFeed",
        ) { s ->
            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                when (s) {
                    is SubscribeState.Input -> {
                        SheetHeader(
                            icon = { SheetBadge() },
                            title = {
                                Text(
                                    text = stringResource(if (s is SubscribeState.Fetching) R.string.searching else R.string.add_feed_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            subtitle = {
                                Text(
                                    text = stringResource(R.string.add_feed_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        val errorText = (s as? SubscribeState.Idle)?.errorMessage ?: ""
                        // The keyboard opens only once the sheet is fully up: opening it while
                        // the sheet slides in made the sheet jump.
                        RYTextField2(
                            state = s.linkState,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            readOnly = s is SubscribeState.Fetching,
                            placeholder = stringResource(R.string.feed_or_site_url),
                            errorMessage = errorText,
                            autoFocus = false,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            onKeyboardAction = KeyboardActionHandler { viewModel.searchFeed() },
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s is SubscribeState.Idle && s.importFromOpmlEnabled) {
                                OutlinedButton(
                                    shapes = ButtonDefaults.shapes(),
                                    onClick = {
                                        launcher.launch(arrayOf(MimeType.ANY))
                                        close()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.import_from_opml), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { close() }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                            Button(
                                shapes = ButtonDefaults.shapes(),
                                enabled = s is SubscribeState.Idle && s.linkState.text.isNotBlank(),
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.searchFeed()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                if (s is SubscribeState.Fetching) {
                                    LoadingIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Text(stringResource(R.string.search))
                                }
                            }
                        }
                    }

                    is SubscribeState.Configure -> {
                        SheetHeader(
                            icon = {
                                FeedIcon(
                                    feedName = s.searchedFeed.title,
                                    iconUrl = s.searchedFeed.icon?.url,
                                    size = 48.dp,
                                    placeholderIcon = null,
                                )
                            },
                            title = {
                                // Tap the name to change it before subscribing.
                                Text(
                                    modifier = Modifier.alphaIndicationClickable { viewModel.showRenameDialog() },
                                    text = s.searchedFeed.title ?: "",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            subtitle = {
                                Text(
                                    text = s.feedLink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            FeedOptions(
                                fullContent = s.fullContent,
                                browser = s.browser,
                                notification = s.notification,
                                onFullContent = { viewModel.toggleParseFullContentPreset() },
                                onBrowser = { viewModel.toggleOpenInBrowserPreset() },
                                onNotification = { viewModel.toggleAllowNotificationPreset() },
                                groups = s.groups,
                                selectedGroupId = s.selectedGroupId,
                                onGroupClick = { viewModel.selectedGroup(it) },
                                onAddNewGroup = { viewModel.showNewGroupDialog() },
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { close() }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                shapes = ButtonDefaults.shapes(),
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.subscribe()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.subscribe))
                            }
                        }
                    }

                    SubscribeState.Hidden -> {}
                }
            }
        }
      }
    }

    OptionSheetDialogs {
        RenameDialog(
            visible = uiState.renameDialogVisible,
            value = uiState.newName,
            onValueChange = { viewModel.inputNewName(it) },
            onDismissRequest = { viewModel.hideRenameDialog() },
            onConfirm = {
                viewModel.renameFeed()
                viewModel.hideRenameDialog()
            },
        )
        TextFieldDialog(
            visible = uiState.newGroupDialogVisible,
            title = stringResource(R.string.create_new_group),
            icon = Icons.Outlined.CreateNewFolder,
            value = uiState.newGroupContent,
            placeholder = stringResource(R.string.name),
            onValueChange = { viewModel.inputNewGroup(it) },
            onDismissRequest = { viewModel.hideNewGroupDialog() },
            onConfirm = { viewModel.addNewGroup() },
        )
    }
}

/** The feed symbol in an accent circle, at the head of the sheet before a feed is found. */
@Composable
private fun SheetBadge() {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.RssFeed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(26.dp),
        )
    }
}
