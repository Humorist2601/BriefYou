package me.ash.reader.ui.page.settings.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.ai.BriefingSettings
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.ModelState
import me.ash.reader.infrastructure.ai.MorningBriefing
import me.ash.reader.infrastructure.android.TextToSpeechManager

@HiltViewModel
class AiSettingsViewModel
@Inject
constructor(
    private val summarizer: GemmaSummarizer,
    private val tts: TextToSpeechManager,
    val briefing: MorningBriefing,
    private val groupDao: GroupDao,
    private val accountService: AccountService,
) : ViewModel() {
    val modelState: StateFlow<ModelState> = summarizer.modelState
    val articlePrompt: StateFlow<String> = summarizer.articlePrompt
    val gistPrompt: StateFlow<String> = summarizer.gistPrompt
    val ramBytes: Long = summarizer.deviceRamBytes()
    val lowRam: Boolean = summarizer.isLowRamForDefaultModel()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _engine = MutableStateFlow(tts.currentEngine())
    val engine: StateFlow<String?> = _engine.asStateFlow()

    private val _rate = MutableStateFlow(tts.speechRate)
    val rate: StateFlow<Float> = _rate.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _groups.value = groupDao.queryAll(accountService.getCurrentAccountId())
        }
    }

    fun importModel(uri: Uri) = summarizer.importModel(uri)

    fun removeModel() = summarizer.deleteModel()

    fun setArticlePrompt(value: String?) = summarizer.setArticlePrompt(value)

    fun setGistPrompt(value: String?) = summarizer.setGistPrompt(value)

    // Voice
    fun engines(): List<Pair<String, String>> = tts.installedEngines()

    fun setEngine(pkg: String?) {
        tts.setEngine(pkg)
        _engine.value = tts.currentEngine()
    }

    fun setRate(rate: Float) {
        tts.setRate(rate)
        _rate.value = rate
    }

    fun testVoice(sentence: String) = tts.readPlainText(sentence)

    // Morning briefing
    fun updateBriefing(settings: BriefingSettings) = briefing.update(settings)
}
