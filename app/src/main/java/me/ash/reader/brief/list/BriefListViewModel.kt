package me.ash.reader.brief.list

import androidx.lifecycle.ViewModel
import androidx.paging.PagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.brief.triage.TriageLeft
import me.ash.reader.brief.triage.TriageNext
import me.ash.reader.brief.triage.TriageSession
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider

/**
 * Brief You's state and data for the article list and reading screen (kept apart from Read You's
 * ArticleListReaderViewModel): the Triage run and what it continues with, and article previews
 * for the next article card. It lives as long as the list and reading screen do, so Triage
 * survives reading an article opened from it.
 */
@HiltViewModel
class BriefListViewModel
@Inject
constructor(
    private val rssService: RssService,
    private val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val settingsProvider: SettingsProvider,
    private val androidStringsHelper: AndroidStringsHelper,
    @param:IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** The current triage run, kept here so it survives reading an article opened from triage. */
    var triageSession: TriageSession? = null

    /** Set when triage opened an article: triage reappears once the article is closed. */
    var triageResume: Boolean = false

    /**
     * What triage can continue with, in the Feeds page order, that still has unread articles and
     * was not triaged yet in this session.
     * - Started from a feed: the next feed, one group after the other.
     * - Started from a group: the next group.
     * Both look after the current one first, then wrap round to the ones before it. Null when
     * triage was opened on everything (nothing is left to move on to).
     */
    fun nextTriage(groupId: String?, feedId: String?, visited: Set<String>): TriageNext? {
        if (groupId == null && feedId == null) return null
        // The counts are unread counts only while the Unread filter is on.
        if (!filterStateUseCase.filterStateFlow.value.filter.isUnread()) return null
        val groups = groupWithFeedsListUseCase.groupWithFeedListFlow.value

        if (feedId != null) {
            val feeds = groups.flatMap { it.feeds }
            val start = feeds.indexOfFirst { it.id == feedId }
            val ordered = if (start < 0) feeds else feeds.drop(start + 1) + feeds.take(start)
            return ordered
                .firstOrNull { it.id !in visited && it.important > 0 }
                ?.let { TriageNext(it.id, it.name, it.important, isFeed = true) }
        }

        val start = groups.indexOfFirst { it.group.id == groupId }
        val ordered = if (start < 0) groups else groups.drop(start + 1) + groups.take(start)
        for (item in ordered) {
            if (item.group.id in visited) continue
            val unread = item.feeds.sumOf { it.important }
            if (unread > 0) return TriageNext(item.group.id, item.group.name, unread)
        }
        return null
    }

    /**
     * Everything unread when a Triage run starts, for its progress ring: the unread articles in all
     * groups (or, for a run started from a feed, all feeds), and how many of them have any.
     */
    fun triageTotals(feeds: Boolean): Pair<Int, Int> {
        if (!filterStateUseCase.filterStateFlow.value.filter.isUnread()) return 0 to 0
        val groups = groupWithFeedsListUseCase.groupWithFeedListFlow.value
        val counts =
            if (feeds) groups.flatMap { it.feeds }.map { it.important }
            else groups.map { g -> g.feeds.sumOf { it.important } }
        return counts.sum() to counts.count { it > 0 }
    }

    /**
     * Unread articles outside the feed or group the list shows ([feedId] wins over [groupId]), for
     * inbox zero: the celebration only plays when nothing is unread anywhere. 0 for the list of
     * everything.
     */
    fun unreadOutside(feedId: String?, groupId: String?): Int {
        val groups = groupWithFeedsListUseCase.groupWithFeedListFlow.value
        return when {
            feedId != null -> groups.flatMap { it.feeds }.filter { it.id != feedId }.sumOf { it.important }
            groupId != null -> groups.filter { it.group.id != groupId }.sumOf { g -> g.feeds.sumOf { it.important } }
            else -> 0
        }
    }

    /**
     * The unread articles left outside the groups (or feeds) triaged in this session, and in how
     * many groups (or feeds) they are, for Triage's end screen. Null when that isn't known (triage
     * opened on everything, or the list isn't showing unread articles).
     */
    fun triageLeft(groupId: String?, feedId: String?, visited: Set<String>): TriageLeft? {
        if (groupId == null && feedId == null) return null
        if (!filterStateUseCase.filterStateFlow.value.filter.isUnread()) return null
        val groups = groupWithFeedsListUseCase.groupWithFeedListFlow.value
        return if (feedId != null) {
            val left = groups.flatMap { it.feeds }.filter { it.id !in visited && it.important > 0 }
            TriageLeft(unread = left.sumOf { it.important }, places = left.size, isFeeds = true)
        } else {
            val left =
                groups
                    .filter { it.group.id !in visited }
                    .map { it.feeds.sumOf { feed -> feed.important } }
                    .filter { it > 0 }
            TriageLeft(unread = left.sum(), places = left.size, isFeeds = false)
        }
    }

    /** The unread articles of a group or feed, newest first like the list, for triage to continue. */
    suspend fun loadTriage(next: TriageNext): List<ArticleWithFeed> =
        withContext(ioDispatcher) {
            val source =
                rssService
                    .get()
                    .pullArticles(
                        groupId = if (next.isFeed) null else next.id,
                        feedId = if (next.isFeed) next.id else null,
                        isStarred = false,
                        isUnread = true,
                        sortAscending = settingsProvider.settings.flowSortUnreadArticles.value,
                    )
            val result =
                source.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 200,
                        placeholdersEnabled = false,
                    )
                )
            (result as? PagingSource.LoadResult.Page)
                ?.data
                .orEmpty()
                .filter { diffMapHolder.checkIfUnread(it) }
                .distinctBy { it.article.id }
                .onEach {
                    it.article.dateString =
                        androidStringsHelper.formatAsString(
                            date = it.article.date,
                            onlyHourMinute = true,
                        )
                }
        }

    /** An article's title, feed and picture, for the next article card at the end of an article. */
    suspend fun previewArticle(id: String): ArticleWithFeed? =
        runCatching { rssService.get().findArticleById(id) }.getOrNull()
}
