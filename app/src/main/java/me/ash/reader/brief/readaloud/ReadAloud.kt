package me.ash.reader.brief.readaloud

import me.ash.reader.brief.readaloud.ReadAloudService

import kotlinx.coroutines.delay
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
 * Read aloud.
 *
 * What is read is a list of parts: an article's sentences, taken from the page as it is shown (so
 * the page can follow along exactly), or the paragraphs of plain text. Each part is spoken as its
 * own utterance (long ones split further), so the part being read is known on any speech engine:
 * from "started" reports when the engine sends them, and from "finished" reports otherwise.
 * Within a part, progress follows the words when the engine reports them, and is estimated from
 * the measured reading speed when it doesn't.
 *
 * The engine can't pause, so pausing stops it and remembers the place; playing again starts from
 * there (the start of its word). Seeking works the same way. [ReadAloudService] shows the system
 * media notification and controls while reading or paused.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
    @param:ApplicationScope
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

    /** What is being read (the article's id), so its page can follow along. */
    @Volatile var sourceKey: String? = null
        private set

    /**
     * The colour of what is being read (the article page's accent, from its picture when it has
     * one), which fills the media notification. Null: the app's accent colour.
     */
    @Volatile var color: Int? = null

    private val prefs = context.getSharedPreferences("read_aloud", Context.MODE_PRIVATE)

    /** Reading speed, 0.8× to 2× in steps of 0.2; the last one used is kept. */
    private val _speed = MutableStateFlow(snap(prefs.getFloat(KEY_SPEED, 1f)))
    val speed = _speed.asStateFlow()

    /** What is being read: the pieces sent to the engine, and where each starts. */
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
                TextToSpeech.SUCCESS -> tts.setSpeechRate(_speed.value)
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

    /** Reads an article aloud from its HTML. [key] names it (the article's id). */
    fun readHtml(
        htmlContent: String,
        title: String? = null,
        key: String? = null,
        color: Int? = null,
    ) {
        this.color = color
        coroutineScope.launch {
            val plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
            read(plainText.split("\n"), title, key)
        }
    }

    /** Reads plain text aloud; each line is a part. */
    fun readPlainText(text: String, title: String? = null, color: Int? = null, key: String? = null) {
        this.color = color
        coroutineScope.launch { read(text.split("\n"), title, key) }
    }

    /** Sets the reading speed; while reading it carries on from the same word at the new speed. */
    fun setSpeed(value: Float) {
        val old = _speed.value
        val new = snap(value)
        if (new == old) return
        _speed.value = new
        prefs.edit().putFloat(KEY_SPEED, new).apply()
        tts.setSpeechRate(new)
        msPerChar = msPerChar * old / new
        (state as? State.Reading)?.let { speakFrom(it.spoken) }
    }

    /** The next speed (back to 0.8× after 2×). */
    fun cycleSpeed() {
        val next = SPEEDS.firstOrNull { it > _speed.value + 0.01f } ?: SPEEDS.first()
        setSpeed(next)
    }

    private fun read(parts: List<String>, title: String?, key: String?) {
        stop()
        if (state != State.Idle) return

        state = State.Preparing
        this.title = title
        this.sourceKey = key
        tts.setSpeechRate(_speed.value)

        val sample = parts.joinToString(" ").take(500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sample.isNotBlank()) {
            tts.language = context.detectLocaleFromText(sample).firstOrNull()?.locale
        }

        // Each part, with very long ones cut into pieces the speech engine accepts. Empty parts
        // aren't spoken.
        val maxLength = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(500) - 100
        val pieceList = ArrayList<String>()
        for (part in parts) {
            if (part.isBlank()) continue
            pieceList += splitForSpeech(part.trim(), maxLength)
        }
        if (pieceList.isEmpty()) {
            state = State.Idle
            return
        }
        pieces = pieceList
        starts = IntArray(pieces.size)
        var at = 0
        pieces.forEachIndexed { index, piece ->
            starts[index] = at
            at += piece.length + 1
        }
        totalChars = at
        msPerChar = DEFAULT_MS_PER_CHAR / _speed.value
        speakFrom(0)
    }

    /** Pauses (stops the engine and remembers the place). */
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

    private fun pieceAt(offset: Int): Int = (starts.indexOfLast { it <= offset }).coerceAtLeast(0)

    /** Starts speaking at character [offset] (from the start of its word). */
    private fun speakFrom(offset: Int) {
        if (pieces.isEmpty()) return
        tts.stop()
        val run = Any().also { currentRun = it }
        val runId = ++runCounter
        val first = pieceAt(offset)
        val firstPiece = pieces[first]
        val local =
            (offset - starts[first]).coerceIn(0, firstPiece.length).let { pos ->
                if (pos == 0) 0 else firstPiece.lastIndexOf(' ', pos).let { if (it < 0) 0 else it + 1 }
            }
        val total = totalChars
        val lastIndex = pieces.lastIndex

        segmentStartChars = starts[first] + local
        segmentStartTime = SystemClock.elapsedRealtime()

        // Which piece is being spoken, where it started (in the whole text) and when, and where
        // it ends. Updated from the engine's reports (see the listener).
        var current = first
        var known = segmentStartChars
        var knownAt = SystemClock.elapsedRealtime()
        // Whether the engine reports each word; if not, the place in a piece is estimated.
        var wordReports = false

        fun base(index: Int) = if (index == first) starts[index] + local else starts[index]
        fun pieceEnd(index: Int) = (starts.getOrNull(index + 1) ?: total) - 1

        fun startPiece(index: Int) {
            current = index
            known = base(index)
            knownAt = SystemClock.elapsedRealtime()
            update(known, total)
        }

        startPiece(first)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (currentRun !== run) return
                val index = utteranceId?.substringAfter(':')?.toIntOrNull() ?: return
                if (index >= current) startPiece(index)
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (currentRun !== run) return
                val index = utteranceId?.substringAfter(':')?.toIntOrNull() ?: return
                wordReports = true
                if (index > current) startPiece(index)
                if (index == current) update(base(index) + start, total)
            }

            override fun onDone(utteranceId: String?) {
                if (currentRun !== run) return
                val index = utteranceId?.substringAfter(':')?.toIntOrNull() ?: return
                if (index >= lastIndex) {
                    currentRun = null
                    state = State.Idle
                } else if (index >= current) {
                    // Engines that don't report starts: the next piece starts now.
                    startPiece(index + 1)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (currentRun !== run) return
                currentRun = null
                state = State.Error
            }
        })

        for (index in first..lastIndex) {
            val text = if (index == first) firstPiece.substring(local) else pieces[index]
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "$runId:$index")
        }
        // Without word reports, the place moves on through the piece at the measured reading
        // speed (never past its end), so the progress keeps moving.
        coroutineScope.launch {
            while (currentRun === run) {
                delay(ESTIMATE_EVERY_MS)
                if (currentRun !== run || wordReports) continue
                val s = state as? State.Reading ?: continue
                val estimate =
                    (known + ((SystemClock.elapsedRealtime() - knownAt) / msPerChar).toInt())
                        .coerceAtMost(pieceEnd(current))
                if (estimate > s.spoken) state = State.Reading(estimate, total)
            }
        }
        // The media notification and controls, which also keep reading going outside the article.
        ReadAloudService.start(context)
    }

    private fun update(spoken: Int, total: Int) {
        // Learn the reading speed as it goes, from real reports only.
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

    companion object {
        /** About 14 characters a second at 1×, until the real speed is measured. */
        private const val DEFAULT_MS_PER_CHAR = 70f
        private const val ESTIMATE_EVERY_MS = 250L
        private const val KEY_SPEED = "speed"

        /** The speeds to choose from: 0.8× to 2× in steps of 0.2. */
        val SPEEDS = listOf(0.8f, 1f, 1.2f, 1.4f, 1.6f, 1.8f, 2f)

        /** The nearest speed step. */
        fun snap(value: Float): Float = SPEEDS.minBy { kotlin.math.abs(it - value) }

        /** "1.2×". */
        fun speedLabel(speed: Float): String =
            String.format(Locale.US, "%.1f", speed).trimEnd('0').trimEnd('.') + "\u00D7"
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

