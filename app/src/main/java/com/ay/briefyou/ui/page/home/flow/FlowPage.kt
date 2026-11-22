package com.ay.briefyou.ui.page.home.flow

// Brief You
import com.ay.briefyou.brief.list.briefAiActions
import com.ay.briefyou.brief.list.BriefFlowOverlays
import com.ay.briefyou.brief.list.briefPullToLoadInsets
import com.ay.briefyou.brief.list.briefPullToLoadPadding
import com.ay.briefyou.brief.list.rememberBriefFlowState
import com.ay.briefyou.brief.ui.GoogleSansWideFontFamily
import com.ay.briefyou.brief.ui.SharedFilterBarSlot
import com.ay.briefyou.brief.ui.TonalCircleButton
import com.ay.briefyou.brief.ui.TopBarAction
import com.ay.briefyou.brief.ui.TopBarButtonGroup
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
import androidx.compose.runtime.withFrameNanos // Brief You
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first // Brief You
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.domain.data.PagerData
import com.ay.briefyou.domain.model.article.ArticleFlowItem
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import com.ay.briefyou.infrastructure.preference.LocalFlowArticleListFeedIcon
import com.ay.briefyou.infrastructure.preference.LocalMarkAsReadOnScroll
import com.ay.briefyou.infrastructure.preference.LocalOpenLink
import com.ay.briefyou.infrastructure.preference.LocalOpenLinkSpecificBrowser
import com.ay.briefyou.infrastructure.preference.LocalSettings
import com.ay.briefyou.infrastructure.preference.LocalSharedContent
import com.ay.briefyou.infrastructure.preference.LocalSortUnreadArticles
import com.ay.briefyou.infrastructure.preference.PullToLoadNextFeedPreference
import com.ay.briefyou.infrastructure.preference.SortUnreadArticlesPreference
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.BYExtensibleVisibility
import com.ay.briefyou.ui.component.base.BYScaffold
import com.ay.briefyou.ui.component.scrollbar.VerticalScrollIndicatorFactory
import com.ay.briefyou.ui.component.scrollbar.drawVerticalScrollIndicator
import com.ay.briefyou.ui.component.scrollbar.scrollIndicator
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.openURL
import com.ay.briefyou.ui.motion.Direction
import com.ay.briefyou.ui.motion.sharedXAxisTransitionSlow
import com.ay.briefyou.ui.motion.sharedYAxisTransitionExpressive
import com.ay.briefyou.ui.page.adaptive.ArticleListReaderViewModel
import com.ay.briefyou.ui.page.home.reading.PullToLoadDefaults
import com.ay.briefyou.ui.page.home.reading.PullToLoadDefaults.ContentOffsetMultiple
import com.ay.briefyou.ui.page.home.reading.PullToLoadState
import com.ay.briefyou.ui.page.home.reading.pullToLoad
import com.ay.briefyou.ui.page.home.reading.rememberPullToLoadState

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
    /** False while the list is hidden (an article is open), so the filter bar slides away. */ // Brief You
    showFilterBar: Boolean = true,
    viewModel: ArticleListReaderViewModel,
    onNavigateUp: () -> Unit,
    navigateToArticle: (String, Int) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
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
    // Brief You: Nightly stutter log: the list's first row moving by itself (not scrolled by a finger).
    if (com.ay.briefyou.brief.perf.PerfLog.enabled) { // Brief You
        LaunchedEffect(listState) { // Brief You
            var last = -1 // Brief You
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }.collect { (row, scrolling) -> // Brief You
                if (last == -1) com.ay.briefyou.brief.perf.PerfLog.note("list: shown at row $row") // Brief You
                else if (!scrolling && kotlin.math.abs(row - last) >= 3) com.ay.briefyou.brief.perf.PerfLog.note("list: moved by itself from row $last to $row") // Brief You
                last = row // Brief You
            } // Brief You
        } // Brief You
    } // Brief You

    val scrolledTopBarContainerColor = MaterialTheme.colorScheme.surface

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
    val brief = rememberBriefFlowState(filterUiState) // Brief You
    var currentPullToLoadState: PullToLoadState? by remember { mutableStateOf(null) }
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

    // Brief You: the row tap, made once. Made inside the list's item builder, it was a new one every
    // time the page recomposed, so every row on screen was rebuilt (behind an open article too).
    val latestArticleClick =
        androidx.compose.runtime.rememberUpdatedState { articleWithFeed: ArticleWithFeed, index: Int -> // Brief You
            if (articleWithFeed.feed.isBrowser) { // Brief You
                viewModel.diffMapHolder.updateDiff(articleWithFeed, isUnread = false) // Brief You
                context.openURL(articleWithFeed.article.link, openLink, openLinkSpecificBrowser) // Brief You
            } else { // Brief You
                com.ay.briefyou.brief.perf.PerfLog.note("list: article opened from row ${listState.firstVisibleItemIndex}") // Brief You: Nightly stutter log
                com.ay.briefyou.brief.perf.PerfLog.action("Open an article", windowMs = 3_000) // Brief You: Nightly stutter log
                navigateToArticle(articleWithFeed.article.id, index) // Brief You
            } // Brief You
        } // Brief You
    val onArticleClick: (ArticleWithFeed, Int) -> Unit = // Brief You
        remember { { articleWithFeed, index -> latestArticleClick.value(articleWithFeed, index) } } // Brief You
    // Brief You: a pressed row's article is prepared for opening (see ArticlePress).
    androidx.compose.runtime.DisposableEffect(viewModel) { // Brief You
        val prepare: (ArticleWithFeed) -> Unit = { viewModel.prepareArticle(it) } // Brief You
        com.ay.briefyou.brief.reading.ArticlePress.onPress = prepare // Brief You
        onDispose { if (com.ay.briefyou.brief.reading.ArticlePress.onPress === prepare) com.ay.briefyou.brief.reading.ArticlePress.onPress = null } // Brief You
    } // Brief You

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
                    listState.animateScrollToItem(com.ay.briefyou.brief.list.lazyIndexOf(itemList.orEmpty(), index, articleListDateStickyHeader.value), scrollOffset = -200) // Brief You: the list's own row
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

                // Brief You: coming back from an article, leave the list where it was if that
                // article's row is still on screen; only jump when it isn't (e.g. after swiping
                // through several articles in the reader).
                if (listState.layoutInfo.totalItemsCount == 0) withFrameNanos {}
                val layout = listState.layoutInfo
                val onScreen =
                    layout.visibleItemsInfo.any {
                        it.key == articleId && // Brief You: by its key (the row numbers differ, see lazyIndexOf)
                            it.offset >= layout.viewportStartOffset &&
                            it.offset + it.size <= layout.viewportEndOffset
                    }
                if (index != -1 && !onScreen) {
                    snapAppBarToCollapsed()
                    listState.requestScrollToItem(com.ay.briefyou.brief.list.lazyIndexOf(itemList.orEmpty(), index, articleListDateStickyHeader.value), scrollOffset = -400) // Brief You: the list's own row
                }
            }
        }
    }

    val isSyncing = viewModel.isSyncingFlow.collectAsStateValue()

    Box(modifier = Modifier.fillMaxSize()) {
        BYScaffold(
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
                                // One word (like "Entertainment") never breaks in the middle: it // Brief You
                                // stays on one line and shrinks further. Several words may wrap
                                // onto two lines, between words.
                                val oneWord = !titleText.trim().contains(' ')
                                BasicText(
                                    modifier =
                                        Modifier.padding(
                                            start = 8.dp,
                                            end = 24.dp,
                                        ),
                                    text = titleText,
                                    maxLines = if (oneWord) 1 else 2,
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
                                            minFontSize = if (oneWord) 20.sp else 28.sp,
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
                        // Material 3 Expressive: tonal buttons, the actions connected into one // Brief You
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
                            TopBarButtonGroup( // Brief You
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
                BYExtensibleVisibility(modifier = Modifier.zIndex(1f), visible = onSearch) {
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

                BYExtensibleVisibility(markAsRead) {
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
                    // Brief You: the first articles of the list tidied ahead, so the first taps open at once.
                    LaunchedEffect(pagingItems) { // Brief You
                        // The first three articles, skipping the date headings between them. // Brief You
                        val first = snapshotFlow { (0 until minOf(10, pagingItems.itemCount)).mapNotNull { (pagingItems.peek(it) as? ArticleFlowItem.Article)?.articleWithFeed }.take(3) } // Brief You
                            .first { it.size == 3 || (it.isNotEmpty() && pagingItems.itemCount <= 10) } // Brief You
                        kotlinx.coroutines.delay(600) // Brief You: after the list's opening slide
                        com.ay.briefyou.brief.reading.FeedContent.prepareAhead(first) // Brief You
                    } // Brief You
                    if (com.ay.briefyou.brief.perf.PerfLog.enabled) { // Brief You: Nightly stutter log
                        LaunchedEffect(pagingItems) { // Brief You
                            snapshotFlow { pagingItems.itemCount to pagingItems.loadState.refresh.javaClass.simpleName } // Brief You
                                .collect { (rows, refresh) -> com.ay.briefyou.brief.perf.PerfLog.note("list: $rows rows, $refresh") } // Brief You
                        } // Brief You
                    } // Brief You

                    if (markAsReadOnScroll && filterState.filter.isUnread()) {
                        val briefSeen = com.ay.briefyou.brief.list.rememberSeenArticles(listState, pager) // Brief You
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
                                            // Brief You: only articles that were on screen (#1274)
                                            if (it.articleWithFeed.article.id in briefSeen) items.add(it.articleWithFeed)
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
                    val swipeEnabled: () -> Boolean = remember(listState) { { listState.isScrollInProgress } } // Brief You: made once

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
                                    // Brief You: inbox zero, only when something was still unread
                                    brief.markAllAsRead(pagingItems.itemSnapshotList.items, viewModel.diffMapHolder) { viewModel.markAllAsRead() }
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
                                Modifier.pullToLoad(
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
                                isSwipeEnabled = swipeEnabled, // Brief You: made once (see onArticleClick)
                                listState = listState, // Brief You: the pinned day's line
                                onClick = onArticleClick, // Brief You: made once
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
            bottomBar = {
                // Brief You: one bar for the whole app (it stays in place between screens); hidden
                // under Triage.
                SharedFilterBarSlot(
                    enabled = showFilterBar && !brief.showTriage,
                    filter = filterUiState.filter,
                    // The AI button (AI Gist and Triage), in Unread.
                    ai = briefAiActions(brief, filterUiState, onSearch),
                ) {
                    if (filterUiState.filter != it) {
                        com.ay.briefyou.brief.perf.PerfLog.action("List: switch filter") // Brief You: Nightly stutter log
                        viewModel.changeFilter(filterUiState.copy(filter = it))
                    } else {
                        scope.launch {
                            if (listState.firstVisibleItemIndex != 0) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    }
                }
            },
        )
        currentPullToLoadState?.let {
            PullToSyncIndicator(pullToLoadState = it, isSyncing = isSyncing)
            PullToLoadIndicator(
                state = it,
                loadAction = currentLoadAction,
                bottomPadding = briefPullToLoadPadding, // Brief You
                modifier =
                    Modifier.windowInsetsPadding(briefPullToLoadInsets) // Brief You
                        .windowInsetsPadding(
                            WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)
                        ),
            )
        }

        // Brief You: inbox zero, Triage and the AI gist sheet.
        BriefFlowOverlays(
            state = brief,
            viewModel = viewModel,
            filterUiState = filterUiState,
            titleText = titleText,
            isTwoPane = isTwoPane,
            openArticleId = readerState.articleId,
            pagingItems = pagingItems,
            navigateToArticle = navigateToArticle,
        )
    }
}
