package me.ash.reader.ui.page.home.flow

import me.ash.reader.ui.component.FilterBarSpace
import me.ash.reader.ui.component.TonalCircleButton
import me.ash.reader.ui.component.TopBarAction
import me.ash.reader.ui.component.TopBarButtonGroup
import me.ash.reader.ui.component.FilterBarSpec
import me.ash.reader.ui.component.ProvideFilterBar
import me.ash.reader.ui.page.home.reading.summary.SummaryRequest
import me.ash.reader.ui.page.home.reading.summary.SummarySheet
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalMarkAsReadOnScroll
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.LocalSortUnreadArticles
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.SortUnreadArticlesPreference
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.FloatingBarGap
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYExtensibleVisibility
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.scrollbar.VerticalScrollIndicatorFactory
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.scrollbar.scrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedXAxisTransitionSlow
import me.ash.reader.ui.motion.sharedYAxisTransitionExpressive
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults.ContentOffsetMultiple
import me.ash.reader.ui.page.home.reading.PullToLoadState
import me.ash.reader.ui.page.home.reading.pullToLoad
import me.ash.reader.ui.page.home.reading.rememberPullToLoadState
import me.ash.reader.ui.theme.GoogleSansWideFontFamily


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterialApi::class,
)

@Composable
fun FlowPage(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isTwoPane: Boolean,
    /** False while the list is hidden (an article is open), so the filter bar slides away. */
    showFilterBar: Boolean = true,
    viewModel: ArticleListReaderViewModel,
    onNavigateUp: () -> Unit,
    navigateToArticle: (String, Int) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    val sharedContent = LocalSharedContent.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current.value
    val context = LocalContext.current

    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current

    val settings = LocalSettings.current
    val pullToSwitchFeed = settings.pullToSwitchFeed

    val flowUiState = viewModel.flowUiState.collectAsStateValue()
    if (flowUiState == null) return

    val pagerData: PagerData = flowUiState.pagerData

    val filterUiState = pagerData.filterState

    val listState = rememberSaveable(pagerData, saver = LazyListState.Saver) { LazyListState(0, 0) }

    val isTopBarElevated = topBarTonalElevation.value > 0
    val scrolledTopBarContainerColor =
        with(MaterialTheme.colorScheme) { if (isTopBarElevated) surfaceContainer else surface }

    val titleText =
        when {
            filterUiState.group != null -> filterUiState.group.name
            filterUiState.feed != null -> filterUiState.feed.name
            else -> filterUiState.filter.toName()
        }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var markAsRead by remember { mutableStateOf(false) }
    var onSearch by rememberSaveable { mutableStateOf(false) }
    var showGist by remember { mutableStateOf(false) }
    var showTriage by rememberSaveable { mutableStateOf(false) }

    // The AI gist / Triage button: only in Unread, and not while searching. Like the reading page's
    // bar, only two things move it, so it can never pop in and out:
    // - your finger: dragging towards the end of the list hides it, dragging back shows it;
    // - a new list (another feed, group or filter) shows it, once.
    // It doesn't look at the list's scroll position: while one feed slides into the next, the two
    // lists briefly share it, which made the button show, hide and show again.
    var fabHiddenByFinger by remember { mutableStateOf(false) }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -1f) fabHiddenByFinger = true
                    else if (available.y > 1f) fabHiddenByFinger = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(filterUiState.copy(searchContent = null)) { fabHiddenByFinger = false }
    var currentPullToLoadState: PullToLoadState? by remember { mutableStateOf(null) }
    val showActionsFab = filterUiState.filter.isUnread() && !onSearch && !fabHiddenByFinger

    var currentLoadAction: LoadAction? by remember { mutableStateOf(null) }

    val settleSpec = remember { spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy) }

    val lastVisibleIndex =
        remember(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .filterNotNull()
        }

    val onToggleStarred: (ArticleWithFeed) -> Unit = remember {
        { article ->
            viewModel.updateStarredStatus(
                articleId = article.article.id,
                isStarred = !article.article.isStarred,
            )
        }
    }

    val onToggleRead: (ArticleWithFeed) -> Unit = remember {
        { articleWithFeed -> viewModel.diffMapHolder.updateDiff(articleWithFeed) }
    }

    val sortByEarliest =
        filterUiState.filter.isUnread() &&
            LocalSortUnreadArticles.current == SortUnreadArticlesPreference.Earliest

    val onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsReadFromListByDate(
                    date = it.article.date,
                    isBefore = sortByEarliest,
                )
            }
        }

    val onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsReadFromListByDate(
                    date = it.article.date,
                    isBefore = !sortByEarliest,
                )
            }
        }

    val onShare: ((ArticleWithFeed) -> Unit)? = remember {
        { articleWithFeed ->
            with(articleWithFeed.article) { sharedContent.share(context, title, link) }
        }
    }

    LaunchedEffect(onSearch) {
        if (!onSearch) {
            keyboardController?.hide()
            viewModel.inputSearchContent(null)
        }
    }

    val topAppBarState = rememberTopAppBarState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val scrollAppBarToCollapsed =
        remember(topAppBarState) {
            {
                scope.launch {
                    val initial = topAppBarState.heightOffset
                    val target = topAppBarState.heightOffsetLimit
                    if (initial != target)
                        animate(
                            initialValue = initial,
                            targetValue = target,
                            initialVelocity = 0f,
                            animationSpec = settleSpec,
                        ) { value, _ ->
                            topAppBarState.heightOffset = value
                        }
                }
            }
        }

    val snapAppBarToCollapsed =
        remember(topAppBarState) {
            {
                scope.launch {
                    val initial = topAppBarState.heightOffset
                    val target = topAppBarState.heightOffsetLimit
                    if (initial != target) {
                        topAppBarState.heightOffset = target
                    }
                }
            }
        }

    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()

    var pagingItems: LazyPagingItems<ArticleFlowItem>? by remember { mutableStateOf(null) }

    if (isTwoPane) {
        LaunchedEffect(readerState) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = pagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    scrollAppBarToCollapsed()
                    listState.animateScrollToItem(index, scrollOffset = -200)
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = pagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    snapAppBarToCollapsed()
                    listState.requestScrollToItem(index, scrollOffset = -400)
                }
            }
        }
    }

    val isSyncing = viewModel.isSyncingFlow.collectAsStateValue()

    // Triage comes back when you close an article that was opened from one of its cards.
    LaunchedEffect(readerState.articleId) {
        if (viewModel.triageResume && readerState.articleId == null) {
            viewModel.triageResume = false
            showTriage = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RYScaffold(
            containerTonalElevation = articleListTonalElevation.value.dp,
            topBar = {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography =
                        MaterialTheme.typography.copy(
                            headlineMedium = MaterialTheme.typography.displaySmall,
                            titleLarge =
                                MaterialTheme.typography.titleLarge.merge(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        ),
                ) {
                    LargeTopAppBar(
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
                        title = {
                            val textStyle = LocalTextStyle.current
                            val color = LocalContentColor.current
                            if (textStyle.fontSize.value > 18f) {
                                BasicText(
                                    modifier =
                                        Modifier.padding(
                                            start = 8.dp,
                                            end = 24.dp,
                                        ),
                                    text = titleText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    // Wide expressive heading for All, Unread, Starred and for
                                    // group and feed names. Long names shrink or wrap (2 lines).
                                    style =
                                        textStyle.copy(
                                            fontFamily = GoogleSansWideFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    color = { color },
                                    autoSize =
                                        TextAutoSize.StepBased(
                                            minFontSize = 28.sp,
                                            maxFontSize = textStyle.fontSize,
                                        ),
                                )
                            } else {
                                Text(
                                    text = titleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        expandedHeight = 172.dp,
                        scrollBehavior = scrollBehavior,
                        // Material 3 Expressive: tonal buttons, the actions connected into one
                        // pill. An action that is on (marking as read, searching) is filled.
                        navigationIcon = {
                            TonalCircleButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                description = stringResource(R.string.back),
                            ) {
                                onSearch = false
                                onNavigateUp()
                            }
                        },
                        actions = {
                            TopBarButtonGroup(
                                buildList {
                                    if (!filterUiState.filter.isStarred()) {
                                        add(
                                            TopBarAction(
                                                icon = Icons.Rounded.DoneAll,
                                                description = stringResource(R.string.mark_all_as_read),
                                                selected = markAsRead,
                                            ) {
                                                if (markAsRead) {
                                                    markAsRead = false
                                                } else {
                                                    scope
                                                        .launch {
                                                            if (listState.firstVisibleItemIndex != 0) {
                                                                listState.animateScrollToItem(0)
                                                            }
                                                        }
                                                        .invokeOnCompletion {
                                                            markAsRead = true
                                                            onSearch = false
                                                        }
                                                }
                                            }
                                        )
                                    }
                                    add(
                                        TopBarAction(
                                            icon = Icons.Rounded.Search,
                                            description = stringResource(R.string.search),
                                            selected = onSearch,
                                        ) {
                                            if (onSearch) {
                                                onSearch = false
                                            } else {
                                                scope
                                                    .launch {
                                                        if (listState.firstVisibleItemIndex != 0) {
                                                            listState.animateScrollToItem(0)
                                                        }
                                                    }
                                                    .invokeOnCompletion {
                                                        scope.launch {
                                                            onSearch = true
                                                            markAsRead = false
                                                            delay(100)
                                                            focusRequester.requestFocus()
                                                        }
                                                    }
                                            }
                                        }
                                    )
                                }
                            )
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                scrolledContainerColor = scrolledTopBarContainerColor
                            ),
                    )
                }
            },
            content = {
                RYExtensibleVisibility(modifier = Modifier.zIndex(1f), visible = onSearch) {
                    BackHandler(onSearch) { onSearch = false }
                    SearchBar(
                        value = filterUiState.searchContent ?: "",
                        placeholder =
                            when {
                                filterUiState.group != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.group.name,
                                    )

                                filterUiState.feed != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.feed.name,
                                    )

                                else ->
                                    stringResource(
                                        R.string.search_for,
                                        filterUiState.filter.toName(),
                                    )
                            },
                        focusRequester = focusRequester,
                        onValueChange = { viewModel.inputSearchContent(it) },
                        onClose = {
                            onSearch = false
                            viewModel.inputSearchContent(null)
                        },
                    )
                }

                RYExtensibleVisibility(markAsRead) {
                    BackHandler(markAsRead) { markAsRead = false }

                    MarkAsReadBar {
                        markAsRead = false
                        viewModel.updateReadStatus(
                            groupId = filterUiState.group?.id,
                            feedId = filterUiState.feed?.id,
                            articleId = null,
                            conditions = it,
                            isUnread = false,
                        )
                    }
                }
                val contentTransitionVertical =
                    sharedYAxisTransitionExpressive(direction = Direction.Forward)
                val contentTransitionBackward =
                    sharedXAxisTransitionSlow(direction = Direction.Backward)
                val contentTransitionForward =
                    sharedXAxisTransitionSlow(direction = Direction.Forward)
                AnimatedContent(
                    targetState = flowUiState,
                    contentKey = { it.pagerData.filterState.copy(searchContent = null) },
                    transitionSpec = {
                        val targetFilter = targetState.pagerData.filterState
                        val initialFilter = initialState.pagerData.filterState

                        if (targetFilter.filter.index > initialFilter.filter.index) {
                            contentTransitionForward
                        } else if (targetFilter.filter.index < initialFilter.filter.index) {
                            contentTransitionBackward
                        } else if (
                            targetFilter.group != initialFilter.group ||
                                targetFilter.feed != initialFilter.feed
                        ) {
                            contentTransitionVertical
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                ) { flowUiState ->
                    val pager = flowUiState.pagerData.pager
                    val filterState = flowUiState.pagerData.filterState
                    val pagingItems = pager.collectAsLazyPagingItems().also { pagingItems = it }

                    if (markAsReadOnScroll && filterState.filter.isUnread()) {
                        LaunchedEffect(listState.isScrollInProgress) {
                            if (!listState.isScrollInProgress) {
                                val firstItemKey =
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.contentType == CONTENT_TYPE_ARTICLE }
                                        ?.key
                                val items = mutableListOf<ArticleWithFeed>()
                                var found = false
                                val itemCount = pagingItems.itemCount
                                for (index in 0 until itemCount) {
                                    pagingItems.peek(index).let {
                                        if (it is ArticleFlowItem.Article) {
                                            if (it.articleWithFeed.article.id == firstItemKey) {
                                                found = true
                                                break
                                            }
                                            items.add(it.articleWithFeed)
                                        }
                                    }
                                }
                                if (items.isNotEmpty() && found) {
                                    viewModel.diffMapHolder.updateDiff(
                                        articleWithFeed = items.toTypedArray(),
                                        isUnread = false,
                                    )
                                }
                            }
                        }
                    }

                    if (settings.flowArticleListDateStickyHeader.value) {
                        LaunchedEffect(lastVisibleIndex) {
                            lastVisibleIndex.collect {
                                if (it in (pagingItems.itemCount - 25..pagingItems.itemCount - 1)) {
                                    pagingItems.get(it)
                                }
                            }
                        }
                    }

                    val listState = remember(pager) { listState }

                    val isSyncing by rememberUpdatedState(isSyncing)

                    LaunchedEffect(pagingItems) {
                        snapshotFlow { pagingItems.loadState.isIdle }
                            .collect {
                                if (isSyncing) {
                                    listState.scrollToItem(0)
                                }
                            }
                    }

                    val loadAction =
                        remember(pager, flowUiState, pullToSwitchFeed) {
                                when (pullToSwitchFeed) {
                                    PullToLoadNextFeedPreference.None -> null
                                    else -> {
                                        when {
                                            flowUiState.nextFilterState != null ->
                                                LoadAction.NextFeed.fromFilterState(
                                                    flowUiState.nextFilterState
                                                )

                                            filterState.filter.isUnread() &&
                                                pullToSwitchFeed ==
                                                    PullToLoadNextFeedPreference
                                                        .MarkAsReadAndLoadNextFeed ->
                                                LoadAction.MarkAllAsRead

                                            else -> null
                                        }
                                    }
                                }
                            }
                            .also { currentLoadAction = it }

                    val onLoadNext: (() -> Unit)? =
                        when (loadAction) {
                            is LoadAction.NextFeed -> viewModel::loadNextFeedOrGroup
                            LoadAction.MarkAllAsRead -> {
                                {
                                    viewModel.markAllAsRead()
                                    currentPullToLoadState?.animateDistanceTo(
                                        targetValue = 0f,
                                        animationSpec = settleSpec,
                                    )
                                }
                            }

                            else -> null
                        }

                    val onPullToSync: (() -> Unit)? =
                        if (isSyncing) null
                        else {
                            {
                                viewModel.sync()
                                currentPullToLoadState?.animateDistanceTo(
                                    targetValue = 0f,
                                    animationSpec = settleSpec,
                                )
                            }
                        }

                    val pullToLoadState =
                        rememberPullToLoadState(
                                key = pager,
                                onLoadNext = onLoadNext,
                                onLoadPrevious = onPullToSync,
                                loadThreshold = PullToLoadDefaults.loadThreshold(.1f),
                            )
                            .also { currentPullToLoadState = it }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier =
                                Modifier.nestedScroll(fabScrollConnection)
                                    .pullToLoad(
                                        state = pullToLoadState,
                                        enabled = true,
                                        contentOffsetY = { fraction ->
                                            if (fraction > 0f) {
                                                (fraction * ContentOffsetMultiple * 1.5f)
                                                    .dp
                                                    .roundToPx()
                                            } else {
                                                (fraction * ContentOffsetMultiple * 2f)
                                                    .dp
                                                    .roundToPx()
                                            }
                                        },
                                        onScroll = {
                                            if (it < -10f) {
                                                markAsRead = false
                                            }
                                        },
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .fillMaxSize()
                                    .drawVerticalScrollIndicator(listState),
                            state = listState,
                        ) {
                            ArticleList(
                                pagingItems = pagingItems,
                                diffMap = viewModel.diffMapHolder.diffMap,
                                isShowFeedIcon = articleListFeedIcon.value,
                                isShowStickyHeader = articleListDateStickyHeader.value,
                                articleListTonalElevation = articleListTonalElevation.value,
                                isSwipeEnabled = { listState.isScrollInProgress },
                                onClick = { articleWithFeed, index ->
                                    if (articleWithFeed.feed.isBrowser) {
                                        viewModel.diffMapHolder.updateDiff(
                                            articleWithFeed,
                                            isUnread = false,
                                        )
                                        context.openURL(
                                            articleWithFeed.article.link,
                                            openLink,
                                            openLinkSpecificBrowser,
                                        )
                                    } else {
                                        navigateToArticle(articleWithFeed.article.id, index)
                                    }
                                },
                                onToggleStarred = onToggleStarred,
                                onToggleRead = onToggleRead,
                                onMarkAboveAsRead = onMarkAboveAsRead,
                                onMarkBelowAsRead = onMarkBelowAsRead,
                                onShare = onShare,
                            )
                            item {
                                Spacer(modifier = Modifier.height(128.dp))
                                Spacer(
                                    modifier =
                                        Modifier.windowInsetsBottomHeight(
                                            WindowInsets.navigationBars
                                        )
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FlowActionsFab(
                    // [FloatingBarGap] above the bottom bar instead of the standard 16 dp above its
                    // 8 dp top padding.
                    modifier = Modifier.offset(y = 16.dp + 8.dp - FloatingBarGap),
                    visible = showActionsFab,
                    onGist = { showGist = true },
                    onTriage = {
                        // A new run each time the button is pressed.
                        viewModel.triageSession = null
                        viewModel.triageResume = false
                        showTriage = true
                    },
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            // The filter bar is drawn once for the whole app, above every screen (SharedFilterBar),
            // so it stays exactly in place while moving to and from Feeds. This keeps its space.
            bottomBar = { FilterBarSpace(filterBarStyle.value) },
        )
        ProvideFilterBar(
            FilterBarSpec(
                filter = filterUiState.filter,
                style = filterBarStyle.value,
                padding = filterBarPadding.dp,
                tonalElevation = filterBarTonalElevation.value.dp,
                // Triage is drawn over the whole screen, so the bar steps aside for it.
                enabled = showFilterBar && !showTriage,
                onClick = {
                    if (filterUiState.filter != it) {
                        viewModel.changeFilter(filterUiState.copy(filter = it))
                    } else {
                        scope.launch {
                            if (listState.firstVisibleItemIndex != 0) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    }
                },
            )
        )
        currentPullToLoadState?.let {
            PullToSyncIndicator(pullToLoadState = it, isSyncing = isSyncing)
            PullToLoadIndicator(
                state = it,
                loadAction = currentLoadAction,
                // At the same height as the AI gist / Triage button: [FloatingBarGap] above the bar.
                bottomPadding =
                    (if (filterBarStyle.value == FlowFilterBarStylePreference.Icon.value) 76.dp
                    else 88.dp) + FloatingBarGap,
                modifier =
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)
                    )
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        ),
            )
        }

        // Triage is a full-screen overlay (not a dialog), so the predictive back gesture can animate it.
        if (showTriage) {
            // The unread articles at the moment triage was opened. The session itself is kept in
            // the view model, so it survives reading an article and coming back.
            val session = remember {
                viewModel.triageSession
                    ?: TriageSession(
                            articles =
                                pagingItems
                                    ?.itemSnapshotList
                                    ?.filterIsInstance<ArticleFlowItem.Article>()
                                    ?.map { it.articleWithFeed }
                                    ?.filter { viewModel.diffMapHolder.checkIfUnread(it) }
                                    ?.distinctBy { it.article.id } ?: emptyList(),
                            // A feed wins over its group: after a feed comes the next feed.
                            groupId = if (filterUiState.feed == null) filterUiState.group?.id else null,
                            feedId = filterUiState.feed?.id,
                            title = filterUiState.feed?.name ?: filterUiState.group?.name,
                        )
                        .also { viewModel.triageSession = it }
            }
            TriageScreen(
                session = session,
                onRead = { viewModel.diffMapHolder.updateDiff(it, isUnread = false) },
                onUnread = { viewModel.diffMapHolder.updateDiff(it, isUnread = true) },
                onStar = { article, starred ->
                    viewModel.updateStarredStatus(
                        articleId = article.article.id,
                        isStarred = starred,
                    )
                },
                onOpen = { articleWithFeed ->
                    if (articleWithFeed.feed.isBrowser) {
                        // Triage stays open underneath; you return to it from the browser.
                        viewModel.diffMapHolder.updateDiff(articleWithFeed, isUnread = false)
                        context.openURL(
                            articleWithFeed.article.link,
                            openLink,
                            openLinkSpecificBrowser,
                        )
                    } else {
                        // Hide triage while the article is open; it returns when you go back.
                        viewModel.triageResume = !isTwoPane
                        showTriage = false
                        val index =
                            pagingItems
                                ?.itemSnapshotList
                                ?.indexOfFirst {
                                    it is ArticleFlowItem.Article &&
                                        it.articleWithFeed.article.id == articleWithFeed.article.id
                                }
                                ?.coerceAtLeast(0) ?: 0
                        navigateToArticle(articleWithFeed.article.id, index)
                    }
                },
                onClose = {
                    showTriage = false
                    viewModel.triageSession = null
                    viewModel.triageResume = false
                },
                findNext = { groupId, feedId, visited ->
                    viewModel.nextTriage(groupId, feedId, visited)
                },
                loadNext = { next -> viewModel.loadTriage(next) },
            )
        }
    }

    if (showGist) {
        val gistFilter = filterUiState.copy(searchContent = null)
        SummarySheet(
            requestKey = gistFilter,
            request = SummaryRequest.Gist(gistFilter),
            heading = stringResource(R.string.ai_gist_of, titleText),
            onDismissRequest = { showGist = false },
        )
    }
}
