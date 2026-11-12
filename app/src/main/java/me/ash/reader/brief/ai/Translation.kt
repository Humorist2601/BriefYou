package me.ash.reader.brief.ai

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.brief.ui.SheetActions
import me.ash.reader.ui.ext.showToast
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** The language articles are translated to: the phone's language until another is chosen. */
object TranslationPrefs {
    private const val PREFS = "translate"
    private const val KEY = "language"

    /** Languages offered besides the phone's own. */
    val LANGUAGES = listOf("en", "es", "fr", "de", "pt", "it", "ar", "hi", "ur", "tr", "ru", "zh", "ja", "ko", "id")

    fun phoneLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    fun language(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: phoneLanguage()

    fun setLanguage(context: Context, tag: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, tag) }

    /** The name shown to the reader, in the reader's language. */
    fun displayName(tag: String): String =
        Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }

    /** The name the model is told, in English. */
    fun englishName(tag: String): String = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH)
}

/** What translating the article being read has done so far. */
@Stable
class TranslationState {
    enum class Status { Idle, Running, Done, Failed }

    var status by mutableStateOf(Status.Idle)
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(1)
        private set
    var language by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** The article's text, translated so far (the rest still in the original). */
    var html by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf<String?>(null)
        private set

    /** Showing the translation (true) or the original, with the translation kept (false). */
    var showing by mutableStateOf(true)

    val progress: Float
        get() = if (total <= 0) 0f else done.toFloat() / total

    private var job: Job? = null

    /** Back to the original (a new article, or after stopping with nothing translated). */
    fun reset() {
        job?.cancel()
        job = null
        status = Status.Idle
        html = null
        title = null
        error = null
        done = 0
        showing = true
    }

    fun stop() {
        job?.cancel()
        job = null
        if (status == Status.Running) status = if (html != null) Status.Done else Status.Idle
    }

    fun start(scope: CoroutineScope, summarizer: GemmaSummarizer, articleTitle: String?, source: String, tag: String) {
        reset()
        status = Status.Running
        language = tag
        job =
            scope.launch {
                try {
                    val name = TranslationPrefs.englishName(tag)
                    val doc = withContext(Dispatchers.Default) { Jsoup.parseBodyFragment(source) }
                    // The text blocks that hold text directly (not a list item that holds paragraphs).
                    val blocks =
                        withContext(Dispatchers.Default) {
                            doc.body().select(BLOCKS).filter { it.select(BLOCKS).size == 1 && it.text().length > 1 }
                        }
                    // Index 0 is the title; the blocks follow. Each batch is a few paragraphs.
                    val items = buildList<Pair<Int, Element?>> {
                        if (!articleTitle.isNullOrBlank()) add(0 to null)
                        blocks.forEachIndexed { i, b -> add((i + 1) to b) }
                    }
                    fun textOf(index: Int, e: Element?) = if (e == null) articleTitle.orEmpty() else e.text()
                    val batches = mutableListOf<MutableList<Pair<Int, Element?>>>()
                    var size = 0
                    for (item in items) {
                        val len = textOf(item.first, item.second).length
                        if (batches.isEmpty() || size + len > BATCH_CHARS) {
                            batches += mutableListOf<Pair<Int, Element?>>()
                            size = 0
                        }
                        batches.last() += item
                        size += len
                    }
                    total = batches.size.coerceAtLeast(1)
                    for (batch in batches) {
                        val prompt = batch.joinToString("\n") { "[${it.first}] " + textOf(it.first, it.second) }
                        var out = ""
                        summarizer.translate(name, prompt).collect { out = it }
                        val marks = MARKER.findAll(out).toList()
                        val byIndex = batch.associate { it.first to it.second }
                        for ((k, m) in marks.withIndex()) {
                            val idx = m.groupValues[1].toIntOrNull() ?: continue
                            if (!byIndex.containsKey(idx)) continue
                            val end = marks.getOrNull(k + 1)?.range?.first ?: out.length
                            val text = out.substring(m.range.last + 1, end).trim()
                            if (text.isBlank()) continue
                            val element = byIndex[idx]
                            if (element == null) title = text else element.text(text)
                        }
                        html = withContext(Dispatchers.Default) { doc.body().html() }
                        done++
                    }
                    status = Status.Done
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error = e.message ?: e.javaClass.simpleName
                    status = Status.Failed
                }
            }
    }

    private companion object {
        const val BLOCKS = "p, h1, h2, h3, h4, h5, h6, li, figcaption, blockquote, td, th"
        const val BATCH_CHARS = 1_800
        val MARKER = Regex("""(?m)^\s*\[(\d+)]\s*""")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TranslationDependencies {
    fun summarizer(): GemmaSummarizer
}

/** The reading page's Translate button: tap translates (or flips original and translation). */
class TranslateController
internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val summarizer: GemmaSummarizer,
    val state: TranslationState,
    private val articleTitle: () -> String?,
    private val articleHtml: () -> String?,
) {
    fun start(tag: String) {
        val html = articleHtml()
        if (html.isNullOrBlank()) return
        if (!summarizer.hasModel()) {
            context.showToast(context.getString(R.string.translate_needs_model))
            return
        }
        TranslationPrefs.setLanguage(context, tag)
        state.start(scope, summarizer, articleTitle(), html, tag)
    }

    fun tap() {
        when (state.status) {
            TranslationState.Status.Idle, TranslationState.Status.Failed ->
                start(TranslationPrefs.language(context))
            TranslationState.Status.Running -> state.stop()
            TranslationState.Status.Done -> state.showing = !state.showing
        }
    }
}

@Composable
fun rememberTranslateController(
    state: TranslationState,
    articleTitle: () -> String?,
    articleHtml: () -> String?,
): TranslateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summarizer =
        remember {
            EntryPointAccessors.fromApplication(context.applicationContext, TranslationDependencies::class.java)
                .summarizer()
        }
    val title = androidx.compose.runtime.rememberUpdatedState(articleTitle)
    val html = androidx.compose.runtime.rememberUpdatedState(articleHtml)
    return remember(state) {
        TranslateController(context, scope, summarizer, state, { title.value() }, { html.value() })
    }
}

/** Under the headline while translating, and once translated (with the way back to the original). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranslationBanner(controller: TranslateController) {
    val t = controller.state
    if (t.status == TranslationState.Status.Idle) return
    val name = TranslationPrefs.displayName(t.language)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    when (t.status) {
                        TranslationState.Status.Running -> stringResource(R.string.translate_running, name)
                        TranslationState.Status.Failed -> stringResource(R.string.translate_failed)
                        else -> stringResource(R.string.translate_done, name)
                    },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    when (t.status) {
                        TranslationState.Status.Running ->
                            stringResource(R.string.translate_progress, (t.done + 1).coerceAtMost(t.total), t.total)
                        TranslationState.Status.Failed -> t.error.orEmpty()
                        else -> stringResource(R.string.translate_by_ai)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (t.status == TranslationState.Status.Running) {
                LinearWavyProgressIndicator(
                    progress = { t.progress.coerceAtLeast(0.04f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        OutlinedButton(
            onClick = {
                when (t.status) {
                    TranslationState.Status.Running -> t.stop()
                    TranslationState.Status.Failed -> t.reset()
                    else -> t.showing = !t.showing
                }
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(
                text =
                    stringResource(
                        when (t.status) {
                            TranslationState.Status.Running -> R.string.translate_stop
                            TranslationState.Status.Failed -> R.string.translate_dismiss
                            else ->
                                if (t.showing) R.string.translate_show_original else R.string.translate_show_translation
                        }
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Long press on Translate: pick the language, then translate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateLanguageSheet(controller: TranslateController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val phone = TranslationPrefs.phoneLanguage()
    var selected by remember { mutableStateOf(TranslationPrefs.language(context)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.translate_to),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.translate_to_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                (listOf(phone) + TranslationPrefs.LANGUAGES.filter { it != phone }).forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = tag }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == tag, onClick = { selected = tag })
                        Text(
                            text = TranslationPrefs.displayName(tag),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        if (tag == phone) {
                            Text(
                                text = stringResource(R.string.translate_phone_language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SheetActions(
                stringResource(R.string.translate_action) to {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismiss()
                        controller.start(selected)
                    }
                    Unit
                },
                null,
                dangerSecond = false,
            )
        }
    }
}
