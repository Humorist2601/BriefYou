package me.ash.reader.ui.page.home.reading.summary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.ai.GemmaSummarizer

sealed interface SummaryUiState {
    /** No model imported yet: show setup instructions. */
    data object NeedsModel : SummaryUiState

    data class Importing(val progress: Float?) : SummaryUiState

    /** Model is loading into memory, or the first tokens haven't arrived yet. */
    data object Thinking : SummaryUiState

    data class Streaming(val text: String, val done: Boolean) : SummaryUiState

    data class Error(val message: String) : SummaryUiState
}

@HiltViewModel
class SummaryViewModel @Inject constructor(private val summarizer: GemmaSummarizer) : ViewModel() {

    private val _state = MutableStateFlow<SummaryUiState>(SummaryUiState.Thinking)
    val state: StateFlow<SummaryUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastRequest: Pair<String?, String>? = null

    fun start(title: String?, html: String) {
        lastRequest = title to html
        job?.cancel()
        if (html.isBlank()) {
            _state.value = SummaryUiState.Error("The article hasn't finished loading yet.")
            return
        }
        if (!summarizer.hasModel()) {
            _state.value = SummaryUiState.NeedsModel
            return
        }
        _state.value = SummaryUiState.Thinking
        job =
            viewModelScope.launch {
                var latest = ""
                summarizer
                    .summarize(title, html)
                    .catch { e ->
                        _state.value =
                            SummaryUiState.Error(e.message ?: e.javaClass.simpleName)
                    }
                    .collect {
                        latest = it
                        _state.value = SummaryUiState.Streaming(it, done = false)
                    }
                if (_state.value is SummaryUiState.Streaming) {
                    _state.value = SummaryUiState.Streaming(latest, done = true)
                }
            }
    }

    fun retry() {
        lastRequest?.let { (title, html) -> start(title, html) }
    }

    fun importModel(uri: Uri) {
        job?.cancel()
        _state.value = SummaryUiState.Importing(null)
        job =
            viewModelScope.launch {
                try {
                    summarizer.importModel(uri) { _state.value = SummaryUiState.Importing(it) }
                    retry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value =
                        SummaryUiState.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
    }

    fun removeModel() {
        job?.cancel()
        viewModelScope.launch {
            summarizer.deleteModel()
            _state.value = SummaryUiState.NeedsModel
        }
    }

    fun stop() {
        job?.cancel()
    }
}
