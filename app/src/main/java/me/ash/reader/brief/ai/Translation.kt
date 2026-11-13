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
import kotlinx.coroutines.NonCancellable
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

/** The translation of the article on screen, for the web view to patch its page in place. */
val LocalTranslation = androidx.compose.runtime.compositionLocalOf<TranslationState?> { null }

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

    /** Paragraphs the model left in the original (it skipped their numbers), when the run ended. */
    var skipped by mutableIntStateOf(0)
        private set

    /** The article text this translation was made from, to notice when the article changes under it. */
    var source by mutableStateOf<String?>(null)
        private set

    /** The article's text, translated so far (the rest still in the original). */
    var html by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf<String?>(null)
        private set

    /**
     * What the web view is given: the page as it was when the run began, with each text block
     * marked (`data-bt`). Later paragraphs reach it as [patches] (swapped in place, so the page
     * doesn't reload and flicker); it only changes when the translation is switched back on.
     */
    var shownHtml by mutableStateOf<String?>(null)
        private set

    /** How many [patches] [shownHtml] already includes. */
    var shownPatchCount by mutableIntStateOf(0)
        private set

    /** Finished paragraphs as (block number, inner HTML); (-1, "") means the run ended: nothing dimmed. */
    val patches = androidx.compose.runtime.mutableStateListOf<Pair<Int, String>>()

    private var showingState by mutableStateOf(true)

    /** Showing the translation (true) or the original, with the translation kept (false). */
    var showing: Boolean
        get() = showingState
        set(value) {
            if (value && !showingState) {
                shownHtml = html
                shownPatchCount = patches.size
            }
            showingState = value
        }

    /** The page to show: the web view takes the stable one (it is patched in place), the native reader the latest. */
    fun htmlFor(webView: Boolean): String? = if (webView) shownHtml else html

    val progress: Float
        get() = if (total <= 0) 0f else done.toFloat() / total

    private var job: Job? = null

    /** Back to the original (a new article, or after stopping with nothing translated). */
    fun reset() {
        generation++
        job?.cancel()
        job = null
        status = Status.Idle
        html = null
        shownHtml = null
        shownPatchCount = 0
        patches.clear()
        title = null
        error = null
        done = 0
        skipped = 0
        source = null
        showingState = true
    }

    fun stop() {
        if (status != Status.Running) return
        // Nothing translated yet: back to the plain article. Otherwise keep what is done.
        if (done == 0) {
            reset()
            return
        }
        job?.cancel()
        job = null
        status = Status.Done
    }

    /** Bumped by every start and reset, so a run that was replaced can't publish its leftovers. */
    private var generation = 0

    fun start(scope: CoroutineScope, summarizer: GemmaSummarizer, articleTitle: String?, source: String, tag: String) {
        reset()
        val gen = ++generation
        status = Status.Running
        language = tag
        this.source = source
        job =
            scope.launch {
                var doc: org.jsoup.nodes.Document? = null
                val dimmed = mutableMapOf<Element, String?>() // block -> its own style, to put back
                try {
                    val name = TranslationPrefs.englishName(tag)
                    val parsed = withContext(Dispatchers.Default) { Jsoup.parseBodyFragment(source) }
                    doc = parsed
                    // The text blocks that hold text directly (not a list item that holds paragraphs).
                    val blocks =
                        withContext(Dispatchers.Default) {
                            parsed.body().select(BLOCKS).filter { it.select(BLOCKS).size == 1 && it.text().length > 1 }
                        }
                    // Each block's text with its links and bold/italic words as numbered tags (<1>…</1>),
                    // so the translation can put them back around the translated words.
                    val tagged = withContext(Dispatchers.Default) { blocks.associateWith { tag(it) } }
                    // Index 0 is the title; the blocks follow.
                    val items = buildList<Pair<Int, Element?>> {
                        if (!articleTitle.isNullOrBlank()) add(0 to null)
                        blocks.forEachIndexed { i, b -> add((i + 1) to b) }
                    }
                    fun textOf(e: Element?) = if (e == null) articleTitle.orEmpty() else tagged.getValue(e).text
                    // The first batch is small, so the first words show within seconds.
                    val batches = mutableListOf<MutableList<Pair<Int, Element?>>>()
                    var size = 0
                    for (item in items) {
                        val len = textOf(item.second).length
                        val limit = if (batches.size <= 1) FIRST_BATCH_CHARS else BATCH_CHARS
                        if (batches.isEmpty() || size + len > limit) {
                            batches += mutableListOf<Pair<Int, Element?>>()
                            size = 0
                        }
                        batches.last() += item
                        size += len
                    }
                    total = items.size.coerceAtLeast(1)
                    // What isn't translated yet is shown dimmed, the translated text at full strength.
                    blocks.forEachIndexed { i, it ->
                        dimmed[it] = it.attr("style").ifBlank { null }
                        it.attr("style", (it.attr("style") + ";opacity:.45").trim(';'))
                        it.attr("data-bt", (i + 1).toString())
                    }
                    html = withContext(Dispatchers.Default) { parsed.body().html() }
                    shownHtml = html
                    shownPatchCount = 0

                    val applied = mutableSetOf<Int>()
                    suspend fun apply(batch: List<Pair<Int, Element?>>, out: String, finished: Boolean) {
                        val byIndex = batch.associate { it.first to it.second }
                        val marks = MARKER.findAll(out).toList()
                        var changed = false
                        for ((k, m) in marks.withIndex()) {
                            val last = k == marks.lastIndex
                            if (last && !finished) break // still being written
                            val idx = m.groupValues[1].toIntOrNull() ?: continue
                            if (!byIndex.containsKey(idx) || idx in applied) continue
                            val end = if (last) out.length else marks[k + 1].range.first
                            val text = out.substring(m.range.last + 1, end).trim()
                            if (text.isBlank()) continue
                            val element = byIndex[idx]
                            if (element == null) title = stripTags(text)
                            else {
                                // Links and bold words come back around the translated words; if the
                                // model broke the tags, it is plain text.
                                val rebuilt = rebuild(text, tagged.getValue(element).marks)
                                if (rebuilt != null) element.html(rebuilt) else element.text(stripTags(text))
                                if (dimmed.containsKey(element)) {
                                    val own = dimmed.remove(element)
                                    if (own == null) element.removeAttr("style") else element.attr("style", own)
                                }
                                patches += idx to element.html()
                            }
                            applied += idx
                            changed = true
                        }
                        if (changed) {
                            done = applied.size
                            html = withContext(Dispatchers.Default) { parsed.body().html() }
                        }
                    }
                    for (batch in batches) {
                        val prompt = batch.joinToString("\n") { "[${it.first}] " + textOf(it.second) }
                        var out = ""
                        summarizer.translate(name, prompt).collect {
                            out = it
                            apply(batch, out, finished = false) // each paragraph shows once the next begins
                        }
                        apply(batch, out, finished = true)
                    }
                    skipped = items.size - applied.size
                    if (applied.isEmpty() && items.isNotEmpty()) {
                        // The model answered, but not in the numbered form: nothing could be used.
                        error = null
                        status = Status.Failed
                    } else {
                        status = Status.Done
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error = e.message ?: e.javaClass.simpleName
                    status = Status.Failed
                } finally {
                    // Stopped or finished: nothing stays dimmed.
                    val d = doc
                    if (d != null && gen == generation) {
                        withContext(NonCancellable + Dispatchers.Default) {
                            dimmed.forEach { (el, own) -> if (own == null) el.removeAttr("style") else el.attr("style", own) }
                            if (html != null) {
                                html = d.body().html()
                                patches += -1 to ""
                            }
                        }
                    }
                }
            }
    }

    private companion object {
        const val BLOCKS = "p, h1, h2, h3, h4, h5, h6, li, figcaption, blockquote, td, th"
        const val BATCH_CHARS = 1_800
        const val FIRST_BATCH_CHARS = 450
        val MARKER = Regex("""(?m)^\s*\[(\d+)]\s*""")
    }
}

private val INLINE = setOf("a", "b", "strong", "i", "em")
private val TAG = Regex("""<(/?)(\d+)>""")
private val SPACES = Regex("\\s+")

/** A block's text, with its links and bold/italic words wrapped in numbered tags, and those words' elements. */
private class Tagged(val text: String, val marks: List<Element>)

private fun tag(block: Element): Tagged {
    val marks = mutableListOf<Element>()
    val sb = StringBuilder()
    fun walk(n: org.jsoup.nodes.Node) {
        when (n) {
            is org.jsoup.nodes.TextNode -> sb.append(n.text())
            is Element ->
                if (n.tagName() in INLINE && n.text().isNotBlank()) {
                    marks += n
                    val id = marks.size
                    sb.append("<$id>")
                    n.childNodes().forEach { walk(it) }
                    sb.append("</$id>")
                } else if (n.tagName() == "br") sb.append(' ') else n.childNodes().forEach { walk(it) }
        }
    }
    block.childNodes().forEach { walk(it) }
    return Tagged(sb.toString().replace(SPACES, " ").trim(), marks)
}

private fun stripTags(text: String) = text.replace(TAG, "").replace(SPACES, " ").trim()

private fun openTag(e: Element): String {
    val attrs = e.attributes().html()
    return "<${e.tagName()}" + (if (attrs.isBlank()) "" else " $attrs") + ">"
}

/**
 * The translated text with its tags turned back into the original links and bold/italic elements,
 * as HTML; null when the model left the tags out or broke them (the caller then uses plain text).
 */
private fun rebuild(translated: String, marks: List<Element>): String? {
    if (marks.isEmpty()) return null
    val out = StringBuilder()
    val open = ArrayDeque<Int>()
    val seen = mutableSetOf<Int>()
    var pos = 0
    for (m in TAG.findAll(translated)) {
        out.append(org.jsoup.nodes.Entities.escape(translated.substring(pos, m.range.first)))
        pos = m.range.last + 1
        val id = m.groupValues[2].toIntOrNull() ?: return null
        if (id < 1 || id > marks.size) return null
        if (m.groupValues[1].isEmpty()) {
            if (!seen.add(id)) return null
            open.addLast(id)
            out.append(openTag(marks[id - 1]))
        } else {
            if (open.lastOrNull() != id) return null
            open.removeLast()
            out.append("</${marks[id - 1].tagName()}>")
        }
    }
    out.append(org.jsoup.nodes.Entities.escape(translated.substring(pos)))
    return if (open.isEmpty()) out.toString() else null
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
                            stringResource(R.string.translate_progress, t.done.coerceAtMost(t.total), t.total)
                        TranslationState.Status.Failed -> t.error ?: stringResource(R.string.translate_nothing)
                        else ->
                            if (t.skipped > 0) stringResource(R.string.translate_skipped, t.skipped)
                            else stringResource(R.string.translate_by_ai)
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
