package com.ay.briefyou.ui.page.home.feeds

// Brief You
import android.os.Build
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.FilterAlt
import com.ay.briefyou.brief.ui.CollapsedTitle
import com.ay.briefyou.brief.ui.TonalCircleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.ay.briefyou.ui.component.base.DisplayText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import com.ay.briefyou.brief.feeds.BriefFeedSortSheet
import com.ay.briefyou.brief.feeds.BriefFeedsPages
import com.ay.briefyou.brief.feeds.rememberBriefFeedsPager
import com.ay.briefyou.brief.ui.SharedFilterBarSlot
import com.ay.briefyou.brief.ui.TopBarAction
import com.ay.briefyou.brief.ui.TopBarButtonGroup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.BYScaffold
import com.ay.briefyou.ui.component.scrollbar.drawVerticalScrollIndicator
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.currentAccountId
import com.ay.briefyou.ui.ext.findActivity
import com.ay.briefyou.ui.ext.getCurrentVersion
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
    var filtersSheetVisible by remember { mutableStateOf(false) } // Brief You

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

    // Brief You: what the hero card says besides the count.
    val online = com.ay.briefyou.brief.feeds.rememberOnline()
    val lastSeen = remember { com.ay.briefyou.brief.feeds.HeroSignals.lastSeen(context) }
    LaunchedEffect(lastSeen) {
        if (lastSeen == 0L) com.ay.briefyou.brief.feeds.HeroSignals.markSeen(context) else feedsViewModel.setNewSince(lastSeen)
    }
    val oldestUnread by feedsViewModel.oldestUnread.collectAsState()
    val newSinceOpen by feedsViewModel.newSinceOpen.collectAsState()
    val cameOutToday by feedsViewModel.cameOutToday.collectAsState()
    val oldestFeedId by feedsViewModel.oldestFeedId.collectAsState()
    val cameOutTodayPerFeed by feedsViewModel.cameOutTodayPerFeed.collectAsState()

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
                    Lifecycle.Event.ON_STOP -> com.ay.briefyou.brief.feeds.HeroSignals.markSeen(context) // Brief You: "new since you last opened"
                    Lifecycle.Event.ON_START -> { // Brief You: back after being away: new since you last left
                        feedsViewModel.refreshToday()
                        com.ay.briefyou.brief.feeds.HeroSignals.lastSeen(context).takeIf { seen -> seen > 0L }?.let(feedsViewModel::setNewSince)
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

    BYScaffold(
        topBar = {
            val surfaceColor = MaterialTheme.colorScheme.surface
            TopAppBar(
                modifier =
                    Modifier.drawBehind {
                        // Brief You: the page fades in under the bar, like the list under the bottom bar.
                        // Moved down first: a brush is laid out from the drawing's own origin.
                        // A rectangle's brush spans the whole bar unless told its height: it is given the fade's own.
                        val fade = 28.dp.toPx()
                        translate(top = size.height) {
                            drawRect(
                                brush = com.ay.briefyou.brief.ui.edgeFadeBrush(surfaceColor, solidAtBottom = false, height = fade),
                                size = androidx.compose.ui.geometry.Size(size.width, fade),
                            )
                        }
                    }.clickable(
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
                            add(TopBarAction(Icons.Outlined.FilterAlt, stringResource(R.string.filters_settings)) { filtersSheetVisible = true })
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
            // Brief You: swipeable Starred / Unread / All pages; each runs the original list below
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
                        // Brief You: the hero card; the count is exact, summed from the page's own feeds.
                        val heroCount = groupWithFeedList.sumOf { g -> g.feeds.sumOf { it.important } }
                        val heroTop = groupWithFeedList.maxByOrNull { g -> g.feeds.sumOf { it.important } }
                            ?.takeIf { g -> g.feeds.any { it.important > 0 } }?.group?.name
                        com.ay.briefyou.brief.feeds.HomeHero(
                            filter = filterState.filter,
                            count = heroCount,
                            groupCount = groupWithFeedList.count { g -> g.feeds.any { it.important > 0 } },
                            feedCount = groupWithFeedList.sumOf { it.feeds.size },
                            topGroup = heroTop,
                            topShare = if (heroCount > 0) (groupWithFeedList.maxOfOrNull { g -> g.feeds.sumOf { it.important } } ?: 0) / heroCount.toFloat() else 1f,
                            updatedAt = feedsUiState.account?.updateAt,
                            hasFeeds = hasFeeds,
                            loading = importantSum.isEmpty() && hasFeeds != false,
                            newCount = newSinceOpen,
                            cameOutToday = cameOutToday,
                            oldestDays = oldestUnread?.takeIf { it > 0 }?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() },
                            topFeed = groupWithFeedList.flatMap { it.feeds }.maxByOrNull { it.important }?.takeIf { it.important > 0 }?.name,
                            oldestFeed = oldestFeedId?.let { id -> groupWithFeedList.firstNotNullOfOrNull { g -> g.feeds.firstOrNull { it.id == id }?.name } },
                            todayTopGroup =
                                run {
                                    val perFeed = cameOutTodayPerFeed.associate { it.feedId to it.c }
                                    groupWithFeedList
                                        .map { g -> g.group.name to g.feeds.sumOf { perFeed[it.id] ?: 0 } }
                                        .maxByOrNull { it.second }
                                        ?.takeIf { it.second > 0 && groupWithFeedList.size >= 2 }
                                        ?.first
                                },
                            syncing = isSyncing,
                            online = online,
                            onRetry = doSync,
                            accountSignsIn = feedsUiState.account?.type != com.ay.briefyou.domain.model.account.AccountType.Local,
                            onSignIn = { feedsUiState.account?.id?.let(navigateToAccountDetail) },
                            animateBackground = !sortSheetVisible && !filtersSheetVisible && !accountTabVisible && !groupDrawerState.isVisible && !feedDrawerState.isVisible,
                            onClick = {
                                feedsViewModel.changeFilter(filterState.copy(group = null, feed = null))
                                navigationToFlow()
                            },
                            onAddFeed = { if (subscribeViewModel.rssService.get().addSubscription) subscribeViewModel.showDrawer() },
                        )
                    }
                    item { com.ay.briefyou.brief.stats.HomeRecapTeaser() } // Brief You: last month's recap (first week)

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 30.dp), // lined up with the groups' arrow chips
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val feedTotal = groupWithFeedList.sumOf { it.feeds.size }
                            val feedWord = pluralStringResource(R.plurals.hero_feed_word, feedTotal)
                            val inGroups = pluralStringResource(R.plurals.hero_in_groups, groupWithFeedList.size, groupWithFeedList.size)
                            Row(verticalAlignment = Alignment.Bottom) { // a big feed count, the rest quietly beside it
                                Text(
                                    text = feedTotal.toString(),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                                Text(
                                    text = "$feedWord $inGroups",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
                                )
                            }
                            // The same size and tone as the groups' arrow chips, right above them.
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { if (hasGroupVisible) collapseAllGroups() else expandAllGroups() },
                                contentAlignment = Alignment.Center,
                            ) {
                                // Brief You: the two icons fade and scale into each other.
                                val unfolded by androidx.compose.animation.core.animateFloatAsState(
                                    if (hasGroupVisible) 1f else 0f,
                                    label = "open all",
                                )
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                                feeds = feeds, // Brief You: its icon stack
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
                                    isFirstItem = index == 0, // Brief You
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
                pagerState = feedsPager.pagerState, // Brief You: the pill follows the swipe
            ) {
                com.ay.briefyou.brief.perf.PerfLog.action("Feeds: switch filter (tap)") // Brief You: Nightly stutter log
                feedsViewModel.changeFilter(filterState.copy(filter = it))
            }
        },
    )

    SubscribeDialog(subscribeViewModel = subscribeViewModel)

    BriefFeedSortSheet(sortSheetVisible) { sortSheetVisible = false } // Brief You
    com.ay.briefyou.brief.filters.FiltersSheet(
        visible = filtersSheetVisible,
        onDismiss = { filtersSheetVisible = false },
        onOpenAll = { navigateToFilters() },
    ) // Brief You

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
