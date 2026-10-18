package me.ash.reader.infrastructure.android

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.di.ApplicationScope
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @ApplicationScope
    private val coroutineScope: CoroutineScope
) {


    private val _stateFlow = MutableStateFlow<State>(State.Idle)
    val stateFlow = _stateFlow.asStateFlow()

    var state
        get() = stateFlow.value
        private set(value) {
            _stateFlow.value = value
        }

    private val tts: TextToSpeech = initTts()

    /** The reading that is running; callbacks from an older one are ignored. */
    @Volatile private var currentRun: Any? = null

    /** The title of what is being read (shown in the read aloud notification). */
    @Volatile var title: String? = null
        private set

    /** What is being read, in the pieces sent to the engine. */
    private var pieces: List<String> = emptyList()

    private fun initTts(): TextToSpeech {
        return TextToSpeech(context, TextToSpeech.OnInitListener {
            when (it) {
                TextToSpeech.SUCCESS -> {}
                else -> {
                    state = State.Error
                    Timber.e("TextToSpeech initialization failed $it")
                }
            }
        })
    }

    sealed interface State {
        object Idle : State
        object Preparing : State
        /** Reading part [current] of [total]. */
        class Reading(val current: Int, val total: Int) : State {
            val progress: Float
                get() = current.toFloat() / total
        }

        object Error : State
    }

    /** Reads an article aloud, from the beginning. */
    fun readHtml(htmlContent: String, title: String? = null) {
        coroutineScope.launch {
            val plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
            readText(plainText, title)
        }
    }

    /** Reads plain text aloud; each line becomes its own utterance. */
    fun readPlainText(text: String, title: String? = null) {
        coroutineScope.launch { readText(text, title) }
    }

    private fun readText(text: String, title: String?) {
        stop()
        this.title = title

        if (state != State.Idle) return

        state = State.Preparing

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tts.language =
                context.detectLocaleFromText(text.take(text.lastIndex.coerceAtMost(500)))
                    .firstOrNull()?.locale
        }

        // Paragraphs, with very long ones cut into pieces the speech engine accepts.
        val maxLength = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(500) - 100
        pieces = text.split("\n").filterNot { it.isBlank() }.flatMap { splitForSpeech(it, maxLength) }
        if (pieces.isEmpty()) {
            state = State.Idle
            return
        }
        val total = pieces.size
        val run = Any().also { currentRun = it }
        state = State.Reading(1, total)
        // The notification with Stop, which also keeps reading going outside the article.
        ReadAloudService.start(context)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (currentRun !== run) return
                val index = (utteranceId?.toIntOrNull() ?: 1) - 1
                state = State.Reading(index + 1, total)
            }

            override fun onDone(utteranceId: String?) {
                if (currentRun !== run) return
                val index = (utteranceId?.toIntOrNull() ?: 1) - 1
                if (index >= total - 1) {
                    currentRun = null
                    state = State.Idle
                }
            }

            override fun onError(utteranceId: String?) {
                if (currentRun !== run) return
                state = State.Error
            }
        })

        for (index in 0 until total) {
            tts.speak(pieces[index], TextToSpeech.QUEUE_ADD, null, (index + 1).toString())
        }
    }

    /** Cuts [text] into pieces of at most [max] characters, at sentence ends or spaces. */
    private fun splitForSpeech(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val result = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val window = rest.substring(0, max)
            val cut =
                listOf(". ", "! ", "? ", "; ", ", ", " ")
                    .map { window.lastIndexOf(it) }
                    .firstOrNull { it > max / 2 }
                    ?.plus(1) ?: max
            result += rest.substring(0, cut)
            rest = rest.substring(cut)
        }
        if (rest.isNotBlank()) result += rest
        return result
    }

    fun stop() {
        currentRun = null
        tts.stop()
        state = State.Idle
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.detectLocaleFromText(
    text: CharSequence,
    minConfidence: Float = 80.0f,
): Sequence<LocaleWithConfidence> {
    val textClassificationManager =
        getSystemService<TextClassificationManager>() ?: return emptySequence()
    val textClassifier = textClassificationManager.textClassifier

    val textRequest = TextLanguage.Request.Builder(text).build()
    val detectedLanguage = textClassifier.detectLanguage(textRequest)

    return sequence {
        for (i in 0 until detectedLanguage.localeHypothesisCount) {
            val localeDetected = detectedLanguage.getLocale(i)
            val confidence = detectedLanguage.getConfidenceScore(localeDetected) * 100.0f
            if (confidence >= minConfidence) {
                yield(
                    LocaleWithConfidence(
                        locale = localeDetected.toLocale(),
                        confidence = confidence,
                    ),
                )
            }
        }
    }
}

data class LocaleWithConfidence(
    val locale: Locale,
    val confidence: Float,
)

