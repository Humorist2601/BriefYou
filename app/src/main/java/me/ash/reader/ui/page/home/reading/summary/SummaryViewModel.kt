package me.ash.reader.ui.page.home.reading.summary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.GistItem
import me.ash.reader.infrastructure.ai.ModelState
import me.ash.reader.infrastructure.android.TextToSpeechManager

/** What the AI sheet is working on. */
sealed interface SummaryRequest {
    data class Article(val articleId: String, val title: String?, val html: String) :
        SummaryRequest

    /** Everything in the list the user is looking at (a group, a feed, or all articles). */
    data class Gist(val filterState: FilterState) : SummaryRequest
}

sealed interface SummaryUiState {
    /** No model imported yet: show setup instructions. */
    data object NeedsModel : SummaryUiState

    data class Importing(val progress: Float?) : SummaryUiState

    /** Model is loading into memory, or the first tokens haven't arrived yet. */
    data class Thinking(val articleCount: Int? = null) : SummaryUiState

    data class Streaming(
        val text: String,
        val done: Boolean,
        val articleCount: Int? = null,
    ) : SummaryUiState

    data class Error(val message: String) : SummaryUiState
}

@HiltViewModel
class SummaryViewModel
@Inject
constructor(
    private val summarizer: GemmaSummarizer,
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val rssService: RssService,
    val textToSpeechManager: TextToSpeechManager,
) : ViewModel() {

    private val _state = MutableStateFlow<SummaryUiState>(SummaryUiState.Thinking())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    /** Number of gist articles marked read from the sheet (null = not yet). */
    private val _gistMarkedRead = MutableStateFlow<Int?>(null)
    val gistMarkedRead: StateFlow<Int?> = _gistMarkedRead.asStateFlow()

    private var job: Job? = null
    private var importWatch: Job? = null
    private var request: SummaryRequest? = null
    private var gistItems: List<GistItem> = emptyList()
    private var startedReading = false

    fun start(newRequest: SummaryRequest) {
        request = newRequest
        load()
    }

    /** Write the summary again. Summaries are never saved, so this always starts fresh. */
    fun regenerate() = load()

    fun retry() = load()

    private fun load() {
        val req = request ?: return
        job?.cancel()
        _gistMarkedRead.value = null
        if (!summarizer.hasModel()) {
            _state.value = SummaryUiState.NeedsModel
            return
        }
        when (req) {
            is SummaryRequest.Article -> {
                if (req.html.isBlank()) {
                    _state.value = SummaryUiState.Error("The article hasn't finished loading yet.")
                    return
                }
                _state.value = SummaryUiState.Thinking()
                job =
                    viewModelScope.launch {
                        collect(summarizer.summarize(req.title, req.html), null)
                    }
            }
            is SummaryRequest.Gist -> {
                _state.value = SummaryUiState.Thinking()
                job =
                    viewModelScope.launch {
                        val items = loadGistItems(req.filterState)
                        gistItems = items
                        if (items.isEmpty()) {
                            _state.value = SummaryUiState.Error("There are no articles in this list.")
                            return@launch
                        }
                        _state.value = SummaryUiState.Thinking(items.size)
                        val name = req.filterState.group?.name ?: req.filterState.feed?.name
                        collect(summarizer.gist(name, items), articleCount = items.size)
                    }
            }
        }
    }

    /** Streams [flow] into the UI state. Returns the final text, or null if it failed. */
    private suspend fun collect(flow: Flow<String>, articleCount: Int?): String? {
        var latest = ""
        var failed = false
        flow
            .catch { e ->
                failed = true
                _state.value = SummaryUiState.Error(e.message ?: e.javaClass.simpleName)
            }
            .collect {
                latest = it
                _state.value = SummaryUiState.Streaming(it, done = false, articleCount)
            }
        if (failed) return null
        _state.value = SummaryUiState.Streaming(latest, done = true, articleCount)
        return latest
    }

    private suspend fun loadGistItems(filter: FilterState): List<GistItem> =
        withContext(Dispatchers.IO) {
            val accountId = accountService.getCurrentAccountId()
            val unread = filter.filter.isUnread()
            val starred = filter.filter.isStarred()
            val rows =
                when {
                    filter.group != null ->
                        articleDao.queryForGistByGroupId(
                            accountId, filter.group.id, unread, starred, GIST_LIMIT)
                    filter.feed != null ->
                        articleDao.queryForGistByFeedId(
                            accountId, filter.feed.id, unread, starred, GIST_LIMIT)
                    else -> articleDao.queryForGist(accountId, unread, starred, GIST_LIMIT)
                }
            rows.map {
                GistItem(it.article.id, it.feed.name, it.article.title, it.article.shortDescription)
            }
        }

    /** Marks every article the gist was based on as read (synced to the account like a swipe). */
    fun markGistAsRead() {
        val ids = gistItems.map { it.articleId }.toSet()
        if (ids.isEmpty() || _gistMarkedRead.value != null) return
        _gistMarkedRead.value = ids.size
        viewModelScope.launch(Dispatchers.IO) {
            rssService.get().batchMarkAsRead(articleIds = ids, isUnread = false)
        }
    }

    fun importModel(uri: Uri) {
        job?.cancel()
        summarizer.importModel(uri)
        importWatch?.cancel()
        importWatch =
            viewModelScope.launch {
                summarizer.modelState.collect { model ->
                    when (model) {
                        is ModelState.Importing ->
                            _state.value = SummaryUiState.Importing(model.progress)
                        is ModelState.Ready -> {
                            retry()
                            importWatch?.cancel()
                        }
                        is ModelState.ImportFailed -> {
                            _state.value = SummaryUiState.Error("Import failed: ${model.message}")
                            importWatch?.cancel()
                        }
                        ModelState.Missing -> Unit
                    }
                }
            }
    }

    /** Long press on read aloud: stop. */
    fun stopReading() {
        textToSpeechManager.stop()
        startedReading = false
    }

    /** Reads the result aloud, or pauses or plays what is being read. */
    fun readAloud(text: String, title: String? = null) {
        val tts = textToSpeechManager
        // While something is being read aloud, the button pauses or plays it.
        when (tts.state) {
            is TextToSpeechManager.State.Reading -> tts.pause()
            is TextToSpeechManager.State.Paused -> tts.resume()
            TextToSpeechManager.State.Preparing -> {}
            else -> {
                // Drop the bullet glyphs so they aren't read out.
                tts.readPlainText(
                    text.lines().joinToString("\n") { it.trimStart('•', '*', '-', ' ') },
                    title = title,
                )
                startedReading = true
            }
        }
    }

    /** Called when the sheet closes. */
    fun stop() {
        job?.cancel()
        importWatch?.cancel()
        // Reading aloud carries on after the sheet closes (stop it from the media notification),
        // like reading an article aloud.
        startedReading = false
    }

    private companion object {
        const val GIST_LIMIT = 40
    }
}
