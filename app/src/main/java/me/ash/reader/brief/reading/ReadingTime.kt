package me.ash.reader.brief.reading

import kotlin.math.ceil

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
