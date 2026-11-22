package com.ay.briefyou.brief.webview

import com.ay.briefyou.ui.component.webview.JavaScriptInterface // Brief You


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.ay.briefyou.infrastructure.preference.ReadingFontsPreference

object WebViewLayout {

    @SuppressLint("SetJavaScriptEnabled")
    fun get(
        context: Context,
        readingFontsPreference: ReadingFontsPreference,
        webViewClient: WebViewClient,
        onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
        /** The page's fonts are loaded and its text drawn (see [PAGE_READY_SCRIPT]); on the main thread. */
        onPageReady: () -> Unit = {},
    ) =
        // The spare one made ahead of time when there is one (see WebViewPool).
        WebViewPool.take(context).apply {
            this.webViewClient = webViewClient
            scrollBarSize = 0
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            // The page scrolls as a whole (the web view is as tall as the article, inside the
            // reading page's scroll). While the article grows (pictures, the font), the web view
            // can be shorter than its text for a moment, and a quick fling then scrolled the text
            // inside the web view, sliding it under the heading. It stays at the top; the height
            // is asked for again so the rest of the text is reached through the page's scroll.
            setOnScrollChangeListener { view, _, y, _, _ ->
                if (y != 0) {
                    com.ay.briefyou.brief.perf.PerfLog.note("web view: scrolled inside by $y px, put back") // Nightly stutter log
                    view.scrollTo(view.scrollX, 0)
                    view.requestLayout()
                }
            }
            with(this.settings) {
                standardFontFamily =
                    when (readingFontsPreference) {
                        ReadingFontsPreference.Literata -> "serif"
                        ReadingFontsPreference.GoogleSans -> {
                            "sans-serif"
                        }
                        ReadingFontsPreference.External -> {
                            allowFileAccess = true
                            "sans-serif"
                        }

                        else -> "sans-serif"
                    }
                domStorageEnabled = true
                javaScriptEnabled = true
                // Pictures load the web view's own way now (see WebViewClient): it asks as Chrome
                // does, without the "web view" mark some picture hosts refuse.
                browserUserAgent(context)?.let { userAgentString = it }
                addJavascriptInterface(
                    object : JavaScriptInterface {
                        @JavascriptInterface
                        override fun onImgTagClick(imgUrl: String?, alt: String?) {
                            if (onImageClick != null && imgUrl != null) {
                                onImageClick.invoke(imgUrl, alt ?: "")
                            }
                        }
                    },
                    JavaScriptInterface.NAME,
                )
                // The page says when its text can be shown: fonts loaded and drawn.
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun ready() {
                            android.os.Handler(android.os.Looper.getMainLooper()).post(onPageReady)
                        }
                    },
                    "BriefPage",
                )
                // Nightly stutter log: the page reports when its fonts load and when it first draws.
                if (com.ay.briefyou.brief.perf.PerfLog.enabled) {
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun note(message: String) = com.ay.briefyou.brief.perf.PerfLog.note(message)
                        },
                        "BriefPerf",
                    )
                }
                setSupportZoom(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = true
                }
            }
        }
}

/**
 * In every article page: tells the app once the page's fonts are loaded and the text drawn with
 * them. Until then the web view keeps text invisible (it waits for the reading font), so the app
 * fades the text in at that moment rather than at the page's first frame. Brief You.
 */
const val PAGE_READY_SCRIPT = """
;(function () {
  function done() { requestAnimationFrame(function () { requestAnimationFrame(function () { try { BriefPage.ready(); } catch (e) {} }); }); }
  try { document.fonts.ready.then(done, done); } catch (e) { done(); }
})();
"""

@Volatile private var browserAgent: String? = null

/** The web view's own user agent without its "; wv" mark (read once). */
private fun browserUserAgent(context: Context): String? =
    browserAgent ?: runCatching {
        android.webkit.WebSettings.getDefaultUserAgent(context).replace("; wv)", ")")
    }.getOrNull()?.also { browserAgent = it }
