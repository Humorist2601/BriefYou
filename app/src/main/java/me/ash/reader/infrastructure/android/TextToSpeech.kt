package me.ash.reader.infrastructure.android

import android.content.Context
import android.os.Build
import android.os.SystemClock
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

/**
 * Read aloud. Progress comes from the words as they are spoken (the speech engine reports where
 * each word starts), counted in characters of the whole text, so it matches the real position.
 *
 * The speech engine can't pause, so pausing stops it and remembers the word; playing again starts
 * from that word. Seeking works the same way. [ReadAloudService] shows the system media
 * notification and controls while reading or paused.
 */
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
    private var runCounter = 0

    /** The title of what is being read (shown in the media notification). */
    @Volatile var title: String? = null
        private set

    /** What is being read, in the pieces sent to the engine, and where each starts. */
    private var pieces: List<String> = emptyList()
    private var starts: IntArray = IntArray(0)
    private var totalChars = 0

    /** How long a character takes to say, measured while reading (for the time shown). */
    @Volatile private var msPerChar = DEFAULT_MS_PER_CHAR
    private var segmentStartChars = 0
    private var segmentStartTime = 0L

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

        /** Reading: [spoken] of [total] characters said so far. */
        class Reading(val spoken: Int, val total: Int) : State {
            val progress: Float
                get() = if (total > 0) (spoken.toFloat() / total).coerceIn(0f, 1f) else 0f
        }

        /** Paused at character [spoken] of [total]. */
        class Paused(val spoken: Int, val total: Int) : State {
            val progress: Float
                get() = if (total > 0) (spoken.toFloat() / total).coerceIn(0f, 1f) else 0f
        }

        object Error : State
    }

    /** About how long the whole text takes to read, and how far in it is (milliseconds). */
    val durationMs: Long
        get() = (totalChars * msPerChar).toLong()

    val positionMs: Long
        get() =
            when (val s = state) {
                is State.Reading -> (s.spoken * msPerChar).toLong()
                is State.Paused -> (s.spoken * msPerChar).toLong()
                else -> 0L
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
        if (state != State.Idle) return

        state = State.Preparing
        this.title = title

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
        starts = IntArray(pieces.size)
        var at = 0
        pieces.forEachIndexed { index, piece ->
            starts[index] = at
            at += piece.length
        }
        totalChars = at
        msPerChar = DEFAULT_MS_PER_CHAR
        speakFrom(0)
    }

    /** Pauses (stops the engine and remembers the word). */
    fun pause() {
        val s = state as? State.Reading ?: return
        currentRun = null
        tts.stop()
        state = State.Paused(s.spoken, s.total)
    }

    /** Plays again from where it was paused. */
    fun resume() {
        val s = state as? State.Paused ?: return
        speakFrom(s.spoken)
    }

    /** Jumps to [positionMs] (from the media controls' progress bar). */
    fun seekTo(positionMs: Long) {
        if (totalChars == 0 || (state !is State.Reading && state !is State.Paused)) return
        val chars = (positionMs / msPerChar).toInt().coerceIn(0, totalChars - 1)
        if (state is State.Paused) {
            state = State.Paused(chars, totalChars)
        } else {
            speakFrom(chars)
        }
    }

    /** Starts speaking at character [offset] of the whole text (from the start of its word). */
    private fun speakFrom(offset: Int) {
        if (pieces.isEmpty()) return
        tts.stop()
        val run = Any().also { currentRun = it }
        val runId = ++runCounter
        val first = (starts.indexOfLast { it <= offset }).coerceAtLeast(0)
        val firstPiece = pieces[first]
        val local =
            (offset - starts[first]).coerceIn(0, firstPiece.length).let { pos ->
                if (pos == 0) 0 else firstPiece.lastIndexOf(' ', pos).let { if (it < 0) 0 else it + 1 }
            }
        // Where each utterance starts in the whole text.
        val bases = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val lastId = "$runId:${pieces.lastIndex}"
        val total = totalChars

        segmentStartChars = starts[first] + local
        segmentStartTime = SystemClock.elapsedRealtime()
        state = State.Reading(segmentStartChars, total)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (currentRun !== run) return
                bases[utteranceId]?.let { update(it, total) }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (currentRun !== run) return
                bases[utteranceId]?.let { update(it + start, total) }
            }

            override fun onDone(utteranceId: String?) {
                if (currentRun !== run) return
                if (utteranceId == lastId) {
                    currentRun = null
                    state = State.Idle
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (currentRun !== run) return
                currentRun = null
                state = State.Error
            }
        })

        for (index in first..pieces.lastIndex) {
            val id = "$runId:$index"
            val text = if (index == first) firstPiece.substring(local) else pieces[index]
            bases[id] = if (index == first) starts[index] + local else starts[index]
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        }
        // The media notification and controls, which also keep reading going outside the article.
        ReadAloudService.start(context)
    }

    private fun update(spoken: Int, total: Int) {
        // Learn the reading speed as it goes, for the time shown in the media controls.
        val said = spoken - segmentStartChars
        if (said > 120) {
            val measured = (SystemClock.elapsedRealtime() - segmentStartTime).toFloat() / said
            if (measured in 20f..400f) msPerChar = measured
        }
        state = State.Reading(spoken, total)
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

    private companion object {
        /** About 14 characters a second, until the real speed is measured. */
        const val DEFAULT_MS_PER_CHAR = 70f
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

