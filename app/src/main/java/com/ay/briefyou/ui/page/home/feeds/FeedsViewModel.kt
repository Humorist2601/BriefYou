package com.ay.briefyou.ui.page.home.feeds

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.account.Account
import com.ay.briefyou.domain.model.general.Filter
import com.ay.briefyou.domain.service.AccountService
import com.ay.briefyou.domain.service.RssService
import com.ay.briefyou.infrastructure.android.AndroidStringsHelper
import com.ay.briefyou.domain.data.DiffMapHolder
import com.ay.briefyou.domain.data.FilterState
import com.ay.briefyou.domain.data.FilterStateUseCase
import com.ay.briefyou.domain.data.GroupWithFeedsListUseCase
import com.ay.briefyou.domain.service.SyncWorker
import com.ay.briefyou.infrastructure.di.ApplicationScope
import com.ay.briefyou.infrastructure.di.DefaultDispatcher
import com.ay.briefyou.infrastructure.di.IODispatcher
import com.ay.briefyou.infrastructure.preference.SettingsProvider
import javax.inject.Inject

private const val TAG = "FeedsViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val accountService: AccountService,
    private val rssService: RssService,
    private val workManager: WorkManager,
    private val androidStringsHelper: AndroidStringsHelper,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val settingsProvider: SettingsProvider,
    private val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val articlePagingListUseCase: com.ay.briefyou.domain.data.ArticlePagingListUseCase, // Brief You
    private val articleDao: com.ay.briefyou.domain.repository.ArticleDao, // Brief You: the hero card's extra lines
) : ViewModel() {

    private val _feedsUiState =
        MutableStateFlow(FeedsUiState())
    val feedsUiState: StateFlow<FeedsUiState> = _feedsUiState.asStateFlow()

    val syncWorkLiveData = workManager.getWorkInfosByTagLiveData(SyncWorker.SYNC_TAG)

    val filterStateFlow = filterStateUseCase.filterStateFlow
    val groupWithFeedsListFlow = groupWithFeedsListUseCase.groupWithFeedListFlow

    var currentJob: Job? = null

    fun sync() {
        applicationScope.launch(ioDispatcher) {
            rssService.get().doSyncOneTime()
        }
    }

    fun commitDiffs() = diffMapHolder.commitDiffsToDb()

    // Brief You: a feed or group pressed on this page: its list is made and its first rows read now,
    // while the finger is still down, so they are there from the list's first frame.
    fun warmList(filterState: FilterState) {
        articlePagingListUseCase.prepare(filterState) // Brief You: its list made and first rows read on the press
    }

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(filterState)
    }

    private val accountFlow = accountService.currentAccountFlow

    /** Brief You: whether the account has any feed at all (null while it is read). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val hasFeeds: StateFlow<Boolean?> =
        accountFlow
            .filterNotNull()
            .flatMapLatest { rssService.get(it.type.id).pullFeeds() }
            .map<List<com.ay.briefyou.domain.model.group.GroupWithFeed>, Boolean?> { groups -> groups.any { it.feeds.isNotEmpty() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Brief You: when the oldest unread article was published (milliseconds), for the hero card. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val oldestUnread: StateFlow<Long?> =
        accountFlow
            .filterNotNull()
            .flatMapLatest { articleDao.queryOldestUnreadDate(it.id ?: -1).debounce(500) } // the screen follows at most twice a second during a sync
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val newSince = MutableStateFlow(0L)

    /** Brief You: how many unread articles came out after [since] (milliseconds); the hero card's "new since you last opened". */
    fun setNewSince(since: Long) {
        newSince.value = since
    }

    private val todayStart = MutableStateFlow(startOfToday())

    private fun startOfToday(): Long =
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Brief You: call when Home comes back, so "today" moves on at midnight. */
    fun refreshToday() {
        todayStart.value = startOfToday()
    }

    /** Brief You: how many articles came out since midnight, for the All page's hero card. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val cameOutToday: StateFlow<Int> =
        accountFlow
            .filterNotNull()
            .combine(todayStart) { account, since -> (account.id ?: -1) to since }
            .flatMapLatest { (id, since) -> articleDao.countSince(id, since).debounce(500) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val newSinceOpen: StateFlow<Int> =
        accountFlow
            .filterNotNull()
            .combine(newSince) { account, since -> (account.id ?: -1) to since }
            .flatMapLatest { (id, since) -> if (since <= 0L) kotlinx.coroutines.flow.flowOf(0) else articleDao.countUnreadSince(id, since).debounce(500) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            accountFlow.collect { account ->
                _feedsUiState.update { it.copy(account = account) }
            }
        }
        viewModelScope.launch {
            filterStateUseCase.filterStateFlow.mapLatest { it.filter }
                .combine(accountFlow) { filter, account ->
                    filter
                }
                .collect {
                    currentJob?.cancel()
                    currentJob = when (it) {
                        Filter.Unread -> pullUnreadFeeds()
                        Filter.Starred -> pullStarredFeeds()
                        else -> pullAllFeeds()
                    }
                }
        }
    }

    private fun pullAllFeeds(): Job {
        val articleCountMapFlow =
            rssService.get().pullImportant(isStarred = false, isUnread = false)

        return viewModelScope.launch {
            launch {
                articleCountMapFlow.mapLatest {
                    val sum = it.values.sum()
                    androidStringsHelper.getQuantityString(R.plurals.all_desc, sum, sum)
                }.flowOn(defaultDispatcher).collect { text ->
                    _feedsUiState.update { it.copy(importantSum = text) }
                }
            }
        }
    }

    private fun pullStarredFeeds(): Job {
        val starredCountMap = rssService.get().pullImportant(isStarred = true, isUnread = false)

        return viewModelScope.launch {
            starredCountMap.mapLatest {
                val sum = it.values.sum()
                androidStringsHelper.getQuantityString(R.plurals.starred_desc, sum, sum)
            }.flowOn(defaultDispatcher).collect { text ->
                _feedsUiState.update { it.copy(importantSum = text) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun pullUnreadFeeds(): Job {
        val unreadCountMapFlow = rssService.get().pullImportant(isStarred = false, isUnread = true)

        return viewModelScope.launch {
            diffMapHolder.diffMapSnapshotFlow
                .combine(
                    unreadCountMapFlow
                ) { diffMap, unreadCountMap ->
                    val sum = unreadCountMap.values.sum()
                    val combinedSum =
                        sum + diffMap.values.sumOf { if (it.isUnread) 1.toInt() else -1 } // KT-46360
                    androidStringsHelper.getQuantityString(
                        R.plurals.unread_desc,
                        combinedSum,
                        combinedSum
                    )
                }.debounce(200L).flowOn(defaultDispatcher).collect { text ->
                    _feedsUiState.update { it.copy(importantSum = text) }
                }
        }
    }

//    @OptIn(ExperimentalCoroutinesApi::class)
//    fun pullFeeds(filterState: FilterState, hideEmptyGroups: Boolean) {
//        val isStarred = filterState.filter.isStarred()
//        val isUnread = filterState.filter.isUnread()
//        _feedsUiState.update {
//            val important = rssService.get().pullImportant(isStarred, isUnread)
//            it.copy(
////                importantSum = important
////                    .mapLatest {
////                        (it["sum"] ?: 0).run {
////                            androidStringsHelper.getQuantityString(
////                                when {
////                                    isStarred -> R.plurals.starred_desc
////                                    isUnread -> R.plurals.unread_desc
////                                    else -> R.plurals.all_desc
////                                },
////                                this,
////                                this
////                            )
////                        }
////                    }.flowOn(defaultDispatcher),
//                groupWithFeedList = combine(
//                    important,
//                    rssService.get().pullFeeds()
//                ) { importantMap, groupWithFeedList ->
//                    val groupIterator = groupWithFeedList.iterator()
//                    while (groupIterator.hasNext()) {
//                        val groupWithFeed = groupIterator.next()
//                        val groupImportant = importantMap[groupWithFeed.group.id] ?: 0
//                        if (hideEmptyGroups && (isStarred || isUnread) && groupImportant == 0) {
//                            groupIterator.remove()
//                            continue
//                        }
//                        groupWithFeed.group.important = groupImportant
//                        val feedIterator = groupWithFeed.feeds.iterator()
//                        while (feedIterator.hasNext()) {
//                            val feed = feedIterator.next()
//                            val feedImportant = importantMap[feed.id] ?: 0
//                            groupWithFeed.group.feeds++
//                            if (hideEmptyGroups && (isStarred || isUnread) && feedImportant == 0) {
//                                feedIterator.remove()
//                                continue
//                            }
//                            feed.important = feedImportant
//                        }
//                    }
//                    groupWithFeedList
//                }.mapLatest { list ->
//                    list.filter { (group, feeds) ->
//                        group.id != feedsUiState.value.account?.id?.getDefaultGroupId() || feeds.isNotEmpty()
//                    }
//                }.flowOn(defaultDispatcher),
//            )
//        }
//    }
}

data class FeedsUiState(
    val account: Account? = null,
    val importantSum: String = "",
    val listState: LazyListState = LazyListState(),
    val groupsVisible: SnapshotStateMap<String, Boolean> = mutableStateMapOf(),
)
