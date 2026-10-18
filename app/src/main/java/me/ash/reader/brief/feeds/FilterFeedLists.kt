package me.ash.reader.brief.feeds

import androidx.compose.ui.util.fastFilteredMap
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import me.ash.reader.R
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.ext.getDefaultGroupId

/**
 * The groups and feeds, and the count line ("123 unread"), of any filter, not just the current
 * one. The Feeds screen shows Starred, Unread and All side by side as pages (see BriefFeedsPages),
 * and each page needs its own list so swiping never shows another filter's groups. Each list is
 * in the user's order (see [FeedOrdering]) and shared while someone watches it.
 *
 * The counting follows Read You's GroupWithFeedsListUseCase (which only keeps the current
 * filter's list), so a fix there may need copying here.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class FilterFeedLists
@Inject
constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsProvider: SettingsProvider,
    private val rssService: RssService,
    private val diffMapHolder: DiffMapHolder,
    private val accountService: AccountService,
    private val androidStringsHelper: AndroidStringsHelper,
    private val feedOrdering: FeedOrdering,
) {
    private val accountFlow = accountService.currentAccountFlow.mapNotNull { it }

    private val feedsFlow: StateFlow<List<GroupWithFeed>> =
        accountFlow
            .flatMapLatest { rssService.get(it.type.id).pullFeeds() }
            .stateIn(applicationScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val defaultGroupId
        get() = accountService.getCurrentAccountId().getDefaultGroupId()

    private val hideEmptyGroups
        get() = settingsProvider.settings.hideEmptyGroups.value

    private val lists = mutableMapOf<Int, StateFlow<List<GroupWithFeed>>>()
    private val sums = mutableMapOf<Int, StateFlow<String>>()

    /** The groups and feeds with [filter]'s counts, in the user's order. */
    fun groups(filter: Filter): StateFlow<List<GroupWithFeed>> =
        synchronized(lists) {
            lists.getOrPut(filter.index) {
                feedOrdering.sorted(
                    accountFlow.flatMapLatest {
                        when (filter) {
                            Filter.Unread -> unreadFeeds()
                            Filter.Starred -> starredFeeds()
                            else -> allFeeds()
                        }
                    },
                    applicationScope,
                    ioDispatcher,
                    SharingStarted.WhileSubscribed(5_000),
                )
            }
        }

    /** [filter]'s count line ("123 unread"). */
    fun importantSum(filter: Filter): StateFlow<String> =
        synchronized(sums) {
            sums.getOrPut(filter.index) {
                accountFlow
                    .flatMapLatest {
                        when (filter) {
                            Filter.Starred ->
                                rssService
                                    .get()
                                    .pullImportant(isStarred = true, isUnread = false)
                                    .mapLatest {
                                        val sum = it.values.sum()
                                        androidStringsHelper.getQuantityString(
                                            R.plurals.starred_desc,
                                            sum,
                                            sum,
                                        )
                                    }
                            Filter.Unread ->
                                diffMapHolder.diffMapSnapshotFlow
                                    .combine(
                                        rssService
                                            .get()
                                            .pullImportant(isStarred = false, isUnread = true)
                                    ) { diffMap, unreadCountMap ->
                                        val sum =
                                            unreadCountMap.values.sum() +
                                                diffMap.values.sumOf { if (it.isUnread) 1 else -1 }
                                        androidStringsHelper.getQuantityString(
                                            R.plurals.unread_desc,
                                            sum,
                                            sum,
                                        )
                                    }
                                    .debounce(200L)
                            else ->
                                rssService
                                    .get()
                                    .pullImportant(isStarred = false, isUnread = false)
                                    .mapLatest {
                                        val sum = it.values.sum()
                                        androidStringsHelper.getQuantityString(
                                            R.plurals.all_desc,
                                            sum,
                                            sum,
                                        )
                                    }
                        }
                    }
                    .flowOn(ioDispatcher)
                    .stateIn(applicationScope, SharingStarted.WhileSubscribed(5_000), "")
            }
        }

    private fun allFeeds(): Flow<List<GroupWithFeed>> =
        feedsFlow
            .combine(rssService.get().pullImportant(isStarred = false, isUnread = false)) {
                groupWithFeedsList,
                articleCountMap ->
                groupWithFeedsList.fastFilteredMap(
                    predicate = { it.group.id != defaultGroupId || it.feeds.isNotEmpty() },
                    transform = {
                        val feedList =
                            it.feeds.map { feed -> feed.copy(important = articleCountMap[feed.id] ?: 0) }
                        it.copy(feeds = feedList.toMutableList())
                    },
                )
            }
            .flowOn(ioDispatcher)

    private fun starredFeeds(): Flow<List<GroupWithFeed>> =
        feedsFlow
            .combine(rssService.get().pullImportant(isStarred = true, isUnread = false)) {
                groupWithFeedsList,
                starredCountMap ->
                groupWithFeedsList.mapNotNull { groupItem ->
                    val feedList =
                        groupItem.feeds.fastMap { feed ->
                            feed.copy(important = starredCountMap[feed.id] ?: 0)
                        }
                    keep(groupItem, feedList)
                }
            }
            .flowOn(ioDispatcher)

    private fun unreadFeeds(): Flow<List<GroupWithFeed>> =
        combine(
                feedsFlow,
                rssService.get().pullImportant(isStarred = false, isUnread = true),
                diffMapHolder.diffMapSnapshotFlow,
            ) { groupWithFeedsList, unreadCountMap, diffMap ->
                val unreadDiffs = diffMap.values.filter { it.isUnread }
                val readDiffs = diffMap.values.filterNot { it.isUnread }
                groupWithFeedsList.mapNotNull { groupItem ->
                    val feedList =
                        groupItem.feeds.map { feed ->
                            val feedId = feed.id
                            val count =
                                (unreadCountMap[feedId] ?: 0) +
                                    unreadDiffs.count { it.feedId == feedId } -
                                    readDiffs.count { it.feedId == feedId }
                            feed.copy(important = count.coerceAtLeast(0))
                        }
                    keep(groupItem, feedList)
                }
            }
            .debounce(200L)
            .flowOn(ioDispatcher)

    /** A group with its counted feeds, or null when it's hidden (empty, as Read You does). */
    private fun keep(
        groupItem: GroupWithFeed,
        feedList: List<me.ash.reader.domain.model.feed.Feed>,
    ): GroupWithFeed? {
        val item =
            if (hideEmptyGroups) {
                val filteredFeeds = feedList.filterNot { it.important == 0 }
                if (filteredFeeds.isEmpty()) return null
                groupItem.copy(feeds = filteredFeeds.toMutableList())
            } else {
                groupItem.copy(feeds = feedList.toMutableList())
            }
        return item.takeIf { it.group.id != defaultGroupId || it.feeds.isNotEmpty() }
    }
}

/** Gives the Feeds screen's pages their lists (see [FilterFeedLists]). */
@HiltViewModel
class BriefFeedsViewModel @Inject constructor(val lists: FilterFeedLists) : ViewModel()
