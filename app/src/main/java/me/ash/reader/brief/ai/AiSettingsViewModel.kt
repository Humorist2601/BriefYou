package me.ash.reader.brief.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import me.ash.reader.brief.ai.GemmaSummarizer
import me.ash.reader.brief.ai.ModelState

@HiltViewModel
class AiSettingsViewModel @Inject constructor(private val summarizer: GemmaSummarizer) :
    ViewModel() {
    val modelState: StateFlow<ModelState> = summarizer.modelState
    val articlePrompt: StateFlow<String> = summarizer.articlePrompt
    val gistPrompt: StateFlow<String> = summarizer.gistPrompt
    val cardPrompt: StateFlow<String> = summarizer.cardPrompt

    fun importModel(uri: Uri) = summarizer.importModel(uri)

    fun removeModel() = summarizer.deleteModel()

    fun startDownload() = summarizer.startDownload()

    fun cancelDownload() = summarizer.cancelDownload()

    init {
        // A download started before the app was closed carries on in the background: show it.
        summarizer.watchDownload()
    }

    fun setArticlePrompt(value: String?) = summarizer.setArticlePrompt(value)

    fun setGistPrompt(value: String?) = summarizer.setGistPrompt(value)

    fun setCardPrompt(value: String?) = summarizer.setCardPrompt(value)
}
