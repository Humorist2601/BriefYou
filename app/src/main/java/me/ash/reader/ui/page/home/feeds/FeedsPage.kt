@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.feeds

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import me.ash.reader.ui.page.home.feeds.sort.FeedSortViewModel
import me.ash.reader.ui.page.home.feeds.sort.FeedSortSheet
import me.ash.reader.ui.page.home.feeds.sort.FeedReorderEditor
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFeedsGroupListExpand
import me.ash.reader.infrastructure.preference.LocalFeedsGroupListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFeedsTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalNewVersionNumber
import me.ash.reader.infrastructure.preference.LocalSkipVersionNumber
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.findActivity
import me.ash.reader.ui.ext.getCurrentVersion
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.page.common.RouteName
import me.ash.reader.ui.page.home.feeds.accounts.AccountsTab
import me.ash.reader.ui.page.home.feeds.drawer.feed.FeedOptionDrawer
import me.ash.reader.ui.page.home.feeds.drawer.group.GroupOptionDrawer
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeDialog
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel
import me.ash.reader.ui.page.settings.accounts.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FeedsPage(
    //    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    accountViewModel: AccountViewModel = hiltViewModel(),
    feedsViewModel: FeedsViewModel = hiltViewModel(),
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
    sortViewModel: FeedSortViewModel = hiltViewModel(),
    navigateToSettings: () -> Unit,
    navigationToFlow: () -> Unit,
    navigateToAccountList: () -> Unit,
    navigateToAccountDetail: (Int) -> Unit,
    navigateToFilters: () -> Unit = {},
) {
    var accountTabVisible by remember { mutableStateOf(false) }

    // Sorting: the sheet with the modes, and the in-place drag-to-reorder mode.
    var sortSheetVisible by remember { mutableStateOf(false) }
    var reorderMode by rememberSaveable { mutableStateOf(false) }
    val sortSettings = sortViewModel.settings.collectAsStateValue()
    val allGroups = sortViewModel.ordered.collectAsStateValue()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val topBarTonalElevation = LocalFeedsTopBarTonalElevation.current
    val groupListTonalElevation = LocalFeedsGroupListTonalElevation.current
    val groupListExpand = LocalFeedsGroupListExpand.current
    val filterBarStyle = LocalFeedsFilterBarStyle.current
    val filterBarPadding = LocalFeedsFilterBarPadding.current
    val filterBarTonalElevation = LocalFeedsFilterBarTonalElevation.current

    val accounts = accountViewModel.accounts.collectAsStateValue(initial = emptyList())

    val feedsUiState = feedsViewModel.feedsUiState.collectAsStateValue()
    val filterState = feedsViewModel.filterStateFlow.collectAsStateValue()
    val importantSum = feedsUiState.importantSum
    val groupWithFeedList = feedsViewModel.groupWithFeedsListFlow.collectAsStateValue()
    val groupsVisible: SnapshotStateMap<String, Boolean> = feedsUiState.groupsVisible
    val hasGroupVisible by
        remember(groupWithFeedList) {
            derivedStateOf { groupWithFeedList.fastAny { groupsVisible[it.group.id] == true } }
        }

    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion = remember { context.getCurrentVersion() }

    // Brief You: Starred, Unread and All are pages you can swipe between (in the order of the bar
    // at the bottom). Each page keeps its own scroll position.
    val filters = Filter.values
    val currentPage = filters.indexOf(filterState.filter).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentPage) { filters.size }
    val listStates = List(filters.size) { rememberLazyListState() }
    val listState = listStates[pagerState.currentPage.coerceIn(0, filters.lastIndex)]

    // Tapping the bar (or a filter changed on another screen): slide to that page. While it
    // slides past the pages in between, those must not become the filter.
    val latestFilterState by rememberUpdatedState(filterState)
    var slidingToPage by remember { mutableStateOf(false) }
    val pageSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage || pagerState.currentPageOffsetFraction != 0f) {
            slidingToPage = true
            try {
                pagerState.animateScrollToPage(currentPage, animationSpec = pageSpec)
            } finally {
                slidingToPage = false
            }
        }
    }
    // Swiping: the filter follows the page as soon as you pass the middle, so the new page has its
    // own data by the time it settles. A tick marks the change, like a segmented control.
    LaunchedEffect(pagerState) {
        merge(
                snapshotFlow { pagerState.currentPage },
                snapshotFlow { pagerState.settledPage },
            )
            .collect { page ->
                val current = latestFilterState
                if (!slidingToPage && filters.getOrNull(page) != null &&
                    filters[page] != current.filter
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    feedsViewModel.changeFilter(current.copy(filter = filters[page]))
                }
            }
    }

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
                    Lifecycle.Event.ON_RESUME,
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

    val groupDrawerState =
        rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)
    val feedDrawerState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden, skipHalfExpanded = true)

    // On Android 12+ the system already sends a root activity to the background on Back, and
    // leaving Back unhandled here lets the predictive "back to home" animation play.
    BackHandler(Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        context.findActivity()?.moveTaskToBack(false)
    }

    BackHandler(enabled = reorderMode) { reorderMode = false }

    RYScaffold(
        topBarTonalElevation = topBarTonalElevation.value.dp,
        //        containerTonalElevation = groupListTonalElevation.value.dp,
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
                title = {},
                navigationIcon = {
                    FeedbackIconButton(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                        showBadge = newVersion.whetherNeedUpdate(currentVersion, skipVersion),
                    ) {
                        navigateToSettings()
                    }
                },
                actions = {
                    // Brief You: sort groups and feeds (by name, by count or by hand).
                    FeedbackIconButton(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = stringResource(R.string.sort_title),
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        sortSheetVisible = true
                    }
                    // Brief You: quick access to mute keywords, duplicates and the filter log.
                    FeedbackIconButton(
                        imageVector = Icons.Outlined.FilterAlt,
                        contentDescription = stringResource(R.string.filters_settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                    ) {
                        navigateToFilters()
                    }
                    if (subscribeViewModel.rssService.get().addSubscription) {
                        FeedbackIconButton(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.subscribe),
                            tint = MaterialTheme.colorScheme.onSurface,
                        ) {
                            subscribeViewModel.showDrawer()
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceColorAtElevation(
                                topBarTonalElevation.value.dp
                            )
                    ),
            )
        },
        content = {
            PullToRefreshBox(
                state = syncingState,
                isRefreshing = isSyncing,
                onRefresh = doSync,
                // Material 3 Expressive shape-morphing refresh indicator.
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = syncingState,
                        isRefreshing = isSyncing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Reordering drags items around, so the pages stay put meanwhile.
                    userScrollEnabled = !reorderMode,
                    key = { filters[it].index },
                    // All three pages stay ready, so the one you swipe to is already filled in.
                    beyondViewportPageCount = filters.size - 1,
                ) { page ->
                    val pageFilter = filters[page]
                    // Each page has its own list and count line, so a page never shows
                    // another filter's groups, even for a moment while swiping.
                    val pageGroups by
                        feedsViewModel.groupWithFeedsListFlow(pageFilter).collectAsState()
                    val pageSum by feedsViewModel.importantSumFlow(pageFilter).collectAsState()
                    val data = FeedsPageData(pageGroups, pageSum)
                    val pageListState = listStates[page]
                    Box(
                        modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                // The page you swipe away from shrinks and dims a little, the one
                                // coming in grows into place.
                                val offset =
                                    ((pagerState.currentPage - page) +
                                            pagerState.currentPageOffsetFraction)
                                        .absoluteValue
                                        .coerceIn(0f, 1f)
                                val scale = 1f - 0.06f * offset
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - 0.4f * offset
                            }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().drawVerticalScrollIndicator(pageListState),
                            state = pageListState,
                        ) {
                            item {
                                DisplayText(text = feedsUiState.account?.name ?: "", desc = "") {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    accountTabVisible = true
                                }
                            }
                            item {
                                FeedsBanner(
                                    filter = pageFilter,
                                    desc = data.importantSum.ifEmpty { stringResource(R.string.loading) },
                                ) {
                                    feedsViewModel.changeFilter(
                                        filterState.copy(filter = pageFilter, group = null, feed = null)
                                    )
                                    navigationToFlow()
                                }
                            }

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
                                        text =
                                            stringResource(
                                                if (reorderMode) R.string.sort_reordering else R.string.feeds
                                            ),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    if (reorderMode) {
                                        FilledTonalButton(onClick = { reorderMode = false }) {
                                            Text(stringResource(R.string.sort_done))
                                        }
                                    } else IconButton(
                                        onClick = {
                                            if (hasGroupVisible) collapseAllGroups() else expandAllGroups()
                                        },
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (hasGroupVisible) Icons.Rounded.UnfoldLess
                                                else Icons.Rounded.UnfoldMore,
                                            contentDescription = stringResource(R.string.unfold_less),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (reorderMode && page == currentPage) {
                                item {
                                    FeedReorderEditor(
                                        groups = allGroups,
                                        onMoveGroups = sortViewModel::moveGroups,
                                        onMoveFeeds = sortViewModel::moveFeeds,
                                    )
                                }
                            } else {
                                itemsIndexed(data.groupWithFeedList) { _, (group, feeds) ->
                                    GroupWithFeedsContainer {
                                        GroupItem(
                                            isExpanded = {
                                                groupsVisible.getOrPut(group.id, groupListExpand::value)
                                            },
                                            group = group,
                                            onExpanded = {
                                                groupsVisible[group.id] =
                                                    groupsVisible
                                                        .getOrPut(group.id, groupListExpand::value)
                                                        .not()
                                            },
                                            onLongClick = { scope.launch { groupDrawerState.show() } },
                                        ) {
                                            feedsViewModel.changeFilter(
                                                filterState.copy(filter = pageFilter, group = group, feed = null)
                                            )
                                            navigationToFlow()
                                        }

                                        feeds.forEachIndexed { index, feed ->
                                            FeedItem(
                                                feed = feed,
                                                isLastItem = { index == feeds.lastIndex },
                                                isExpanded = {
                                                    groupsVisible.getOrPut(feed.groupId, groupListExpand::value)
                                                },
                                                onClick = {
                                                    feedsViewModel.changeFilter(
                                                        filterState.copy(filter = pageFilter, feed = feed, group = null)
                                                    )
                                                    navigationToFlow()
                                                },
                                                onLongClick = { scope.launch { feedDrawerState.show() } },
                                            )
                                        }
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
                }
            }
        },
        bottomBar = {
            FilterBar(
                modifier =
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("filterBar"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                filter = filterState.filter,
                filterBarStyle = filterBarStyle.value,
                filterBarFilled = true,
                filterBarPadding = filterBarPadding.dp,
                filterBarTonalElevation = filterBarTonalElevation.value.dp,
            ) {
                feedsViewModel.changeFilter(filterState.copy(filter = it))
            }
        },
    )

    SubscribeDialog(subscribeViewModel = subscribeViewModel)

    FeedSortSheet(
        visible = sortSheetVisible,
        settings = sortSettings,
        onDismiss = { sortSheetVisible = false },
        onGroupMode = sortViewModel::setGroupMode,
        onFeedMode = sortViewModel::setFeedMode,
        onReorder = {
            sortViewModel.startReorder()
            sortSheetVisible = false
            reorderMode = true
        },
        onReset = sortViewModel::reset,
    )

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

/** What one page of the Feeds screen shows: its groups and feeds, and the count line on top. */
private data class FeedsPageData(val groupWithFeedList: List<GroupWithFeed>, val importantSum: String)
