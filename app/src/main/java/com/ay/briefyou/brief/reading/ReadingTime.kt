package com.ay.briefyou.brief.reading

import kotlin.math.ceil
import kotlinx.coroutines.launch

private const val LATIN_WORDS_PER_MINUTE = 230.0
private const val CJK_CHARS_PER_MINUTE = 400.0

/** Feeds that only send a teaser would give a misleading "1 min", so below this we show nothing. */
private const val MIN_WORDS_TO_ESTIMATE = 120
private const val MAX_CHARS_SCANNED = 200_000

/**
 * Estimated reading time for the article list. Returns null when the feed only provides a short
 * teaser, because the real length can't be known and "1 min" would be misleading.
 */
fun estimateReadingMinutes(html: String): Int? = readingMinutes(html, MIN_WORDS_TO_ESTIMATE)

/**
 * The article list's and Triage's reading times ([estimateReadingMinutes]), worked out once per
 * article and kept on the phone. The lists leave each article's text out (so their pages load
 * quickly), so a row can't count its words itself: [ensure] reads the text of an article not seen
 * before, in the background, while its list page is prepared, and drawing a row or a card only
 * looks the number up ([of]).
 */
object ListReadingTime {
    private const val NONE = -1
    private const val FILE = "reading_minutes.txt"
    /** Articles kept (a few thousand per year of reading, so this lasts years). */
    private const val MAX = 40_000

    private val known = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val scope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var file: java.io.File? = null
    @Volatile private var textOf: (suspend (String) -> String?)? = null
    private var saveJob: kotlinx.coroutines.Job? = null

    /** At app start: reads the saved times in the background. [textOf] reads one article's text. */
    fun start(context: android.content.Context, textOf: suspend (String) -> String?) {
        this.textOf = textOf
        val saved = java.io.File(context.filesDir, FILE)
        file = saved
        scope.launch {
            runCatching {
                if (saved.exists()) {
                    saved.forEachLine { line ->
                        val tab = line.lastIndexOf('\t')
                        if (tab > 0) line.substring(tab + 1).toIntOrNull()?.let { known.putIfAbsent(line.substring(0, tab), it) }
                    }
                }
            }
            loaded.complete(Unit)
        }
    }

    /** For drawing a row or a card: only a look-up. */
    fun of(id: String): Int? = known[id]?.takeIf { it != NONE }

    /** Makes sure [id]'s reading time is known; [html] if it is at hand, else it is read. */
    suspend fun ensure(id: String, html: String? = null) {
        if (known.containsKey(id)) return
        kotlinx.coroutines.withTimeoutOrNull(2_000) { loaded.await() }
        if (known.containsKey(id)) return
        val text = html?.takeIf { it.isNotEmpty() } ?: runCatching { textOf?.invoke(id) }.getOrNull() ?: return
        known[id] = estimateReadingMinutes(text) ?: NONE
        saveSoon()
    }

    @Synchronized
    private fun saveSoon() {
        saveJob?.cancel()
        saveJob =
            scope.launch {
                kotlinx.coroutines.delay(3_000)
                save()
            }
    }

    private fun save() {
        val saved = file ?: return
        if (known.size > MAX) known.keys.take(known.size - MAX * 4 / 5).forEach { known.remove(it) }
        runCatching {
            val temp = java.io.File(saved.path + ".tmp")
            temp.bufferedWriter().use { out ->
                for ((id, minutes) in known) {
                    if ('\n' in id || '\r' in id) continue
                    out.write(id)
                    out.write("\t")
                    out.write(minutes.toString())
                    out.write("\n")
                }
            }
            temp.renameTo(saved)
        }
    }
}

/**
 * Reading time in minutes for the HTML that is actually shown, or null if it has no words.
 * Pass [minWords] > 0 to refuse to estimate very short texts.
 */
fun readingMinutes(html: String, minWords: Int = 1): Int? {
    if (html.isBlank()) return null
    val (latinWords, cjkChars) = countWords(html, MAX_CHARS_SCANNED)

    if (latinWords + cjkChars / 2 < minWords.coerceAtLeast(1)) return null

    val minutes = latinWords / LATIN_WORDS_PER_MINUTE + cjkChars / CJK_CHARS_PER_MINUTE
    return ceil(minutes).toInt().coerceAtLeast(1)
}

/**
 * Words (runs of non-space with a letter or digit) and CJK characters in the text of [html], in
 * one pass without copying it: tags, script and style blocks and entities are skipped, and CJK
 * characters are counted one by one. It runs for every article in the list while you scroll, so
 * it avoids the four regular-expression passes (and copies of the article) it used before; the
 * counts are the same.
 */
internal fun countWords(html: String, limit: Int): Pair<Int, Int> {
    val end = minOf(html.length, limit)
    var words = 0
    var cjkChars = 0
    var inWord = false
    var wordHasLetter = false
    var i = 0
    fun endWord() {
        if (inWord && wordHasLetter) words++
        inWord = false
        wordHasLetter = false
    }
    while (i < end) {
        val c = html[i]
        when {
            c == '<' -> {
                endWord()
                // Script and style blocks: skip to their closing tag.
                val block =
                    when {
                        html.regionMatches(i + 1, "script", 0, 6, ignoreCase = true) -> "</script"
                        html.regionMatches(i + 1, "style", 0, 5, ignoreCase = true) -> "</style"
                        else -> null
                    }
                if (block != null) {
                    val close = html.indexOf(block, i + 1, ignoreCase = true)
                    i = if (close < 0) end else close
                }
                val tagEnd = html.indexOf('>', i + 1)
                i = if (tagEnd < 0) end else tagEnd + 1
                continue
            }
            c == '&' -> {
                // An entity (&amp; &#8217;) separates words, like a space.
                var j = i + 1
                while (j < end && j - i <= 10 && (html[j].isLetterOrDigit() || html[j] == '#')) j++
                if (j < end && html[j] == ';' && j > i + 1) {
                    endWord()
                    i = j + 1
                    continue
                }
                inWord = true
            }
            isCjk(c) -> {
                endWord()
                cjkChars++
            }
            c.isWhitespace() -> endWord()
            else -> {
                inWord = true
                if (c.isLetterOrDigit()) wordHasLetter = true
            }
        }
        i++
    }
    endWord()
    return words to cjkChars
}

private fun isCjk(c: Char): Boolean =
    c in '\u3040'..'\u30ff' || c in '\u3400'..'\u4dbf' || c in '\u4e00'..'\u9fff' || c in '\uac00'..'\ud7af'
