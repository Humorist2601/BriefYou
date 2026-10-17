package me.ash.reader.ui.component.webview

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
        onSelectionChanged: ((String) -> Unit)? = null,
    ) =
        WebView(context).apply {
            this.webViewClient = webViewClient
            scrollBarSize = 0
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
            // Brief You: long press on a link shows where it goes before you open it.
            setOnLongClickListener {
                val hit = hitTestResult
                val url = hit.extra
                if (
                    (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                        hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && url != null
                ) {
                    val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
                    android.widget.Toast.makeText(
                            context,
                            host?.removePrefix("www.") ?: url,
                            android.widget.Toast.LENGTH_SHORT,
                        )
                        .show()
                    true
                } else {
                    false
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
                addJavascriptInterface(
                    object : JavaScriptInterface {
                        @JavascriptInterface
                        override fun onImgTagClick(imgUrl: String?, alt: String?) {
                            if (onImageClick != null && imgUrl != null) {
                                onImageClick.invoke(imgUrl, alt ?: "")
                            }
                        }

                        @JavascriptInterface
                        override fun onSelectionChanged(text: String?) {
                            val selected = text.orEmpty().trim()
                            post { onSelectionChanged?.invoke(selected) }
                        }
                    },
                    JavaScriptInterface.NAME,
                )
                setSupportZoom(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = true
                }
            }
        }
}
