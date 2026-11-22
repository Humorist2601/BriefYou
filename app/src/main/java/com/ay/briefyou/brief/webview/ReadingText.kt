package com.ay.briefyou.brief.webview

/**
 * Brief You's touches to how an article's text and links look in the reading web view (on top of
 * The original style, WebViewStyle):
 * - Links have the weight of the text around them (the original app made every link semi-bold, so links
 *   outshone the writer's own bold) and a thin, softer underline a little below the letters (a
 *   thick one ran through the stretched gaps of justified text).
 * - With justified text, English articles are hyphenated, so narrow lines (list items) don't get
 *   wide gaps. Only English: hyphenation needs the article's language, and the owner's feeds are
 *   English; other articles look as before ([looksEnglish]).
 */
object ReadingText {

    fun css(justified: Boolean): String = buildString {
        append(
            """
/* Brief You: links (see ReadingText) */
article a {
    font-weight: inherit !important;
    text-decoration-line: underline;
    text-decoration-thickness: 1.5px;
    text-underline-offset: 0.22em;
    text-decoration-color: color-mix(in srgb, var(--link-text-color) 45%, transparent);
}
"""
        )
        if (justified) {
            append(
                """
/* Brief You: English articles are hyphenated when justified (see ReadingText) */
html[lang="en"] article {
    -webkit-hyphens: auto;
    hyphens: auto;
}
"""
            )
        }
    }

    private val TAG = Regex("<[^>]*>")
    private val WORD = Regex("[A-Za-z']+")

    /** Words common in English and rare as words in other languages written in Latin letters. */
    private val ENGLISH = setOf("the", "and", "of", "to", "that", "with", "this", "for", "was", "are", "is", "it", "have", "from")

    /**
     * Whether an article is in English, from its first few thousand characters: at least one word
     * in eight is one of English's most common ones (running English text is near one in four;
     * other languages are near none). Short articles (under 30 words) aren't hyphenated anyway.
     */
    fun looksEnglish(html: String): Boolean {
        val text = TAG.replace(html.take(12_000), " ")
        var words = 0
        var common = 0
        for (m in WORD.findAll(text)) {
            words++
            if (m.value.lowercase() in ENGLISH) common++
            if (words >= 400) break
        }
        return words >= 30 && common * 8 >= words
    }
}
