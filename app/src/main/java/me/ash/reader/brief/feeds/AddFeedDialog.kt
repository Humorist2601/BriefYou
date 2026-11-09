package me.ash.reader.brief.feeds

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.ash.reader.R
import me.ash.reader.brief.ui.InWindowAlertDialog
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.RYTextField2
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeState
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel

/**
 * Add a feed, as a floating dialog like Change URL (drawn in the app's window, so the keyboard and
 * the cursor's handle behave): the address first, then the found feed with its settings in the
 * same design as a feed's options sheet (tiles, a sideways row of groups), and Subscribe.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddFeedDialog(viewModel: SubscribeViewModel) {
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

    if (state !is SubscribeState.Visible) return
    DisposableEffect(Unit) { onDispose { viewModel.cancelSearch() } }

    fun close() {
        focusManager.clearFocus()
        viewModel.hideDrawer()
    }

    // In a window of its own (no dialog host), like the dialogs over the option sheets. Drawn in
    // the app's window, every frame of the keyboard sliding re-laid out the whole feeds page
    // under it, which made the dialog lag.
    me.ash.reader.brief.ui.OptionSheetDialogs { AddFeedDialogContent(viewModel, state, uiState, { launcher.launch(it) }) { close() } }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddFeedDialogContent(
    viewModel: SubscribeViewModel,
    state: SubscribeState,
    uiState: me.ash.reader.ui.page.home.feeds.subscribe.SubscribeUiState,
    openFile: (Array<String>) -> Unit,
    close: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val configure = state as? SubscribeState.Configure
    InWindowAlertDialog(
        // Not closed by a tap outside, so a pasted address isn't lost to a stray tap.
        properties = DialogProperties(dismissOnClickOutside = false),
        onDismissRequest = { close() },
        icon = {
            if (configure != null) {
                FeedIcon(
                    feedName = configure.searchedFeed.title,
                    iconUrl = configure.searchedFeed.icon?.url,
                    size = 48.dp,
                )
            } else {
                Box(
                    modifier =
                        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
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
        },
        title = {
            if (configure != null) {
                // Tap the name to change it before subscribing.
                Text(
                    text = configure.searchedFeed.title ?: "",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alphaIndicationClickable { viewModel.showRenameDialog() },
                )
            } else {
                Text(
                    stringResource(
                        if (state is SubscribeState.Fetching) R.string.searching else R.string.add_feed_title
                    )
                )
            }
        },
        text = {
            AnimatedContent(
                targetState = state as SubscribeState,
                transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false)) },
                contentKey = { it is SubscribeState.Configure },
                label = "addFeed",
            ) { s ->
                when (s) {
                    is SubscribeState.Input -> {
                        val fetching = s is SubscribeState.Fetching
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.add_feed_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            RYTextField2(
                                state = s.linkState,
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = fetching,
                                placeholder = stringResource(R.string.feed_or_site_url),
                                errorMessage = (s as? SubscribeState.Idle)?.errorMessage ?: "",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                onKeyboardAction = KeyboardActionHandler { viewModel.searchFeed() },
                            )
                            // While it looks, a wavy line under the address.
                            AnimatedVisibility(
                                visible = fetching,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                            }
                        }
                    }

                    is SubscribeState.Configure -> {
                        Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                text = s.feedLink,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(18.dp))
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
                    }

                    SubscribeState.Hidden -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is SubscribeState.Configure ->
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.subscribe()
                        }
                    ) {
                        Text(stringResource(R.string.subscribe))
                    }

                is SubscribeState.Input ->
                    TextButton(
                        enabled = state is SubscribeState.Idle && state.linkState.text.isNotBlank(),
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.searchFeed()
                        },
                    ) {
                        Text(stringResource(R.string.search))
                    }

                else -> {}
            }
        },
        dismissButton = {
            if (state is SubscribeState.Idle && state.importFromOpmlEnabled) {
                TextButton(
                    onClick = {
                        openFile(arrayOf(MimeType.ANY))
                        close()
                    }
                ) {
                    Text(stringResource(R.string.import_from_opml))
                }
            } else {
                TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

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
