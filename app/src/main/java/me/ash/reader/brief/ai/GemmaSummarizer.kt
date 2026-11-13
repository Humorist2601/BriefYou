package me.ash.reader.brief.ai

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * Copying a picked file in, or [downloading] it from Hugging Face ([waiting]: the connection
     * dropped and it will carry on by itself).
     */
    data class Importing(val progress: Float?, val downloading: Boolean = false, val waiting: Boolean = false) :
        ModelState

    data class Ready(val sizeBytes: Long) : ModelState

    data class ImportFailed(val message: String) : ModelState
}

/** One article fed into a group gist. */
data class GistItem(
    val articleId: String,
    val feedName: String,
    val title: String,
    val description: String,
)

/**
 * On-device article summaries and group gists with a Gemma model run through LiteRT-LM.
 *
 * Nothing leaves the phone: the user imports a `.litertlm` model file once (for example Gemma 4
 * E2B from the litert-community page on Hugging Face), it is copied into the app's private
 * storage, and everything runs locally. The engine is loaded on first use, on the GPU (falling
 * back to the CPU, which is then remembered so later loads don't try the GPU first), and unloaded
 * after a few idle minutes so the multi-gigabyte model doesn't stay in memory.
 * The instructions sent to the model are editable in Settings → AI model.
 */
@Singleton
class GemmaSummarizer
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val modelFile = File(context.filesDir, "models/summary-model.litertlm")
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val engineLock = Mutex()
    private val generationLock = Mutex()
    @Volatile private var engine: Engine? = null
    private var unloadJob: Job? = null
    private var importJob: Job? = null
    private val activeUses = AtomicInteger(0)
    /** An answer the model didn't stop in time; completes when it finally does. */
    @Volatile private var stuck: CompletableDeferred<Unit>? = null

    private val _modelState = MutableStateFlow(currentFileState())
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _articlePrompt =
        MutableStateFlow(prefs.getString(KEY_ARTICLE_PROMPT, null) ?: DEFAULT_ARTICLE_PROMPT)
    val articlePrompt: StateFlow<String> = _articlePrompt.asStateFlow()

    private val _gistPrompt =
        MutableStateFlow(prefs.getString(KEY_GIST_PROMPT, null) ?: DEFAULT_GIST_PROMPT)
    val gistPrompt: StateFlow<String> = _gistPrompt.asStateFlow()

    private val _cardPrompt =
        MutableStateFlow(prefs.getString(KEY_CARD_PROMPT, null) ?: DEFAULT_CARD_PROMPT)
    val cardPrompt: StateFlow<String> = _cardPrompt.asStateFlow()

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

    fun setCardPrompt(value: String?) {
        val v = value?.takeIf { it.isNotBlank() }
        prefs.edit { if (v == null) remove(KEY_CARD_PROMPT) else putString(KEY_CARD_PROMPT, v) }
        _cardPrompt.value = v ?: DEFAULT_CARD_PROMPT
    }

    fun customCardPrompt(): String? = prefs.getString(KEY_CARD_PROMPT, null)

    /** The instructions the user wrote, for a backup; null where the default is used. */
    fun customArticlePrompt(): String? = prefs.getString(KEY_ARTICLE_PROMPT, null)

    fun customGistPrompt(): String? = prefs.getString(KEY_GIST_PROMPT, null)

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

    /** Downloads the model in the background (see [ModelDownload]); progress is on [modelState]. */
    fun startDownload() {
        if (importJob?.isActive == true) return
        _modelState.value = ModelState.Importing(null, downloading = true)
        ModelDownload.start(context)
    }

    fun cancelDownload() {
        ModelDownload.cancel(context)
        _modelState.value = currentFileState()
    }

    private var downloadWatch: Job? = null

    /** Shows a download that's still running from before (the app was closed meanwhile). */
    fun watchDownload() {
        if (downloadWatch?.isActive == true) return
        downloadWatch =
            appScope.launch {
                ModelDownload.running(context).collect { running ->
                    val now = _modelState.value
                    if (running && !(now is ModelState.Importing && now.downloading)) {
                        _modelState.value = ModelState.Importing(null, downloading = true)
                    }
                }
            }
    }

    internal fun onDownload(progress: Float?, waiting: Boolean = false) {
        _modelState.value = ModelState.Importing(progress, downloading = true, waiting = waiting)
    }

    internal fun onDownloadFailed(message: String) {
        _modelState.value = ModelState.ImportFailed(message)
    }

    /** Moves the downloaded file into place (the same folder, so no second copy). */
    internal suspend fun installDownloaded(part: File) {
        unload(force = true)
        modelFile.delete()
        if (!part.renameTo(modelFile)) error("Could not save the model")
        _modelState.value = currentFileState()
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

    /**
     * Streams a very short summary for a Triage card: 3 bullet points of one short sentence each,
     * so it fits on the card. Each emission is the full text generated so far.
     */
    fun summarizeForCard(title: String?, html: String): Flow<String> {
        val prompt = buildString {
            if (!title.isNullOrBlank()) append("Title: ").append(title).append("\n\n")
            append(htmlToText(html).take(MAX_INPUT_CHARS))
        }
        return generate(cardPrompt.value, prompt)
    }

    /**
     * Streams an answer to a question about one article, from the article only (with the last
     * few questions and answers, so follow-up questions make sense).
     */
    fun ask(
        title: String?,
        html: String,
        history: List<Pair<String, String>>,
        question: String,
    ): Flow<String> {
        val prompt = buildString {
            if (!title.isNullOrBlank()) append("Title: ").append(title).append("\n\n")
            append("Article:\n").append(htmlToText(html).take(MAX_INPUT_CHARS - 2_000)).append("\n\n")
            for ((q, a) in history.takeLast(3)) {
                append("Question: ").append(q.take(300)).append('\n')
                append("Answer: ").append(a.take(600)).append("\n\n")
            }
            append("Question: ").append(question.take(500))
        }
        return generate(ASK_PROMPT, prompt)
    }

    /** Streams an answer to a question about a list's articles (the gist sheet). */
    fun askGist(
        groupName: String?,
        items: List<GistItem>,
        history: List<Pair<String, String>>,
        question: String,
    ): Flow<String> {
        val prompt = buildString {
            append("Articles:\n").append(buildGistPrompt(groupName, items).take(MAX_INPUT_CHARS - 2_000))
            append("\n\n")
            for ((q, a) in history.takeLast(3)) {
                append("Question: ").append(q.take(300)).append('\n')
                append("Answer: ").append(a.take(600)).append("\n\n")
            }
            append("Question: ").append(question.take(500))
        }
        return generate(ASK_GIST_PROMPT, prompt)
    }

    /**
     * Streams a translation of numbered paragraphs ("[1] text", one per line) into [language]
     * (its English name), with the same numbers. Each emission is the full text so far.
     */
    fun translate(language: String, numbered: String): Flow<String> =
        generate(
            "You translate text for a news reader app. The user sends paragraphs, each starting with " +
                "a number in square brackets. Translate every paragraph into $language and reply with " +
                "the same numbers in square brackets, one paragraph after each number, in the same " +
                "order. Keep names, numbers and quotes accurate. If a paragraph is already in " +
                "$language, repeat it as it is. Some paragraphs contain numbered tags such as <1>words</1>. " +
                "Keep every tag pair exactly as written, around the words that translate the tagged " +
                "words, and never explain the tags. Do not add notes, explanations or anything else.",
            numbered,
        )

    /** Streams a gist of many articles (for example everything in a group). */
    fun gist(groupName: String?, items: List<GistItem>): Flow<String> {
        return generate(gistPrompt.value, buildGistPrompt(groupName, items))
    }

    private fun buildGistPrompt(groupName: String?, items: List<GistItem>): String = buildString {
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

    /**
     * Runs one request. Requests run one at a time (the engine has one conversation at a time).
     *
     * Stopping it (cancelling the collector) is done carefully: the model keeps working on its own
     * thread, so the conversation is first told to stop, and only deleted once the model says it
     * has stopped. Deleting it while the model still works on it crashes the app.
     */
    private fun generate(systemPrompt: String, userText: String): Flow<String> =
        flow {
                generationLock.withLock {
                    // An earlier answer the model didn't stop in time: wait (a little) for it to
                    // finish before starting another, instead of starting alongside it, which
                    // could hang. Still busy: say so, rather than loading forever.
                    stuck?.let { busy ->
                        withTimeoutOrNull(STOP_TIMEOUT_MS) { busy.await() }
                            ?: throw IllegalStateException(
                                "The model is still finishing the last answer. Try again in a moment."
                            )
                    }
                    val sb = StringBuilder()
                    val engine = acquireEngine()
                    var stillRunning = false
                    try {
                        // Stopped while the model was loading: don't start.
                        currentCoroutineContext().ensureActive()
                        val conversation =
                            engine.createConversation(
                                ConversationConfig(
                                    systemInstruction = Contents.of(systemPrompt),
                                    samplerConfig =
                                        SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                                )
                            )
                        val chunks = Channel<String>(Channel.UNLIMITED)
                        val finished = CompletableDeferred<Unit>()
                        try {
                            conversation.sendMessageAsync(
                                Contents.of(userText),
                                object : MessageCallback {
                                    override fun onMessage(message: Message) {
                                        chunks.trySend(message.toString())
                                    }

                                    override fun onDone() {
                                        chunks.close()
                                        finished.complete(Unit)
                                    }

                                    override fun onError(throwable: Throwable) {
                                        chunks.close(throwable)
                                        finished.complete(Unit)
                                    }
                                },
                            )
                            // Never waits forever: if the model goes quiet (nothing at all, or
                            // nothing more for a while), the request ends with an error and a
                            // chance to try again, instead of a spinner that never stops.
                            var first = true
                            while (true) {
                                val next =
                                    withTimeoutOrNull(if (first) FIRST_CHUNK_TIMEOUT_MS else NEXT_CHUNK_TIMEOUT_MS) {
                                        chunks.receiveCatching()
                                    } ?: throw IllegalStateException("The model stopped answering. Try again.")
                                if (next.isClosed) {
                                    next.exceptionOrNull()?.let { throw it }
                                    break
                                }
                                first = false
                                sb.append(next.getOrThrow())
                                emit(sb.toString().trim())
                            }
                        } finally {
                            if (!finished.isCompleted) {
                                runCatching { conversation.cancelProcess() }
                                    .onFailure { Timber.w(it, "could not stop the model") }
                                withContext(NonCancellable) {
                                    withTimeoutOrNull(STOP_TIMEOUT_MS) { finished.await() }
                                }
                            }
                            if (finished.isCompleted) {
                                runCatching { conversation.close() }
                            } else {
                                // The model never said it stopped. Leaving the conversation (and
                                // the engine) alone is better than crashing; it's closed, and the
                                // engine released, once the model does say so. Until then the next
                                // request waits for it (see above).
                                Timber.w("model did not stop in time; keeping the engine loaded")
                                stillRunning = true
                                stuck = finished
                                appScope.launch {
                                    finished.await()
                                    runCatching { conversation.close() }
                                    if (stuck === finished) stuck = null
                                    if (activeUses.decrementAndGet() == 0) scheduleUnload()
                                }
                            }
                        }
                    } finally {
                        if (!stillRunning && activeUses.decrementAndGet() == 0) scheduleUnload()
                    }
                }
            }
            .flowOn(Dispatchers.Default)

    /**
     * Brief You is on screen: the model loads in the background a moment later (after the app's
     * start-up animations), so every AI feature answers without waiting for it — Google's own
     * advice for LiteRT-LM, whose loading can take up to 10 seconds. Only when the phone can
     * afford it: not in battery saver, with battery to spare (or charging) and enough free
     * memory; otherwise it loads on first use. It stays loaded while the app is open.
     */
    fun onAppForeground() {
        inForeground = true
        unloadJob?.cancel()
        warmJob?.cancel()
        warmJob =
            appScope.launch(Dispatchers.Default) {
                delay(WARM_UP_DELAY_MS)
                if (engine != null || !hasModel() || !canAfford()) return@launch
                try {
                    acquireEngine()
                    // Left the app while it loaded: unload later as usual.
                    if (activeUses.decrementAndGet() == 0 && !inForeground) scheduleUnload()
                } catch (t: Throwable) {
                    Timber.w(t, "AI warm-up failed")
                }
            }
    }

    /** Brief You left the screen: the model unloads a few minutes later unless it's in use. */
    fun onAppBackground() {
        inForeground = false
        warmJob?.cancel()
        if (activeUses.get() == 0) scheduleUnload()
    }

    @Volatile private var inForeground = false
    private var warmJob: Job? = null

    private fun canAfford(): Boolean =
        runCatching {
                val power = context.getSystemService(android.os.PowerManager::class.java)
                if (power?.isPowerSaveMode == true) return false
                val battery = context.getSystemService(android.os.BatteryManager::class.java)
                val level = battery?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                if (level in 0 until MIN_BATTERY && battery?.isCharging != true) return false
                val activity = context.getSystemService(android.app.ActivityManager::class.java)
                val memory = android.app.ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
                !memory.lowMemory && memory.availMem > modelFile.length() + MEMORY_MARGIN_BYTES
            }
            .getOrDefault(false)

    private suspend fun acquireEngine(): Engine {
        engineLock.withLock {
            unloadJob?.cancel()
            engine?.let {
                activeUses.incrementAndGet()
                return it
            }
            check(hasModel()) { "No model imported. Add one in Settings → AI model." }
            val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
            fun load(backend: Backend) =
                Engine(
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = backend,
                            cacheDir = cacheDir,
                        )
                    )
                    .also { it.initialize() }
            // When the GPU failed before, go straight to the CPU instead of trying the GPU again
            // first, which takes a while to fail (Pixels' Tensor chips have no OpenCL, which
            // LiteRT's GPU backend needs). The GPU is tried again after a month, in case an
            // update made it work.
            val failedAt = prefs.getLong(KEY_GPU_FAILED_AT, 0L)
            val gpuFailed = failedAt > 0 && System.currentTimeMillis() - failedAt < GPU_RETRY_AFTER_MS
            val created =
                if (gpuFailed) {
                    load(Backend.CPU())
                } else {
                    try {
                        load(Backend.GPU())
                    } catch (t: Throwable) {
                        Timber.w(t, "GPU backend failed, falling back to CPU")
                        prefs.edit { putLong(KEY_GPU_FAILED_AT, System.currentTimeMillis()) }
                        load(Backend.CPU())
                    }
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

    private suspend fun unload(force: Boolean = false) {
        // Forced (a new model, or deleting it): wait for an answer being written to finish first,
        // so the engine isn't closed under it.
        if (force) generationLock.withLock { closeEngine(force = true) } else closeEngine(force = false)
    }

    private suspend fun closeEngine(force: Boolean) =
        engineLock.withLock {
            // Kept while the app is open; unloaded a few minutes after you leave it.
            if (!force && (activeUses.get() > 0 || inForeground)) return@withLock
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
        private const val KEY_CARD_PROMPT = "card_prompt"
        /** After the app opens, before the model starts loading (after start-up animations). */
        private const val WARM_UP_DELAY_MS = 2_500L
        private const val GPU_RETRY_AFTER_MS = 30L * 24 * 60 * 60 * 1000
        private const val MIN_BATTERY = 20
        /** Free memory needed on top of the model's size before loading it ahead. */
        private const val MEMORY_MARGIN_BYTES = 1_500_000_000L
        private const val KEY_GIST_PROMPT = "gist_prompt"
        /** The app version in which the GPU failed to load the model. */
        private const val KEY_GPU_FAILED_AT = "gpu_failed_at"
        // Roughly 2.5k tokens of input, leaving room for the instruction and the answer.
        private const val MAX_INPUT_CHARS = 10_000
        private const val MAX_GIST_DESC_CHARS = 220
        private const val IDLE_UNLOAD_MS = 3 * 60 * 1000L
        private const val STOP_TIMEOUT_MS = 15_000L
        /** How long the model may think before its first word (a long article on a slow phone). */
        private const val FIRST_CHUNK_TIMEOUT_MS = 180_000L
        /** How long it may go quiet once it has started writing. */
        private const val NEXT_CHUNK_TIMEOUT_MS = 60_000L

        private const val ASK_PROMPT =
            "You answer a reader's questions about a news article. Use the article first. You " +
                "may also use general knowledge to explain terms, give background or answer " +
                "related questions; when part of your answer is not from the article, say so " +
                "briefly. Answer in 1 to 4 short sentences, in the same language as the question."

        private const val ASK_GIST_PROMPT =
            "You answer a reader's questions about a list of news articles (titles and short " +
                "descriptions). Use the list first, and mention which articles you mean. You may " +
                "also use general knowledge for background; when part of your answer is not from " +
                "the list, say so briefly. Answer in 1 to 4 short sentences, in the same language " +
                "as the question."

        const val DEFAULT_CARD_PROMPT =
            "You summarize news articles for a small card in a news reader app. Reply with " +
                "exactly 3 bullet points starting with \"• \", each one short sentence of at " +
                "most 18 words with the key facts (what happened, who, the most important " +
                "detail). Write in the same language as the article. Do not add facts that are " +
                "not in the article and do not add any preamble or closing line."

        const val DEFAULT_ARTICLE_PROMPT =
            "You summarize articles for a news reader app. Reply with exactly 3 short " +
                "bullet points starting with \"• \" that capture the key facts, then one line starting " +
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
