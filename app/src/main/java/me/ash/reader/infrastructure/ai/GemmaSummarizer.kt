package me.ash.reader.infrastructure.ai

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.ApplicationScope
import org.jsoup.Jsoup
import timber.log.Timber

/**
 * On-device article summaries with a Gemma model run through LiteRT-LM.
 *
 * Nothing leaves the phone: the user imports a `.litertlm` model file once (for example Gemma 4
 * E2B from the litert-community page on Hugging Face), it is copied into the app's private
 * storage, and every summary runs locally. The engine is loaded on first use (GPU, falling back
 * to CPU) and unloaded after a few idle minutes so the ~2-3 GB model doesn't stay in memory.
 */
@Singleton
class GemmaSummarizer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val modelFile = File(context.filesDir, "models/summary-model.litertlm")
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var unloadJob: Job? = null
    private val activeUses = AtomicInteger(0)

    fun hasModel(): Boolean = modelFile.isFile && modelFile.length() > 0

    fun modelSizeBytes(): Long = if (hasModel()) modelFile.length() else 0L

    /** Copies the picked model into private storage. Reports progress in 0..1 when size is known. */
    suspend fun importModel(uri: Uri, onProgress: (Float?) -> Unit) =
        withContext(Dispatchers.IO) {
            unload()
            val total =
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    ?.takeIf { it > 0 }
            modelFile.parentFile?.mkdirs()
            val tmp = File(modelFile.parentFile, "import.tmp")
            try {
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
                                onProgress(total?.let { copied.toFloat() / it })
                            }
                        }
                    }
                }
                if (!tmp.renameTo(modelFile)) error("Could not save the model")
            } finally {
                tmp.delete()
            }
        }

    suspend fun deleteModel() =
        withContext(Dispatchers.IO) {
            unload()
            modelFile.delete()
        }

    /**
     * Streams the summary text as it is generated. Each emission is the full text so far, which
     * makes the UI trivial (just show the latest value).
     */
    fun summarize(title: String?, html: String): Flow<String> =
        flow {
                val articleText = htmlToText(html).take(MAX_INPUT_CHARS)
                val prompt = buildString {
                    if (!title.isNullOrBlank()) append("Title: ").append(title).append("\n\n")
                    append(articleText)
                }
                val sb = StringBuilder()
                val engine = acquireEngine()
                try {
                    engine
                        .createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(SYSTEM_PROMPT),
                                samplerConfig =
                                    SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3),
                            )
                        )
                        .use { conversation ->
                            conversation.sendMessageAsync(prompt).collect { chunk ->
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
            check(hasModel()) { "No model imported" }
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

    private suspend fun unload() =
        engineLock.withLock {
            if (activeUses.get() > 0) return@withLock
            engine?.close()
            engine = null
        }

    private fun htmlToText(html: String): String =
        Jsoup.parse(html).apply { select("script, style, figure, img").remove() }.text()

    private companion object {
        // Roughly 2.5k tokens of article, leaving room for the instruction and the answer.
        const val MAX_INPUT_CHARS = 10_000
        const val IDLE_UNLOAD_MS = 3 * 60 * 1000L
        const val SYSTEM_PROMPT =
            "You summarize articles for a news reader app. Reply with 3 to 5 short bullet " +
                "points starting with \"• \" that capture the key facts, then one line starting " +
                "with \"Why it matters:\". Write in the same language as the article. Do not " +
                "add facts that are not in the article and do not add any preamble."
    }
}
