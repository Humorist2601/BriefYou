package me.ash.reader.brief.webview

import me.ash.reader.ui.component.webview.JavaScriptInterface // Brief You


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebView
import me.ash.reader.infrastructure.preference.ReadingFontsPreference

object WebViewLayout {

    @SuppressLint("SetJavaScriptEnabled")
    fun get(
        context: Context,
        readingFontsPreference: ReadingFontsPreference,
        webViewClient: WebViewClient,
        onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    ) =
        // The spare one made ahead of time when there is one (see WebViewPool).
        WebViewPool.take(context).apply {
            this.webViewClient = webViewClient
            scrollBarSize = 0
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
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
                // Nightly stutter log: the page reports when its fonts load and when it first draws.
                if (me.ash.reader.brief.perf.PerfLog.enabled) {
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun note(message: String) = me.ash.reader.brief.perf.PerfLog.note(message)
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
