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

    // Brief You: the speech engine and rate are chosen in Settings → AI & voice.
    private val voicePrefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    /** Package of the chosen speech engine, or null for the system default. */
    var enginePackage: String? = voicePrefs.getString("engine", null)
        private set

    var speechRate: Float = voicePrefs.getFloat("rate", 1f)
        private set

    private var tts: TextToSpeech = initTts()

    private fun initTts(): TextToSpeech {
        val listener = TextToSpeech.OnInitListener {
            when (it) {
                TextToSpeech.SUCCESS -> {
                    if (state == State.Error) state = State.Idle
                }
                else -> {
                    state = State.Error
                    Timber.e("TextToSpeech initialization failed $it")
                }
            }
        }
        return (enginePackage?.let { TextToSpeech(context, listener, it) }
                ?: TextToSpeech(context, listener))
            .also { it.setSpeechRate(speechRate) }
    }

    /** Installed speech engines as (package, label). */
    fun installedEngines(): List<Pair<String, String>> =
        tts.engines.map { it.name to it.label }

    /** The engine actually in use (the chosen one, or the system default). */
    fun currentEngine(): String? = enginePackage ?: tts.defaultEngine

    fun setEngine(pkg: String?) {
        stop()
        tts.shutdown()
        enginePackage = pkg
        voicePrefs.edit().apply { if (pkg == null) remove("engine") else putString("engine", pkg) }.apply()
        tts = initTts()
    }

    fun setRate(rate: Float) {
        speechRate = rate
        voicePrefs.edit().putFloat("rate", rate).apply()
        tts.setSpeechRate(rate)
    }

    sealed interface State {
        object Idle : State
        object Preparing : State
        class Reading(val current: Int, val total: Int) : State {
            val progress: Float
                get() = current.toFloat() / total
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

        val textSegments = text.split("\n").filterNot { it.isBlank() }
        val total = textSegments.size
        state = State.Reading(0, total)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                state = State.Reading(utteranceId?.toIntOrNull() ?: 0, total)
            }

            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: 0
                val cur = state
                if (cur is State.Reading && index >= cur.total) {
                    state = State.Idle
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