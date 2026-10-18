package me.ash.reader.brief.list

import me.ash.reader.brief.app.BriefShortcuts
import me.ash.reader.brief.stats.ReadingStats
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.brief.ai.SummaryRequest
import me.ash.reader.brief.ai.SummarySheet
import me.ash.reader.brief.triage.TriageScreen
import me.ash.reader.brief.triage.TriageSession
import me.ash.reader.brief.ui.ScreenOverlay
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.model.article.ArticleFlowItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.ui.unit.Dp
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.brief.ui.FloatingBarGap
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel

/*
 * Brief You's additions to Read You's article list (FlowPage), kept here so Read You's file only
 * needs a few one-line calls: the AI gist / Triage button, Triage, the gist sheet and the inbox
 * zero celebration.
 */

/** Brief You's state on the article list. */
@Stable
class BriefFlowState internal constructor(private val triage: MutableState<Boolean>) {
    /** The AI gist sheet is open. */
    var showGist by mutableStateOf(false)

    /** Triage is open (kept across configuration changes). */
    var showTriage: Boolean
        get() = triage.value
        set(value) {
            triage.value = value
        }

    /**
     * The AI gist / Triage button is hidden by your finger. Like the reading page's bar, only two
     * things move it, so it can never pop in and out: dragging towards the end of the list hides
     * it, dragging back shows it; and a new list (another feed, group or filter) shows it, once.
     * It doesn't look at the list's scroll position: while one feed slides into the next, the two
     * lists briefly share it, which made the button show, hide and show again.
     */
    internal var fabHiddenByFinger by mutableStateOf(false)

    /** The inbox zero celebration is showing. */
    internal var celebrate by mutableStateOf(false)

    /** Put on the list (Modifier.nestedScroll) so dragging it can hide and show the button. */
    val listScroll: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -1f) fabHiddenByFinger = true
                    else if (available.y > 1f) fabHiddenByFinger = false
                }
                return Offset.Zero
            }
        }

    /** After "mark all as read" from swiping up on the Unread list: the inbox zero celebration. */
    fun onMarkedAllRead() {
        if (!showTriage) celebrate = true
    }
}

@Composable
fun rememberBriefFlowState(filterUiState: FilterState): BriefFlowState {
    val triage = rememberSaveable { mutableStateOf(false) }
    val state = remember { BriefFlowState(triage) }
    LaunchedEffect(filterUiState.copy(searchContent = null)) { state.fabHiddenByFinger = false }
    return state
}

/** The AI gist / Triage button, for the scaffold's floating action button slot. */
@Composable
fun BriefFlowFab(
    state: BriefFlowState,
    viewModel: ArticleListReaderViewModel,
    filterUiState: FilterState,
    searching: Boolean,
    briefViewModel: BriefListViewModel = hiltViewModel(),
) {
    FlowActionsFab(
        // [FloatingBarGap] above the bottom bar instead of the standard 16 dp above its 8 dp top
        // padding.
        modifier = Modifier.offset(y = 16.dp + 8.dp - FloatingBarGap),
        // Only in Unread, and not while searching.
        visible = filterUiState.filter.isUnread() && !searching && !state.fabHiddenByFinger,
        onGist = { state.showGist = true },
        onTriage = {
            // A new run each time the button is pressed.
            briefViewModel.triageSession = null
            briefViewModel.triageResume = false
            state.showTriage = true
        },
    )
}

/**
 * Brief You's overlays on the article list: inbox zero, Triage (full screen, over the list) and
 * the AI gist sheet. Put last inside the list's outer Box.
 */
@Composable
fun BriefFlowOverlays(
    state: BriefFlowState,
    viewModel: ArticleListReaderViewModel,
    filterUiState: FilterState,
    titleText: String,
    isTwoPane: Boolean,
    openArticleId: String?,
    pagingItems: LazyPagingItems<ArticleFlowItem>?,
    navigateToArticle: (String, Int) -> Unit,
    briefViewModel: BriefListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Opens links the way chosen in the settings ("open links in").
    val uriHandler = LocalUriHandler.current

    // Inbox zero, in the exact middle of the screen, over everything (Triage has its own, at the
    // end of the cards).
    // App shortcuts: Triage or the gist, once the Unread list of everything is showing.
    val pendingShortcut = BriefShortcuts.pending
    LaunchedEffect(pendingShortcut, filterUiState) {
        val ready =
            filterUiState.filter.isUnread() && filterUiState.feed == null && filterUiState.group == null
        if (pendingShortcut == null || !ready) return@LaunchedEffect
        BriefShortcuts.pending = null
        when (pendingShortcut) {
            BriefShortcuts.Action.Triage -> {
                briefViewModel.triageSession = null
                briefViewModel.triageResume = false
                state.showTriage = true
            }
            BriefShortcuts.Action.Gist -> state.showGist = true
            else -> {}
        }
    }

    if (state.celebrate) {
        LaunchedEffect(Unit) { ReadingStats.inboxZero(context) } // Reading stats
        ScreenOverlay {
            InboxZeroCelebration(visible = true, onFinished = { state.celebrate = false })
        }
    }

    // Triage comes back when you close an article that was opened from one of its cards.
    LaunchedEffect(openArticleId) {
        if (briefViewModel.triageResume && openArticleId == null) {
            briefViewModel.triageResume = false
            state.showTriage = true
        }
    }

    // Triage is a full-screen overlay (not a dialog), so the predictive back gesture can animate it.
    if (state.showTriage) {
        // The unread articles at the moment triage was opened. The session itself is kept in the
        // view model, so it survives reading an article and coming back.
        val session = remember {
            briefViewModel.triageSession
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
                    .also { briefViewModel.triageSession = it }
        }
        TriageScreen(
            session = session,
            onRead = { viewModel.diffMapHolder.updateDiff(it, isUnread = false) },
            onUnread = { viewModel.diffMapHolder.updateDiff(it, isUnread = true) },
            onStar = { article, starred ->
                viewModel.updateStarredStatus(articleId = article.article.id, isStarred = starred)
            },
            onOpen = { articleWithFeed ->
                if (articleWithFeed.feed.isBrowser) {
                    // Triage stays open underneath; you return to it from the browser.
                    viewModel.diffMapHolder.updateDiff(articleWithFeed, isUnread = false)
                    uriHandler.openUri(articleWithFeed.article.link)
                } else {
                    // Hide triage while the article is open; it returns when you go back.
                    briefViewModel.triageResume = !isTwoPane
                    state.showTriage = false
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
                state.showTriage = false
                briefViewModel.triageSession = null
                briefViewModel.triageResume = false
            },
            findNext = { groupId, feedId, visited -> briefViewModel.nextTriage(groupId, feedId, visited) },
            loadNext = { next -> briefViewModel.loadTriage(next) },
        )
    }

    if (state.showGist) {
        val gistFilter = filterUiState.copy(searchContent = null)
        SummarySheet(
            requestKey = gistFilter,
            request = SummaryRequest.Gist(gistFilter),
            heading = stringResource(R.string.ai_gist_of, titleText),
            onDismissRequest = { state.showGist = false },
        )
    }
}

/**
 * The pull-to-load hint's distance from the bottom: at the same height as the AI gist / Triage
 * button, [FloatingBarGap] above the filter bar.
 */
fun briefPullToLoadPadding(filterBarStyle: Int): Dp =
    (if (filterBarStyle == FlowFilterBarStylePreference.Icon.value) 76.dp else 88.dp) +
        FloatingBarGap

/** The pull-to-load hint stays above the navigation bar. */
val briefPullToLoadInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
