package me.ash.reader.ui.page.home.reading.summary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.GistItem
import me.ash.reader.infrastructure.ai.ModelState
import me.ash.reader.infrastructure.ai.SavedKind
import me.ash.reader.infrastructure.ai.SummaryStore
import me.ash.reader.infrastructure.android.TextToSpeechManager

/** What the AI sheet is working on. */
sealed interface SummaryRequest {
    data class Article(val articleId: String, val title: String?, val html: String) :
        SummaryRequest

    /** Everything in the list the user is looking at (a group, a feed, or all articles). */
    data class Gist(val filterState: FilterState) : SummaryRequest
}

/** Article sheets can show a summary or a translation. */
enum class SheetMode {
    Summary,
    Translate,
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
        val fromCache: Boolean = false,
    ) : SummaryUiState

    data class Error(val message: String) : SummaryUiState
}

/** One follow-up question and its (possibly still streaming) answer. */
data class QA(val question: String, val answer: String, val done: Boolean, val error: Boolean = false)

@HiltViewModel
class SummaryViewModel
@Inject
constructor(
    private val summarizer: GemmaSummarizer,
    private val store: SummaryStore,
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val rssService: RssService,
    val textToSpeechManager: TextToSpeechManager,
) : ViewModel() {

    private val _state = MutableStateFlow<SummaryUiState>(SummaryUiState.Thinking())
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(SheetMode.Summary)
    val mode: StateFlow<SheetMode> = _mode.asStateFlow()

    private val _chat = MutableStateFlow<List<QA>>(emptyList())
    val chat: StateFlow<List<QA>> = _chat.asStateFlow()

    /** Number of gist articles marked read from the sheet (null = not yet). */
    private val _gistMarkedRead = MutableStateFlow<Int?>(null)
    val gistMarkedRead: StateFlow<Int?> = _gistMarkedRead.asStateFlow()

    val lowRam: Boolean
        get() = summarizer.isLowRamForDefaultModel()

    private var job: Job? = null
    private var chatJob: Job? = null
    private var importWatch: Job? = null
    private var request: SummaryRequest? = null
    private var gistItems: List<GistItem> = emptyList()
    private var chatSession: GemmaSummarizer.Chat? = null
    private var startedReading = false

    fun start(newRequest: SummaryRequest) {
        if (newRequest != request) {
            _mode.value = SheetMode.Summary
        }
        request = newRequest
        load(useCache = true)
    }

    fun setMode(mode: SheetMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        load(useCache = true)
    }

    /** Regenerate, ignoring anything saved. */
    fun regenerate() = load(useCache = false)

    fun retry() = load(useCache = true)

    private fun load(useCache: Boolean) {
        val req = request ?: return
        job?.cancel()
        resetChat()
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
                val kind =
                    if (_mode.value == SheetMode.Translate) SavedKind.Translation
                    else SavedKind.Summary
                _state.value = SummaryUiState.Thinking()
                job =
                    viewModelScope.launch {
                        if (useCache) {
                            store.get(req.articleId, kind)?.let {
                                _state.value = SummaryUiState.Streaming(it, done = true, fromCache = true)
                                return@launch
                            }
                        }
                        val flow =
                            if (kind == SavedKind.Translation)
                                summarizer.translate(req.title, req.html, deviceLanguage())
                            else summarizer.summarize(req.title, req.html)
                        val result = collect(flow, articleCount = null)
                        if (result != null) store.put(req.articleId, kind, result)
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
                        collect(summarizer.gist(gistName(req), items), articleCount = items.size)
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

    private fun gistName(req: SummaryRequest.Gist) =
        req.filterState.group?.name ?: req.filterState.feed?.name

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

    // --- Follow-up questions ---

    fun ask(question: String) {
        val q = question.trim()
        val req = request ?: return
        if (q.isEmpty() || chatJob?.isActive == true) return
        val shown = (_state.value as? SummaryUiState.Streaming)?.text
        _chat.update { it + QA(q, "", done = false) }
        chatJob =
            viewModelScope.launch {
                try {
                    val session =
                        chatSession
                            ?: summarizer
                                .openChat(
                                    source =
                                        when (req) {
                                            is SummaryRequest.Article ->
                                                summarizer.articleContext(req.title, req.html)
                                            is SummaryRequest.Gist ->
                                                summarizer.gistContext(gistName(req), gistItems)
                                        },
                                    priorAnswer = shown,
                                )
                                .also { chatSession = it }
                    session.ask(q).collect { answer -> updateLastAnswer(answer, done = false) }
                    updateLastAnswer(null, done = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateLastAnswer(e.message ?: e.javaClass.simpleName, done = true, error = true)
                }
            }
    }

    private fun updateLastAnswer(answer: String?, done: Boolean, error: Boolean = false) {
        _chat.update { list ->
            if (list.isEmpty()) list
            else {
                val last = list.last()
                list.dropLast(1) + last.copy(answer = answer ?: last.answer, done = done, error = error)
            }
        }
    }

    private fun resetChat() {
        chatJob?.cancel()
        chatSession?.close()
        chatSession = null
        _chat.value = emptyList()
    }

    // --- Model import from the sheet ---

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

    // --- Read aloud ---

    /** Toggles reading the result aloud. */
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
        resetChat()
        if (startedReading) textToSpeechManager.stop()
        startedReading = false
    }

    override fun onCleared() {
        resetChat()
    }

    private fun deviceLanguage(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)

    private companion object {
        const val GIST_LIMIT = 40
    }
}
