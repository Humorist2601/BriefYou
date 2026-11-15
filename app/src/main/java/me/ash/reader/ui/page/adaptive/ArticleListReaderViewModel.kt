package me.ash.reader.ui.page.adaptive

import me.ash.reader.brief.reading.FeedContent // Brief You
import android.net.Uri
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlin.collections.any
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter // Brief You
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async // Brief You
import kotlinx.coroutines.Deferred // Brief You
import me.ash.reader.domain.data.ArticlePagingListUseCase
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.MarkAsReadConditions
import me.ash.reader.domain.service.GoogleReaderRssService
import me.ash.reader.domain.service.LocalRssService
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.infrastructure.android.AndroidImageDownloader
import me.ash.reader.brief.readaloud.TextToSpeechManager // Brief You
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import timber.log.Timber

private const val TAG = "FlowViewModel"

@OptIn(FlowPreview::class)
@HiltViewModel()
class ArticleListReaderViewModel
@Inject
constructor(
    private val rssService: RssService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val settingsProvider: SettingsProvider,
    private val readerCacheHelper: ReaderCacheHelper,
    val textToSpeechManager: TextToSpeechManager,
    private val imageDownloader: AndroidImageDownloader,
    private val articleListUseCase: ArticlePagingListUseCase,
    workManager: WorkManager,
) : ViewModel() {

    // Brief You: the list made for the filter shown now, never the one before it. The list for a
    // new feed is made in the background, so for a moment the old one can still be there; the
    // screen then slid in with the previous feed's list and animated over to the new one, building
    // two lists at once.
    private fun isCurrent(pagerData: PagerData) =
        pagerData.filterState == filterStateUseCase.filterStateFlow.value

    val flowUiState: StateFlow<FlowUiState?> =
        articleListUseCase.pagerFlow
            .filter { isCurrent(it) } // Brief You
            .combine(groupWithFeedsListUseCase.groupWithFeedListFlow) {
                pagerData,
                groupWithFeedsList ->
                val filterState = pagerData.filterState
                var nextFilterState: FilterState? = null
                if (filterState.group != null) {
                    val groupList = groupWithFeedsList.map { it.group }
                    val index = groupList.indexOfFirst { it.id == filterState.group.id }
                    if (index != -1) {
                        val nextGroup = groupList.getOrNull(index + 1)
                        if (nextGroup != null) {
                            nextFilterState = filterState.copy(group = nextGroup)
                        }
                    } else {
                        val allGroupList =
                            rssService.get().queryAllGroupWithFeeds().map { it.group }
                        val index = allGroupList.indexOfFirst { it.id == filterState.group.id }
                        if (index != -1) {
                            val nextGroup =
                                allGroupList.subList(index, allGroupList.size).fastFirstOrNull {
                                    groupList.map { it.id }.contains(it.id)
                                }
                            if (nextGroup != null) {
                                nextFilterState = filterState.copy(group = nextGroup)
                            }
                        }
                    }
                } else if (filterState.feed != null) {
                    val feedList = groupWithFeedsList.flatMap { it.feeds }
                    val index = feedList.indexOfFirst { it.id == filterState.feed.id }
                    if (index != -1) {
                        val nextFeed = feedList.getOrNull(index + 1)
                        if (nextFeed != null) {
                            nextFilterState = filterState.copy(feed = nextFeed)
                        }
                    } else {
                        val allFeedList =
                            rssService.get().queryAllGroupWithFeeds().flatMap { it.feeds }
                        val index = allFeedList.indexOfFirst { it.id == filterState.feed.id }
                        if (index != -1) {
                            val nextFeed =
                                allFeedList.subList(index, allFeedList.size).fastFirstOrNull {
                                    feedList.map { it.id }.contains(it.id)
                                }
                            if (nextFeed != null) {
                                nextFilterState = filterState.copy(feed = nextFeed)
                            }
                        }
                    }
                }
                FlowUiState(nextFilterState = nextFilterState, pagerData = pagerData)
            }
            // Brief You: ready from the screen's first frame when its list is already made (the
            // next feed or group is filled in a moment later when it needs the database). It used
            // to start empty, so the first frame was blank and the whole page was built mid-slide.
            .stateIn(viewModelScope, SharingStarted.Eagerly, firstFlowUiState())

    /** Brief You: the list screen's state at once, from what is in memory (see [flowUiState]). */
    private fun firstFlowUiState(): FlowUiState? {
        val pagerData = articleListUseCase.pagerFlow.value
        if (!isCurrent(pagerData)) return null
        val filterState = pagerData.filterState
        val groups = groupWithFeedsListUseCase.groupWithFeedListFlow.value
        val next =
            when {
                filterState.group != null ->
                    groups.map { it.group }.let { list ->
                        list.indexOfFirst { it.id == filterState.group.id }.takeIf { it != -1 }
                            ?.let { list.getOrNull(it + 1) }?.let { filterState.copy(group = it) }
                    }
                filterState.feed != null ->
                    groups.flatMap { it.feeds }.let { list ->
                        list.indexOfFirst { it.id == filterState.feed.id }.takeIf { it != -1 }
                            ?.let { list.getOrNull(it + 1) }?.let { filterState.copy(feed = it) }
                    }
                else -> null
            }
        return FlowUiState(nextFilterState = next, pagerData = pagerData)
    }

    private val syncWorkerStatusFlow =
        workManager
            .getWorkInfosByTagFlow(SyncWorker.SYNC_TAG)
            .map { it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow = _isSyncingFlow.asStateFlow()

    init {
        viewModelScope.launch {
            syncWorkerStatusFlow.debounce(500L).collect { _isSyncingFlow.value = it }
        }
    }

    fun updateReadStatus(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        conditions: MarkAsReadConditions,
        isUnread: Boolean,
    ) {
        applicationScope.launch(ioDispatcher) {
            rssService
                .get()
                .markAsRead(
                    groupId = groupId,
                    feedId = feedId,
                    articleId = articleId,
                    before = conditions.toDate(),
                    isUnread = isUnread,
                )
        }
    }

    fun updateStarredStatus(articleId: String?, isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            if (articleId != null) {
                rssService.get().markAsStarred(articleId = articleId, isStarred = isStarred)
            }
        }
    }

    fun markAsReadFromListByDate(date: Date, isBefore: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val items =
                articleListUseCase.itemSnapshotList
                    .filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }
                    .filter {
                        if (isBefore) {
                            date > it.article.date && it.article.isUnread
                        } else {
                            date < it.article.date && it.article.isUnread
                        }
                    }
                    .distinctBy { it.article.id }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun loadNextFeedOrGroup() {
        viewModelScope.launch {
            if (
                settingsProvider.settings.pullToSwitchFeed ==
                    PullToLoadNextFeedPreference.MarkAsReadAndLoadNextFeed
            ) {
                markAllAsRead()
            }
            flowUiState.value?.nextFilterState?.let { filterStateUseCase.updateFilterState(it) }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val items =
                articleListUseCase.itemSnapshotList.items
                    .filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun sync() {
        diffMapHolder.commitDiffsToDb()
        viewModelScope.launch {
            _isSyncingFlow.value = true
            val isSyncing = syncWorkerStatusFlow.value
            if (!isSyncing) {
                delay(1000L)
                if (syncWorkerStatusFlow.value == false) {
                    _isSyncingFlow.value = false
                }
            }
        }
        applicationScope.launch(ioDispatcher) {
            val filterState = filterStateUseCase.filterStateFlow.value
            val service = rssService.get()
            when (service) {
                is LocalRssService ->
                    service.doSyncOneTime(
                        feedId = filterState.feed?.id,
                        groupId = filterState.group?.id,
                    )

                is GoogleReaderRssService ->
                    service.doSyncOneTime(
                        feedId = filterState.feed?.id,
                        groupId = filterState.group?.id,
                    )

                else -> service.doSyncOneTime()
            }
        }
    }

    fun resetFilter() =
        filterStateUseCase.updateFilterState(feed = null, group = null, searchContent = null)

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(
            filterState.feed,
            filterState.group,
            filterState.filter,
        )
    }

    fun inputSearchContent(content: String? = null) {
        if (content != filterStateUseCase.filterStateFlow.value.searchContent)
            filterStateUseCase.updateFilterState(searchContent = content)
    }

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private val _readerState: MutableStateFlow<ReaderState> = MutableStateFlow(ReaderState())
    val readerStateStateFlow = _readerState.asStateFlow()

    private val currentArticle: Article?
        get() = readingUiState.value.articleWithFeed?.article

    private val currentFeed: Feed?
        get() = readingUiState.value.articleWithFeed?.feed

    /** Brief You: the article already started by [openNow] (the pane asks again when it opens). */
    private var openedEarly: String? = null
    private var openNowAt = 0L // Brief You: Nightly stutter log

    /**
     * Brief You: starts the article as it is tapped, before its page is made, so with a prepared
     * article the page has it in its first frame (it was started a few frames later, once the
     * page was there, from the page itself).
     */
    fun openNow(articleId: String, listIndex: Int?) {
        openedEarly = articleId
        openNowAt = android.os.SystemClock.uptimeMillis() // Nightly stutter log
        initData(articleId, listIndex)
    }

    fun initData(articleId: String, listIndex: Int? = null) {
        if (openedEarly != null) { // Brief You
            val early = openedEarly // Brief You
            openedEarly = null // Brief You
            if (early == articleId) return // Brief You: already started on the tap
        } // Brief You
        viewModelScope.launch {
            val snapshotList = articleListUseCase.itemSnapshotList

            val itemByIndex =
                listIndex?.let { snapshotList.getOrNull(it) as? ArticleFlowItem.Article }

            val itemFromList =
                if (itemByIndex != null && itemByIndex.articleWithFeed.article.id != articleId) {
                    itemByIndex
                } else {
                    snapshotList.find { item ->
                        item is ArticleFlowItem.Article &&
                            item.articleWithFeed.article.id == articleId
                    } as? ArticleFlowItem.Article
                }

            // Brief You: list rows leave out the article's text (so lists load quickly), so the
            // article is read here in full, by its id.
            val listItem = itemByIndex?.articleWithFeed ?: itemFromList?.articleWithFeed
            // Brief You: read already when the row was pressed (see prepareArticle), else now.
            val targetId = listItem?.article?.id ?: articleId // Brief You
            val ready = preparedArticle?.takeIf { it.first == targetId }?.second // Brief You
            preparedArticle = null // Brief You
            val waitFrom = android.os.SystemClock.uptimeMillis() // Brief You: Nightly stutter log
            if (openNowAt > 0) me.ash.reader.brief.perf.PerfLog.note("open: started ${waitFrom - openNowAt} ms after the tap") // Brief You: Nightly stutter log
            openNowAt = 0L // Brief You
            val wasReady = ready?.isCompleted == true // Brief You
            val readyArticle = ready?.let { runCatching { it.await() }.getOrNull() } // Brief You
            me.ash.reader.brief.perf.PerfLog.note( // Brief You: Nightly stutter log
                when { // Brief You
                    ready == null -> "open: not prepared, reading it now" // Brief You
                    wasReady -> "open: prepared article ready" // Brief You
                    else -> "open: waited ${android.os.SystemClock.uptimeMillis() - waitFrom} ms for the prepared article" // Brief You
                } // Brief You
            ) // Brief You
            val item =
                readyArticle?.first // Brief You
                    ?: rssService.get().findArticleById(targetId) ?: listItem!! // Brief You

            if (diffMapHolder.checkIfUnread(item)) {
                diffMapHolder.updateDiff(item, isUnread = false)
            }
            item.run {
                _readingUiState.update {
                    it.copy(articleWithFeed = this, isStarred = article.isStarred, isUnread = false)
                }
                _readerState.update {
                    it.copy(
                            articleId = article.id,
                            feedName = feed.name,
                            title = article.title,
                            author = me.ash.reader.brief.reading.EmailContent.readableAuthor(article.author), // Brief You: no "delivery_…@bounce" senders
                            link = article.link,
                            publishedDate = article.date,
                        )
                        .prefetchArticleId()
                        .let { state -> // Brief You: the text tidied on the press when it was
                            readyArticle?.second?.let { state.copy(content = it) } ?: state.renderContent(this)
                        }
                        .also { me.ash.reader.brief.perf.PerfLog.note("open: article on the page (${if (it.content is ReaderState.Loading) "text still loading" else "with its text"})") } // Brief You: Nightly stutter log
                }
            }
        }
    }

    /**
     * Brief You: an article row is pressed. The article is read and its text tidied now, while the
     * finger is still down (a tap lasts about 0.1 s), so when it opens the page has its title and
     * text from its first frame and is seen sliding in (it slid in empty, and everything popped in
     * as the slide ended). [initData] takes it when the same article is opened.
     */
    private var preparedArticle: Pair<String, Deferred<Pair<ArticleWithFeed, ReaderState.ContentState?>>>? = null

    fun prepareArticle(listItem: ArticleWithFeed) {
        val id = listItem.article.id
        if (preparedArticle?.first == id) return
        preparedArticle?.second?.cancel()
        preparedArticle =
            id to
                viewModelScope.async {
                    val started = android.os.SystemClock.uptimeMillis()
                    val item = rssService.get().findArticleById(id) ?: listItem
                    val read = android.os.SystemClock.uptimeMillis()
                    val content =
                        if (item.feed.isFullContent) {
                            // Only a full text already kept; one still to fetch is fetched on opening.
                            readerCacheHelper.readFullContent(id).getOrNull()?.let {
                                ReaderState.FullContent(FeedContent.forReaderInBackground(it, item.article.link, item.article.title))
                            }
                        } else {
                            ReaderState.Description(FeedContent.forReaderInBackground(item.article.rawDescription, item.article.link, item.article.title))
                        }
                    me.ash.reader.brief.perf.PerfLog.note(
                        "prepared: read in ${read - started} ms, text tidied in ${android.os.SystemClock.uptimeMillis() - read} ms" +
                            if (content == null) " (full text still to fetch)" else ""
                    ) // Nightly stutter log
                    item to content
                }
    }

    fun clearReadingData() {
        _readingUiState.update { ReadingUiState() }
        _readerState.update { ReaderState() }
    }

    suspend fun ReaderState.renderContent(articleWithFeed: ArticleWithFeed): ReaderState {
        val contentState =
            if (articleWithFeed.feed.isFullContent) {
                val fullContent =
                    readerCacheHelper.readFullContent(articleWithFeed.article.id).getOrNull()
                if (fullContent != null) ReaderState.FullContent(FeedContent.forReaderInBackground(fullContent, articleWithFeed.article.link, articleWithFeed.article.title)) // Brief You: tidied, off the main thread
                else {
                    renderFullContent()
                    ReaderState.Loading
                }
            } else ReaderState.Description(FeedContent.forReaderInBackground(articleWithFeed.article.rawDescription, articleWithFeed.article.link, articleWithFeed.article.title)) // Brief You: off the main thread

        return copy(content = contentState)
    }

    fun renderDescriptionContent() {
        // Brief You: tidied off the main thread (it was done where the button was tapped)
        viewModelScope.launch {
            val tidied = FeedContent.forReaderInBackground(currentArticle?.rawDescription ?: "", currentArticle?.link, currentArticle?.title)
            _readerState.update { it.copy(content = ReaderState.Description(content = tidied)) }
        }
    }

    fun renderFullContent() {
        val fetchJob =
            viewModelScope.launch {
                readerCacheHelper
                    .readOrFetchFullContent(currentArticle!!)
                    .onSuccess { content ->
                        _readerState.update {
                            it.copy(content = ReaderState.FullContent(content = FeedContent.forReaderInBackground(content, currentArticle?.link, currentArticle?.title))) // Brief You: off the main thread
                        }
                    }
                    .onFailure { th ->
                        _readerState.update {
                            it.copy(content = ReaderState.Error(th.message.toString()))
                        }
                    }
            }
        viewModelScope.launch {
            delay(100L)
            if (fetchJob.isActive) {
                setLoading()
            }
        }
    }

    fun updateReadStatus(isUnread: Boolean) {
        readingUiState.value.articleWithFeed?.let {
            diffMapHolder.updateDiff(it, isUnread = isUnread)
        }
        _readingUiState.update {
            it.copy(isUnread = diffMapHolder.checkIfUnread(it.articleWithFeed!!))
        }
    }

    fun updateStarredStatus(isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            _readingUiState.update { it.copy(isStarred = isStarred) }
            currentArticle?.let {
                rssService.get().markAsStarred(articleId = it.id, isStarred = isStarred)
            }
        }
    }

    private fun setLoading() {
        _readerState.update { it.copy(content = ReaderState.Loading) }
    }

    fun ReaderState.prefetchArticleId(): ReaderState {
        val items = articleListUseCase.itemSnapshotList
        val currentId = currentArticle?.id
        val index =
            items.indexOfFirst { item ->
                item is ArticleFlowItem.Article && item.articleWithFeed.article.id == currentId
            }
        var previousArticle: ReaderState.PrefetchResult? = null
        var nextArticle: ReaderState.PrefetchResult? = null

        if (index != -1 || currentId == null) {
            val prevIterator = items.listIterator(index)
            while (prevIterator.hasPrevious()) {
                val previousIndex = prevIterator.previousIndex()
                val prev = prevIterator.previous()
                if (prev is ArticleFlowItem.Article) {
                    previousArticle =
                        ReaderState.PrefetchResult(
                            articleId = prev.articleWithFeed.article.id,
                            index = previousIndex,
                        )
                    break
                }
            }
            val nextIterator = items.listIterator(index + 1)
            while (nextIterator.hasNext()) {
                val nextIndex = nextIterator.nextIndex()
                val next = nextIterator.next()
                if (
                    next is ArticleFlowItem.Article && next.articleWithFeed.article.id != currentId
                ) {
                    nextArticle =
                        ReaderState.PrefetchResult(
                            articleId = next.articleWithFeed.article.id,
                            index = nextIndex,
                        )
                    break
                }
            }
        }

        Timber.d("$previousArticle, $nextArticle, $listIndex")
        return copy(nextArticle = nextArticle, previousArticle = previousArticle, listIndex = index)
    }

    fun downloadImage(
        url: String,
        onSuccess: (Uri) -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            imageDownloader.downloadImage(url).onSuccess(onSuccess).onFailure(onFailure)
        }
    }
}

data class FlowUiState(val pagerData: PagerData, val nextFilterState: FilterState? = null)

data class ReadingUiState(
    val articleWithFeed: ArticleWithFeed? = null,
    val isUnread: Boolean = false,
    val isStarred: Boolean = false,
)

data class ReaderState(
    val articleId: String? = null,
    val feedName: String = "",
    val title: String? = null,
    val author: String? = null,
    val link: String? = null,
    val publishedDate: Date = Date(0L),
    val content: ContentState = Loading,
    val listIndex: Int? = null,
    val nextArticle: PrefetchResult? = null,
    val previousArticle: PrefetchResult? = null,
) {
    data class PrefetchResult(val articleId: String, val index: Int)

    sealed interface ContentState {
        val text: String?
            get() {
                return when (this) {
                    is Description -> content
                    is Error -> message
                    is FullContent -> content
                    Loading -> null
                }
            }
    }

    data class FullContent(val content: String) : ContentState

    data class Description(val content: String) : ContentState

    data class Error(val message: String) : ContentState

    data object Loading : ContentState
}
