package me.ash.reader.brief.webview

import me.ash.reader.ui.component.webview.JavaScriptInterface // Brief You

import android.content.Context
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import me.ash.reader.ui.ext.isUrl
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URI

const val INJECTION_TOKEN = "/android_asset_font/"

class WebViewClient(
    private val context: Context,
    private val refererDomain: String?,
    /** The article's own address, sent as the referrer when fetching its pictures. */
    private val pageUrl: String? = null,
    private val onOpenLink: (url: String) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null && url.contains(INJECTION_TOKEN)) {
            try {
                val assetPath = url.substring(
                    url.indexOf(INJECTION_TOKEN) + INJECTION_TOKEN.length,
                    url.length
                )
                return WebResourceResponse(
                    "text/HTML",
                    "UTF-8",
                    context.assets.open(assetPath)
                )
            } catch (e: Exception) {
                Log.e("RLog", "WebView shouldInterceptRequest: $e")
            }
        } else if (url != null && url.isUrl() && request.method == "GET" && isImage(request)) {
            // Pictures are fetched here like a browser would (no app header, the article as the
            // referrer, then without one), since some image hosts and proxies (Kagi News, for
            // one) refuse the web view's own request. If both fail, the web view tries itself.
            fetchImage(url)?.let { return it }
        } else if (url != null && url.isUrl()) {
            try {
                var connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                if (connection.responseCode == 403) {
                    connection.disconnect()
                    connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                    connection.setRequestProperty("Referer", refererDomain)
                    val inputStream = DataInputStream(connection.inputStream)
                    return WebResourceResponse(connection.contentType, "UTF-8", inputStream)
                }
            } catch (e: Exception) {
                Log.e("RLog", "shouldInterceptRequest url: $e")
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun isImage(request: WebResourceRequest): Boolean {
        val accept = request.requestHeaders["Accept"] ?: request.requestHeaders["accept"]
        if (accept != null && accept.startsWith("image/")) return true
        val path = request.url.path?.lowercase() ?: return false
        return IMAGE_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun fetchImage(url: String): WebResourceResponse? {
        val userAgent =
            runCatching { WebSettings.getDefaultUserAgent(context).replace("; wv)", ")") }
                .getOrNull()
        for (referer in listOf(pageUrl, null).distinct()) {
            try {
                val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                userAgent?.let { connection.setRequestProperty("User-Agent", it) }
                connection.setRequestProperty(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                )
                referer?.let { connection.setRequestProperty("Referer", it) }
                val code = connection.responseCode
                if (code in 200..299) {
                    val type =
                        connection.contentType?.substringBefore(';')?.trim()?.ifBlank { null }
                            ?: "image/*"
                    return WebResourceResponse(
                        type,
                        null,
                        code,
                        connection.responseMessage?.ifBlank { null } ?: "OK",
                        // No cross-origin header: the article's scripts mustn't be able to read
                        // what's fetched here (pictures can still be shown without it).
                        emptyMap(),
                        connection.inputStream,
                    )
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("RLog", "fetchImage $url: $e")
            }
        }
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view!!.evaluateJavascript(OnImgClickScript, null)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (null == request?.url) return false
        val url = request.url.toString()
        if (url.isNotEmpty()) onOpenLink(url)
        return true
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        Log.e("RLog", "RYWebView onReceivedError: $error")
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }

    companion object {
        private const val OnImgClickScript = """
            javascript:(function() {
                var imgs = document.getElementsByTagName("img");
                for(var i = 0; i < imgs.length; i++){
                    imgs[i].pos = i;
                    imgs[i].onclick = function(event) {
                        event.preventDefault();
                        window.${JavaScriptInterface.NAME}.onImgTagClick(this.src, this.alt);
                    }
                }
            })()
            """
    }
}

private val IMAGE_EXTENSIONS =
    listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".svg", ".bmp", ".heic")

