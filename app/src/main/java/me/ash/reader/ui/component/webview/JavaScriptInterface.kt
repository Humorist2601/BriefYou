package me.ash.reader.ui.component.webview

import android.webkit.JavascriptInterface

interface JavaScriptInterface {

    @JavascriptInterface
    fun onImgTagClick(imgUrl: String?, alt: String?)

    /** The text selected in the article ("" when nothing is selected). */
    @JavascriptInterface
    fun onSelectionChanged(text: String?)

    companion object {

        const val NAME = "JavaScriptInterface"
    }
}
