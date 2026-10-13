package me.ash.reader.infrastructure.ai

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.ApplicationScope
import org.jsoup.Jsoup
import timber.log.Timber

/** What the app knows about the on-device model file. */
sealed interface ModelState {
    data object Missing : ModelState

    data class Importing(val progress: Float?) : ModelState

    data class Ready(val sizeBytes: Long) : ModelState

    data class ImportFailed(val message: String) : ModelState
}

/** One article fed into a group gist. */
data class GistItem(val feedName: String, val title: String, val description: String)

/**
 * On-device article summaries and group gists with a Gemma model run through LiteRT-LM.
 *
 * Nothing leaves the phone: the user imports a `.litertlm` model file once (for example Gemma 4
 * E2B from the litert-community page on Hugging Face), it is copied into the app's private
 * storage, and everything runs locally. The engine is loaded on first use (GPU, falling back to
 * CPU) and unloaded after a few idle minutes so the multi-gigabyte model doesn't stay in memory.
 * The instructions sent to the model are editable in Settings → AI model.
 */
@Singleton
class GemmaSummarizer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val modelFile = File(context.filesDir, "models/summary-model.litertlm")
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var unloadJob: Job? = null
    private var importJob: Job? = null
    private val activeUses = AtomicInteger(0)

    private val _modelState = MutableStateFlow(currentFileState())
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _articlePrompt =
        MutableStateFlow(prefs.getString(KEY_ARTICLE_PROMPT, null) ?: DEFAULT_ARTICLE_PROMPT)
    val articlePrompt: StateFlow<String> = _articlePrompt.asStateFlow()

    private val _gistPrompt =
        MutableStateFlow(prefs.getString(KEY_GIST_PROMPT, null) ?: DEFAULT_GIST_PROMPT)
    val gistPrompt: StateFlow<String> = _gistPrompt.asStateFlow()

    fun hasModel(): Boolean = modelFile.isFile && modelFile.length() > 0

    fun setArticlePrompt(value: String?) {
        val v = value?.takeIf { it.isNotBlank() }
        prefs.edit { if (v == null) remove(KEY_ARTICLE_PROMPT) else putString(KEY_ARTICLE_PROMPT, v) }
        _articlePrompt.value = v ?: DEFAULT_ARTICLE_PROMPT
    }

    fun setGistPrompt(value: String?) {
        val v = value?.takeIf { it.isNotBlank() }
        prefs.edit { if (v == null) remove(KEY_GIST_PROMPT) else putString(KEY_GIST_PROMPT, v) }
        _gistPrompt.value = v ?: DEFAULT_GIST_PROMPT
    }

    /**
     * Copies the picked model into private storage. Runs in the app scope, so leaving the screen
     * doesn't cancel it; progress is published on [modelState].
     */
    fun importModel(uri: Uri) {
        if (importJob?.isActive == true) return
        _modelState.value = ModelState.Importing(null)
        importJob =
            appScope.launch(Dispatchers.IO) {
                val tmp = File(modelFile.parentFile, "import.tmp")
                try {
                    unload(force = true)
                    val total =
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                            ?.takeIf { it > 0 }
                    modelFile.parentFile?.mkdirs()
                    val input =
                        context.contentResolver.openInputStream(uri)
                            ?: error("Could not open the selected file")
                    input.use { src ->
                        tmp.outputStream().use { dst ->
                            val buf = ByteArray(1 shl 20)
                            var copied = 0L
                            var lastReported = 0L
                            while (true) {
                                val n = src.read(buf)
                                if (n < 0) break
                                dst.write(buf, 0, n)
                                copied += n
                                if (copied - lastReported > (16 shl 20)) {
                                    lastReported = copied
                                    _modelState.value =
                                        ModelState.Importing(total?.let { copied.toFloat() / it })
                                }
                            }
                        }
                    }
                    modelFile.delete()
                    if (!tmp.renameTo(modelFile)) error("Could not save the model")
                    _modelState.value = currentFileState()
                } catch (e: CancellationException) {
                    _modelState.value = currentFileState()
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "model import failed")
                    _modelState.value =
                        ModelState.ImportFailed(e.message ?: e.javaClass.simpleName)
                } finally {
                    tmp.delete()
                }
            }
    }

    fun deleteModel() {
        appScope.launch(Dispatchers.IO) {
            importJob?.cancel()
            unload(force = true)
            modelFile.delete()
            _modelState.value = ModelState.Missing
        }
    }

    /** Streams a summary of one article. Each emission is the full text generated so far. */
    fun summarize(title: String?, html: String): Flow<String> {
        val prompt = buildString {
            if (!title.isNullOrBlank()) append("Title: ").append(title).append("\n\n")
            append(htmlToText(html).take(MAX_INPUT_CHARS))
        }
        return generate(articlePrompt.value, prompt)
    }

    /** Streams a gist of many articles (for example everything in a group). */
    fun gist(groupName: String?, items: List<GistItem>): Flow<String> {
        val prompt = buildString {
            if (!groupName.isNullOrBlank()) append("Group: ").append(groupName).append("\n\n")
            for ((i, item) in items.withIndex()) {
                val line = buildString {
                    append(i + 1).append(". [").append(item.feedName).append("] ").append(item.title)
                    val desc = item.description.trim().take(MAX_GIST_DESC_CHARS)
                    if (desc.isNotEmpty()) append(" — ").append(desc)
                    append('\n')
                }
                if (length + line.length > MAX_INPUT_CHARS) break
                append(line)
            }
        }
        return generate(gistPrompt.value, prompt)
    }

    private fun generate(systemPrompt: String, userText: String): Flow<String> =
        flow {
                val sb = StringBuilder()
                val engine = acquireEngine()
                try {
                    engine
                        .createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(systemPrompt),
                                samplerConfig =
                                    SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                            )
                        )
                        .use { conversation ->
                            conversation.sendMessageAsync(userText).collect { chunk ->
                                sb.append(chunk.toString())
                                emit(sb.toString().trim())
                            }
                        }
                } finally {
                    if (activeUses.decrementAndGet() == 0) scheduleUnload()
                }
            }
            .flowOn(Dispatchers.Default)

    private suspend fun acquireEngine(): Engine {
        engineLock.withLock {
            unloadJob?.cancel()
            engine?.let {
                activeUses.incrementAndGet()
                return it
            }
            check(hasModel()) { "No model imported. Add one in Settings → AI model." }
            val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
            val created =
                try {
                    Engine(
                            EngineConfig(
                                modelPath = modelFile.absolutePath,
                                backend = Backend.GPU(),
                                cacheDir = cacheDir,
                            )
                        )
                        .also { it.initialize() }
                } catch (t: Throwable) {
                    Timber.w(t, "GPU backend failed, falling back to CPU")
                    Engine(
                            EngineConfig(
                                modelPath = modelFile.absolutePath,
                                backend = Backend.CPU(),
                                cacheDir = cacheDir,
                            )
                        )
                        .also { it.initialize() }
                }
            engine = created
            activeUses.incrementAndGet()
            return created
        }
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob =
            appScope.launch {
                delay(IDLE_UNLOAD_MS)
                unload()
            }
    }

    private suspend fun unload(force: Boolean = false) =
        engineLock.withLock {
            if (!force && activeUses.get() > 0) return@withLock
            engine?.close()
            engine = null
        }

    private fun currentFileState(): ModelState =
        if (hasModel()) ModelState.Ready(modelFile.length()) else ModelState.Missing

    private fun htmlToText(html: String): String =
        Jsoup.parse(html).apply { select("script, style, figure, img").remove() }.text()

    companion object {
        const val MODEL_PAGE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"

        private const val KEY_ARTICLE_PROMPT = "article_prompt"
        private const val KEY_GIST_PROMPT = "gist_prompt"
        // Roughly 2.5k tokens of input, leaving room for the instruction and the answer.
        private const val MAX_INPUT_CHARS = 10_000
        private const val MAX_GIST_DESC_CHARS = 220
        private const val IDLE_UNLOAD_MS = 3 * 60 * 1000L

        const val DEFAULT_ARTICLE_PROMPT =
            "You summarize articles for a news reader app. Reply with 3 to 5 short bullet " +
                "points starting with \"• \" that capture the key facts, then one line starting " +
                "with \"Why it matters:\". Write in the same language as the article. Do not " +
                "add facts that are not in the article and do not add any preamble."

        const val DEFAULT_GIST_PROMPT =
            "You write a briefing for a news reader app. You get a numbered list of recent " +
                "articles, each with its source in brackets, a title and a short description. " +
                "Group related stories together and reply with 4 to 8 bullet points starting " +
                "with \"• \", most important first. Each bullet is one or two sentences and may " +
                "combine several articles about the same story. Write in the language most " +
                "articles use. Only use facts from the list and do not add any preamble."
    }
}
