@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package me.ash.reader.brief.feeds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.merge
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.page.home.feeds.FeedsViewModel

/*
 * Brief You's additions to Read You's Feeds screen (FeedsPage), kept here so Read You's file only
 * needs a few calls: Starred, Unread and All as pages you can swipe between, and the sort sheet.
 */

/** The Feeds pages: one per filter (in the order of the bar at the bottom), each with its own scroll position. */
@Stable
class BriefFeedsPager internal constructor(
    internal val pagerState: PagerState,
    internal val listStates: List<LazyListState>,
) {
    /** The list on the page you're looking at. */
    val listState: LazyListState
        get() = listStates[pagerState.currentPage.coerceIn(0, listStates.lastIndex)]
}

@Composable
fun rememberBriefFeedsPager(filterState: FilterState, feedsViewModel: FeedsViewModel): BriefFeedsPager {
    val hapticFeedback = LocalHapticFeedback.current
    val filters = Filter.values
    val currentPage = filters.indexOf(filterState.filter).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentPage) { filters.size }
    val listStates = List(filters.size) { rememberLazyListState() }
    val pager = remember(pagerState) { BriefFeedsPager(pagerState, listStates) }

    // Tapping the bar (or a filter changed on another screen): slide to that page. While it
    // slides past the pages in between, those must not become the filter.
    val latestFilterState by rememberUpdatedState(filterState)
    var slidingToPage by remember { mutableStateOf(false) }
    val pageSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(currentPage) {
        // A swipe changes the filter itself, half way; the pager is then already on that page and
        // finishes the swipe on its own. Animating here too would fight your finger (and the fling)
        // and make swiping feel laggy, so only a filter the pager isn't on yet slides it.
        if (pagerState.currentPage != currentPage) {
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
        merge(snapshotFlow { pagerState.currentPage }, snapshotFlow { pagerState.settledPage })
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
    return pager
}

/**
 * Takes the place of Read You's PullToRefreshBox on the Feeds screen: the same pull to refresh
 * (with the Expressive indicator) around swipeable Starred / Unread / All pages. [content] is Read
 * You's list, given each page's filter state, groups, count line and list state under the names
 * Read You's code already uses, so that code stays as it is.
 */
@Composable
fun BriefFeedsPages(
    pager: BriefFeedsPager,
    @Suppress("UNUSED_PARAMETER") feedsViewModel: FeedsViewModel,
    filterState: FilterState,
    state: PullToRefreshState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content:
        @Composable
        (
            filterState: FilterState,
            groupWithFeedList: List<GroupWithFeed>,
            importantSum: String,
            listState: LazyListState,
        ) -> Unit,
) {
    val lists = hiltViewModel<BriefFeedsViewModel>().lists
    val filters = Filter.values
    val pagerState = pager.pagerState
    PullToRefreshBox(
        state = state,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        // Material 3 Expressive shape-morphing refresh indicator.
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { filters[it].index },
            // All three pages stay ready, so the one you swipe to is already filled in.
            beyondViewportPageCount = filters.size - 1,
        ) { page ->
            val pageFilter = filters[page]
            // Each page has its own list and count line, so a page never shows another filter's
            // groups, even for a moment while swiping.
            val pageGroups by lists.groups(pageFilter).collectAsState()
            val pageSum by lists.importantSum(pageFilter).collectAsState()
            Box(
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        // The page you swipe away from shrinks and dims a little, the one coming
                        // in grows into place.
                        val offset =
                            ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                .absoluteValue
                                .coerceIn(0f, 1f)
                        val scale = 1f - 0.06f * offset
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.4f * offset
                    }
            ) {
                content(
                    filterState.copy(filter = pageFilter),
                    pageGroups,
                    pageSum,
                    pager.listStates[page],
                )
            }
        }
    }
}

/** The sheet to sort groups and feeds (opened from the sort button on the Feeds screen). */
@Composable
fun BriefFeedSortSheet(visible: Boolean, onDismiss: () -> Unit) {
    val sortViewModel: FeedSortViewModel = hiltViewModel()
    val settings = sortViewModel.settings.collectAsStateValue()
    FeedSortSheet(
        visible = visible,
        settings = settings,
        onDismiss = onDismiss,
        onGroupMode = sortViewModel::setGroupMode,
        onFeedMode = sortViewModel::setFeedMode,
    )
}
