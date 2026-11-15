package me.ash.reader.domain.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.ItemSnapshotList
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.cachedIn
import javax.inject.Inject
import kotlin.text.trim
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job // Brief You
import kotlinx.coroutines.SupervisorJob // Brief You
import kotlinx.coroutines.cancel // Brief You
import kotlinx.coroutines.flow.first // Brief You
import kotlinx.coroutines.withTimeoutOrNull // Brief You
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.mapPagingFlowItem
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider

class ArticlePagingListUseCase
@Inject
constructor(
    private val rssService: RssService,
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsProvider: SettingsProvider,
    private val filterStateUseCase: FilterStateUseCase,
    private val accountService: AccountService,
) {

    private val mutablePagerFlow =
        MutableStateFlow<PagerData>(
            PagerData(filterState = filterStateUseCase.filterStateFlow.value)
        )
    val pagerFlow: StateFlow<PagerData> = mutablePagerFlow

    var itemSnapshotList by
        mutableStateOf(
            ItemSnapshotList<ArticleFlowItem>(
                placeholdersBefore = 0,
                placeholdersAfter = 0,
                items = emptyList(),
            )
        )
        private set

    val pagingDataPresenter =
        object : PagingDataPresenter<ArticleFlowItem>() {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<ArticleFlowItem>) {
                itemSnapshotList = snapshot()
            }
        }

    // Brief You: the list of one group or feed, as it is made when its filter is chosen.
    private fun makePagerData(filterState: FilterState, scope: CoroutineScope = applicationScope): PagerData { // Brief You
        val searchContent = filterState.searchContent // Brief You
        return PagerData(
            Pager(
                    config = PagingConfig(pageSize = 50, enablePlaceholders = false)
                ) {
                    if (!searchContent.isNullOrBlank()) {
                        rssService
                            .get()
                            .searchArticles(
                                content = searchContent.trim(),
                                groupId = filterState.group?.id,
                                feedId = filterState.feed?.id,
                                isStarred = filterState.filter.isStarred(),
                                isUnread = filterState.filter.isUnread(),
                                sortAscending =
                                    settingsProvider.settings.flowSortUnreadArticles
                                        .value,
                            )
                    } else {
                        rssService
                            .get()
                            .pullArticles(
                                groupId = filterState.group?.id,
                                feedId = filterState.feed?.id,
                                isStarred = filterState.filter.isStarred(),
                                isUnread = filterState.filter.isUnread(),
                                sortAscending =
                                    settingsProvider.settings.flowSortUnreadArticles
                                        .value,
                            )
                    }
                }
                .flow
                .map { it.mapPagingFlowItem(androidStringsHelper) }
                .cachedIn(scope), // Brief You
            filterState = filterState,
        )
    } // Brief You

    /**
     * Brief You: a group or feed is pressed. Its list is made and its first rows read now, while the
     * finger is still down (a tap lasts about 0.1 s), so when it opens its rows are there from the
     * first frame, built before the slide moves instead of in the middle of it. Pressed but not
     * opened (a scroll), the next press or opening replaces it.
     */
    @Volatile private var prepared: PagerData? = null
    private var preparing: Job? = null
    /** The prepared list's own scope: ended when it isn't the one opened, so it reads nothing more. */
    @Volatile private var preparedScope: CoroutineScope? = null

    fun prepare(filterState: FilterState) {
        preparing?.cancel()
        if (prepared != null) preparedScope?.cancel()
        val scope = CoroutineScope(applicationScope.coroutineContext + SupervisorJob(applicationScope.coroutineContext[Job]))
        val data = makePagerData(filterState, scope)
        preparedScope = scope
        prepared = data
        preparing =
            applicationScope.launch(ioDispatcher) {
                // Something has to collect the list for its first rows to be read (and kept by
                // cachedIn); once they are, this stops, and the list's own collector takes over.
                val presenter =
                    object : PagingDataPresenter<ArticleFlowItem>() {
                        override suspend fun presentPagingDataEvent(event: PagingDataEvent<ArticleFlowItem>) {}
                    }
                val collecting = launch { data.pager.collectLatest { presenter.collectFrom(it) } }
                withTimeoutOrNull(3_000) { presenter.onPagesUpdatedFlow.first() }
                collecting.cancel()
            }
    }

    init {
        applicationScope.launch(ioDispatcher) {
            filterStateUseCase.filterStateFlow
                .combine(accountService.currentAccountIdFlow) { filterState, accountId ->
                    filterState
                }
                .collect { filterState ->
                    me.ash.reader.brief.perf.PerfLog.note("new list pager") // Brief You

                    // Brief You: the list prepared while the finger was on this group or feed, when it is the one opened.
                    val ready = prepared?.takeIf { it.filterState == filterState } // Brief You
                    if (prepared != null && ready == null) preparedScope?.cancel() // Brief You: pressed, not opened
                    prepared = null // Brief You
                    mutablePagerFlow.value = ready ?: makePagerData(filterState) // Brief You
                }
        }
        applicationScope.launch {
            pagerFlow.collectLatest { (pager, _) ->
                pager.collectLatest { pagingDataPresenter.collectFrom(it) }
            }
        }
    }
}

data class PagerData(
    val pager: Flow<PagingData<ArticleFlowItem>> = emptyFlow(),
    val filterState: FilterState = FilterState(),
)
