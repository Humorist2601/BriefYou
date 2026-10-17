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
        /**
         * Reading part [current] of [total]. [fraction] is how much of the text has been read,
         * by characters, following the word being spoken.
         */
        class Reading(val current: Int, val total: Int, private val fraction: Float? = null) : State {
            val progress: Float
                get() = fraction ?: (current.toFloat() / total)
        }

        object Error : State
    }


    fun readHtml(htmlContent: String) {
        coroutineScope.launch {
            val plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
            readText(plainText)
        }
    }

    /** Reads plain text aloud; each line becomes its own utterance. */
    fun readPlainText(text: String) {
        coroutineScope.launch { readText(text) }
    }

    private fun readText(text: String) {
        stop()

        if (state != State.Idle) return

        state = State.Preparing

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tts.language =
                context.detectLocaleFromText(text.take(text.lastIndex.coerceAtMost(500)))
                    .firstOrNull()?.locale
        }

        // Paragraphs, with very long ones cut into pieces the speech engine accepts.
        val maxLength = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(500) - 100
        val textSegments =
            text.split("\n").filterNot { it.isBlank() }.flatMap { splitForSpeech(it, maxLength) }
        val total = textSegments.size
        if (total == 0) {
            state = State.Idle
            return
        }
        // Where each piece starts in the whole text, so the progress follows the text's length
        // (a short heading moves it a little, a long paragraph a lot).
        val starts = IntArray(total)
        var length = 0
        textSegments.forEachIndexed { i, segment ->
            starts[i] = length
            length += segment.length
        }
        val totalLength = length.coerceAtLeast(1)

        fun reading(index: Int, charInSegment: Int) {
            val i = index.coerceIn(0, total - 1)
            val read = (starts[i] + charInSegment).coerceIn(0, totalLength)
            state = State.Reading(i + 1, total, read.toFloat() / totalLength)
        }

        state = State.Reading(0, total, 0f)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val index = (utteranceId?.toIntOrNull() ?: 1) - 1
                reading(index, 0)
            }

            // The word being spoken (Android 8+), so the progress moves along as it reads.
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val index = (utteranceId?.toIntOrNull() ?: 1) - 1
                reading(index, start)
            }

            override fun onDone(utteranceId: String?) {
                val index = (utteranceId?.toIntOrNull() ?: 1) - 1
                if (index >= total - 1) {
                    state = State.Idle
                } else {
                    reading(index + 1, 0)
                }
            }

            override fun onError(utteranceId: String?) {
                state = State.Error
            }
        })

        textSegments.forEachIndexed { index, text ->
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, (index + 1).toString())
        }
    }

    /** Cuts [text] into pieces of at most [max] characters, at sentence ends or spaces. */
    private fun splitForSpeech(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val pieces = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val window = rest.substring(0, max)
            val cut =
                listOf(". ", "! ", "? ", "; ", ", ", " ")
                    .map { window.lastIndexOf(it) }
                    .firstOrNull { it > max / 2 }
                    ?.plus(1) ?: max
            pieces += rest.substring(0, cut)
            rest = rest.substring(cut)
        }
        if (rest.isNotBlank()) pieces += rest
        return pieces
    }

    fun stop() {
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