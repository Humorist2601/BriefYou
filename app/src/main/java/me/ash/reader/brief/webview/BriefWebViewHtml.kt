package me.ash.reader.brief.webview

import android.util.Base64
import java.security.SecureRandom

/**
 * The page the reading web view loads (the same as Read You's, see ui/component/webview/WebViewHtml).
 * What is added is a content policy: scripts inside an article (a feed's own `<script>` tags,
 * `onerror=` handlers, `javascript:` links) are blocked, while the app's own script in the page
 * carries a one-time key and runs. Scripts the app runs itself (picture taps, Translate swaps) go
 * in from outside the page and are not affected. Pictures, styles and embedded players are not
 * restricted.
 */
object BriefWebViewHtml {

    private val random = SecureRandom()

    /** A new key for each page load, so an article can't guess it. */
    private fun newKey(): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    fun page(css: String, baseUrl: String, content: String, script: String): String {
        val key = newKey()
        return """
<!DOCTYPE html>
<html dir="auto">
<head>
    <meta http-equiv="Content-Security-Policy" content="script-src 'nonce-$key'; object-src 'none'" />
    <meta name="viewport" content="initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, width=device-width, viewport-fit=cover" />
    <meta content="text/html; charset=utf-8" http-equiv="content-type"/>
    <style type="text/css">
        $css
    </style>
    <base href="$baseUrl" />
</head>
<body>
<main>
    <article>
        $content
    </article>
</main>
<script nonce="$key">
$script
</script>
</body>
</html>
"""
    }
}
