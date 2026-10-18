package me.ash.reader.brief.reading

import kotlin.math.ceil

private const val LATIN_WORDS_PER_MINUTE = 230.0
private const val CJK_CHARS_PER_MINUTE = 400.0

/** Feeds that only send a teaser would give a misleading "1 min", so below this we show nothing. */
private const val MIN_WORDS_TO_ESTIMATE = 120
private const val MAX_CHARS_SCANNED = 200_000

private val scriptOrStyle =
    Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val htmlTag = Regex("<[^>]*>")
private val htmlEntity = Regex("&[#a-zA-Z0-9]+;")
private val cjk =
    Regex("[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uac00-\\ud7af]")
private val whitespace = Regex("\\s+")

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
    val text =
        html
            .take(MAX_CHARS_SCANNED)
            .replace(scriptOrStyle, " ")
            .replace(htmlTag, " ")
            .replace(htmlEntity, " ")

    val cjkChars = cjk.findAll(text).count()
    val latinWords =
        text.replace(cjk, " ").trim().split(whitespace).count { it.any(Char::isLetterOrDigit) }

    if (latinWords + cjkChars / 2 < minWords.coerceAtLeast(1)) return null

    val minutes = latinWords / LATIN_WORDS_PER_MINUTE + cjkChars / CJK_CHARS_PER_MINUTE
    return ceil(minutes).toInt().coerceAtLeast(1)
}
