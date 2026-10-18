package me.ash.reader.ui.component.webview

import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Brief You: the reading page's way to the article as shown in its web view, for read aloud. The
 * sentences come from the page itself (see briefSentences in WebViewScript), so the page can
 * follow along exactly.
 */
class ArticlePageText {
    internal var webView: WebView? = null

    /** The article's sentences in page order, or null when the page can't give them. */
    suspend fun sentences(): List<String>? =
        withContext(Dispatchers.Main) {
            val view = webView ?: return@withContext null
            suspendCancellableCoroutine { cont ->
                view.evaluateJavascript(
                    "(typeof briefSentences === 'function') ? briefSentences() : null"
                ) { result ->
                    val list =
                        runCatching {
                                if (result == null || result == "null") null
                                else
                                    JSONArray(result).let { array ->
                                        List(array.length()) { array.getString(it) }
                                    }
                            }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                    if (cont.isActive) cont.resume(list)
                }
            }
        }
}
