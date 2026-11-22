package com.ay.briefyou.ui.page.home.feeds

// Brief You
import com.ay.briefyou.brief.ui.CollapsedTitle // Brief You
import android.os.Build
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterAlt
import com.ay.briefyou.brief.feeds.BriefFeedSortSheet
import com.ay.briefyou.brief.feeds.BriefFeedsPages
import com.ay.briefyou.brief.feeds.rememberBriefFeedsPager
import com.ay.briefyou.brief.ui.SharedFilterBarSlot
import com.ay.briefyou.brief.ui.TonalCircleButton
import com.ay.briefyou.brief.ui.TopBarAction
import com.ay.briefyou.brief.ui.TopBarButtonGroup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ay.briefyou.brief.ui.onFingerDown // Brief You
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import androidx.work.WorkInfo
import kotlin.collections.set
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.infrastructure.preference.LocalFeedsGroupListExpand
import com.ay.briefyou.infrastructure.preference.LocalNewVersionNumber
import com.ay.briefyou.infrastructure.preference.LocalSkipVersionNumber
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.RYScaffold
import com.ay.briefyou.ui.component.scrollbar.drawVerticalScrollIndicator
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.currentAccountId
import com.ay.briefyou.ui.ext.findActivity
import com.ay.briefyou.ui.ext.getCurrentVersion
import com.ay.briefyou.ui.page.common.RouteName
import com.ay.briefyou.ui.page.home.feeds.accounts.AccountsTab
import com.ay.briefyou.ui.page.home.feeds.drawer.feed.FeedOptionDrawer
import com.ay.briefyou.ui.page.home.feeds.drawer.group.GroupOptionDrawer
import com.ay.briefyou.ui.page.home.feeds.subscribe.SubscribeDialog
import com.ay.briefyou.ui.page.home.feeds.subscribe.SubscribeViewModel
import com.ay.briefyou.ui.page.settings.accounts.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FeedsPage(
    //    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    accountViewModel: AccountViewModel = hiltViewModel(),
    feedsViewModel: FeedsViewModel = hiltViewModel(),
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
    navigateToSettings: () -> Unit,
    navigationToFlow: () -> Unit,
    navigateToAccountList: () -> Unit,
    navigateToAccountDetail: (Int) -> Unit,
    navigateToFilters: () -> Unit = {}, // Brief You
) {
    var accountTabVisible by remember { mutableStateOf(false) }
    var sortSheetVisible by remember { mutableStateOf(false) } // Brief You

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val groupListExpand = LocalFeedsGroupListExpand.current

    val accounts = accountViewModel.accounts.collectAsStateValue(initial = emptyList())

    val feedsUiState = feedsViewModel.feedsUiState.collectAsStateValue()
    val filterState = feedsViewModel.filterStateFlow.collectAsStateValue()
    val importantSum = feedsUiState.importantSum
    val groupWithFeedList = feedsViewModel.groupWithFeedsListFlow.collectAsStateValue()
    val hasFeeds = feedsViewModel.hasFeeds.collectAsStateValue() // Brief You: an account without feeds shows how to add one
    val groupsVisible: SnapshotStateMap<String, Boolean> = feedsUiState.groupsVisible
    val hasGroupVisible by
        remember(groupWithFeedList) {
            derivedStateOf { groupWithFeedList.fastAny { groupsVisible[it.group.id] == true } }
        }

    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion = remember { context.getCurrentVersion() }
    // Brief You: Starred, Unread and All are pages you swipe between, each with its own list.
    val feedsPager = rememberBriefFeedsPager(filterState, feedsViewModel)
    val listState = feedsPager.listState

    val owner = LocalLifecycleOwner.current

    var isSyncing by remember { mutableStateOf(false) }
    val syncingState = rememberPullToRefreshState()
    val syncingScope = rememberCoroutineScope()
    val doSync: () -> Unit = {
        isSyncing = true
        syncingScope.launch { feedsViewModel.sync() }
    }

    DisposableEffect(owner) {
        scope.launch {
            owner.lifecycle.eventFlow.collect {
                when (it) {
                    Lifecycle.Event.ON_RESUME -> { // Brief You: after the screen's animation
                        scope.launch { kotlinx.coroutines.delay(700); feedsViewModel.commitDiffs() } // Brief You
                    } // Brief You
                    Lifecycle.Event.ON_PAUSE -> {
                        feedsViewModel.commitDiffs()
                    }

                    else -> {
                        /* no-op */
                    }
                }
            }
        }
        feedsViewModel.syncWorkLiveData.observe(owner) { workInfoList ->
            workInfoList.let {
                isSyncing = it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
            }
        }
        onDispose { feedsViewModel.syncWorkLiveData.removeObservers(owner) }
    }

    fun expandAllGroups() {
        groupWithFeedList.forEach { groupWithFeed -> groupsVisible[groupWithFeed.group.id] = true }
    }

    fun collapseAllGroups() {
        groupWithFeedList.forEach { groupWithFeed -> groupsVisible[groupWithFeed.group.id] = false }
    }

    val groupDrawerState = com.ay.briefyou.brief.ui.rememberSheetSwitch() // Brief You: Material 3 option sheets
    val feedDrawerState = com.ay.briefyou.brief.ui.rememberSheetSwitch() // Brief You

    // Brief You: on Android 12+ the system already sends a root activity to the background on
    // Back, and leaving Back unhandled lets the predictive "back to home" animation play.
    BackHandler(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { context.findActivity()?.moveTaskToBack(false) }

    RYScaffold(
        topBar = {
            TopAppBar(
                modifier =
                    Modifier.clickable(
                        onClick = {
                            scope.launch {
                                if (listState.firstVisibleItemIndex != 0) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
                title = { CollapsedTitle() }, // Brief You: the account name moves up here as you scroll
                // Brief You: Material 3 Expressive tonal buttons, the actions connected into one
                // pill, with Sort and Filters added.
                navigationIcon = {
                    TonalCircleButton(
                        icon = Icons.Outlined.Settings,
                        description = stringResource(R.string.settings),
                        showBadge = newVersion.whetherNeedUpdate(currentVersion, skipVersion),
                    ) {
                        navigateToSettings()
                    }
                },
                actions = {
                    TopBarButtonGroup( // Brief You
                        buildList {
                            add(TopBarAction(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort_title)) { sortSheetVisible = true })
                            add(TopBarAction(Icons.Outlined.FilterAlt, stringResource(R.string.filters_settings)) { navigateToFilters() })
                            if (subscribeViewModel.rssService.get().addSubscription) {
                                add(TopBarAction(Icons.Rounded.Add, stringResource(R.string.subscribe)) { subscribeViewModel.showDrawer() })
                            }
                        }
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        content = {
            // Brief You: swipeable Starred / Unread / All pages; each runs Read You's list below
            // with its own filter state, groups, count line and list state.
            BriefFeedsPages(feedsPager, feedsViewModel, filterState, state = syncingState, isRefreshing = isSyncing, onRefresh = doSync) { filterState, groupWithFeedList, importantSum, listState ->
                LazyColumn(modifier = Modifier.fillMaxSize().drawVerticalScrollIndicator(listState), state = listState) {
                    item {
                        DisplayText(text = feedsUiState.account?.name ?: "", desc = "") {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            accountTabVisible = true
                        }
                    }
                    item {
                        FeedsBanner(
                            filter = filterState.filter,
                            desc = importantSum.ifEmpty { stringResource(R.string.loading) },
                        ) {
                            feedsViewModel.changeFilter(filterState.copy(group = null, feed = null))
                            navigationToFlow()
                        }
                    }
                    item { com.ay.briefyou.brief.stats.HomeRecapTeaser() } // Brief You: last month's recap (first week)

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.feeds),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            IconButton(
                                onClick = {
                                    if (hasGroupVisible) collapseAllGroups() else expandAllGroups()
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(28.dp),
                            ) {
                                // Brief You: the two icons fade and scale into each other.
                                val unfolded by androidx.compose.animation.core.animateFloatAsState(
                                    if (hasGroupVisible) 1f else 0f,
                                    label = "open all",
                                )
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.UnfoldMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = 1f - unfolded
                                            scaleX = 1f - 0.4f * unfolded
                                            scaleY = scaleX
                                        },
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.UnfoldLess,
                                        contentDescription = stringResource(R.string.unfold_less),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = unfolded
                                            scaleX = 0.6f + 0.4f * unfolded
                                            scaleY = scaleX
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Brief You: a new account starts without feeds; this points to adding the first.
                    if (hasFeeds == false && subscribeViewModel.rssService.get().addSubscription) {
                        item {
                            com.ay.briefyou.ui.component.base.Banner(
                                modifier = Modifier.padding(bottom = 12.dp),
                                title = stringResource(R.string.add_feed_title),
                                desc = stringResource(R.string.add_feed_hint),
                                icon = Icons.Rounded.Add,
                            ) {
                                subscribeViewModel.showDrawer()
                            }
                        }
                    }

                    itemsIndexed(groupWithFeedList) { index, (group, feeds) -> // Brief You: index, for the joined list
                        GroupWithFeedsContainer(shape = com.ay.briefyou.brief.ui.groupShape(index, groupWithFeedList.size), top = com.ay.briefyou.brief.ui.Group.Gap) { // Brief You: the groups as one joined list
                            androidx.compose.foundation.layout.Box(
                                Modifier.onFingerDown(group.id) { // Brief You: its list starts loading on the press
                                    feedsViewModel.warmList(filterState.copy(group = group, feed = null))
                                }
                            ) {
                            GroupItem(
                                isExpanded = {
                                    groupsVisible.getOrPut(group.id, groupListExpand::value)
                                },
                                group = group,
                                count = feeds.sumOf { it.important }, // Brief You
                                onExpanded = {
                                    groupsVisible[group.id] =
                                        groupsVisible
                                            .getOrPut(group.id, groupListExpand::value)
                                            .not()
                                },
                                onLongClick = { scope.launch { groupDrawerState.show() } },
                            ) {
                                com.ay.briefyou.brief.perf.PerfLog.action("Open a group's list") // Brief You: Nightly stutter log
                                feedsViewModel.changeFilter(
                                    filterState.copy(group = group, feed = null)
                                )
                                navigationToFlow()
                            }
                            } // Brief You

                            feeds.forEachIndexed { index, feed ->
                                androidx.compose.foundation.layout.Box(
                                    Modifier.onFingerDown(feed.id) { // Brief You: its list starts loading on the press
                                        feedsViewModel.warmList(filterState.copy(feed = feed, group = null))
                                    }
                                ) {
                                FeedItem(
                                    feed = feed,
                                    isLastItem = { index == feeds.lastIndex },
                                    isExpanded = {
                                        groupsVisible.getOrPut(feed.groupId, groupListExpand::value)
                                    },
                                    onClick = {
                                        com.ay.briefyou.brief.perf.PerfLog.action("Open a feed's list") // Brief You: Nightly stutter log
                                        feedsViewModel.changeFilter(
                                            filterState.copy(feed = feed, group = null)
                                        )
                                        navigationToFlow()
                                    },
                                    onLongClick = { scope.launch { feedDrawerState.show() } },
                                )
                                } // Brief You
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(128.dp))
                        Spacer(
                            modifier =
                                Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Brief You: one bar for the whole app; it steps aside for the option drawers.
            SharedFilterBarSlot(
                enabled = !groupDrawerState.isVisible && !feedDrawerState.isVisible,
                filter = filterState.filter,
            ) {
                com.ay.briefyou.brief.perf.PerfLog.action("Feeds: switch filter (tap)") // Brief You: Nightly stutter log
                feedsViewModel.changeFilter(filterState.copy(filter = it))
            }
        },
    )

    SubscribeDialog(subscribeViewModel = subscribeViewModel)

    BriefFeedSortSheet(sortSheetVisible) { sortSheetVisible = false } // Brief You

    GroupOptionDrawer(drawerState = groupDrawerState)
    FeedOptionDrawer(drawerState = feedDrawerState)

    val currentAccountId = feedsUiState.account?.id

    AccountsTab(
        visible = accountTabVisible,
        accounts = accounts,
        currentAccountId = currentAccountId,
        onAccountSwitch = { accountViewModel.switchAccount(it) { accountTabVisible = false } },
        onClickSettings = {
            accountTabVisible = false
            navigateToAccountDetail(currentAccountId!!)
        },
        onClickManage = {
            accountTabVisible = false
            navigateToAccountList()
        },
        onDismissRequest = { accountTabVisible = false },
    )
}
