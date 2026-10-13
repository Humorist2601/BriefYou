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
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.GistItem
import me.ash.reader.infrastructure.ai.ModelState
import me.ash.reader.infrastructure.android.TextToSpeechManager

/** What the AI sheet is summarizing. */
sealed interface SummaryRequest {
    data class Article(val title: String?, val html: String) : SummaryRequest

    /** Everything in the list the user is looking at (a group, a feed, or all articles). */
    data class Gist(val filterState: FilterState) : SummaryRequest
}

sealed interface SummaryUiState {
    /** No model imported yet: show setup instructions. */
    data object NeedsModel : SummaryUiState

    data class Importing(val progress: Float?) : SummaryUiState

    /** Model is loading into memory, or the first tokens haven't arrived yet. */
    data class Thinking(val articleCount: Int? = null) : SummaryUiState

    data class Streaming(val text: String, val done: Boolean, val articleCount: Int? = null) :
        SummaryUiState

    data class Error(val message: String) : SummaryUiState
}

@HiltViewModel
class SummaryViewModel
@Inject
constructor(
    private val summarizer: GemmaSummarizer,
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    val textToSpeechManager: TextToSpeechManager,
) : ViewModel() {

    private val _state = MutableStateFlow<SummaryUiState>(SummaryUiState.Thinking())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var importWatch: Job? = null
    private var lastRequest: SummaryRequest? = null
    private var startedReading = false

    fun start(request: SummaryRequest) {
        lastRequest = request
        job?.cancel()
        if (!summarizer.hasModel()) {
            _state.value = SummaryUiState.NeedsModel
            return
        }
        when (request) {
            is SummaryRequest.Article -> {
                if (request.html.isBlank()) {
                    _state.value = SummaryUiState.Error("The article hasn't finished loading yet.")
                    return
                }
                run(summarizer.summarize(request.title, request.html), articleCount = null)
            }
            is SummaryRequest.Gist -> {
                _state.value = SummaryUiState.Thinking()
                job =
                    viewModelScope.launch {
                        val items = loadGistItems(request.filterState)
                        if (items.isEmpty()) {
                            _state.value = SummaryUiState.Error("There are no articles in this list.")
                            return@launch
                        }
                        val name =
                            request.filterState.group?.name ?: request.filterState.feed?.name
                        run(summarizer.gist(name, items), articleCount = items.size)
                    }
            }
        }
    }

    private fun run(flow: Flow<String>, articleCount: Int?) {
        _state.value = SummaryUiState.Thinking(articleCount)
        job =
            viewModelScope.launch {
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
                if (!failed) _state.value = SummaryUiState.Streaming(latest, done = true, articleCount)
            }
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
            rows.map { GistItem(it.feed.name, it.article.title, it.article.shortDescription) }
        }

    fun retry() {
        lastRequest?.let(::start)
    }

    fun importModel(uri: Uri) {
        job?.cancel()
        summarizer.importModel(uri)
        importWatch?.cancel()
        importWatch =
            viewModelScope.launch {
                summarizer.modelState.collect { model ->
                    when (model) {
                        is ModelState.Importing -> _state.value = SummaryUiState.Importing(model.progress)
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

    /** Toggles reading the summary aloud. */
    fun readAloud(text: String) {
        val tts = textToSpeechManager
        if (startedReading && tts.state !is TextToSpeechManager.State.Idle) {
            tts.stop()
            startedReading = false
        } else {
            // Drop the bullet glyphs so they aren't read out.
            tts.readPlainText(text.lines().joinToString("\n") { it.trimStart('•', '*', '-', ' ') })
            startedReading = true
        }
    }

    /** Called when the sheet closes. */
    fun stop() {
        job?.cancel()
        importWatch?.cancel()
        if (startedReading) textToSpeechManager.stop()
        startedReading = false
    }

    private companion object {
        const val GIST_LIMIT = 40
    }
}
