package com.ay.briefyou.brief.webview

import com.ay.briefyou.ui.component.webview.JavaScriptInterface // Brief You

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
import com.ay.briefyou.ui.ext.isUrl
import java.net.HttpURLConnection
import org.json.JSONObject
import java.net.URI

const val INJECTION_TOKEN = "/android_asset_font/"

class WebViewClient(
    private val context: Context,
    private val refererDomain: String?,
    /** The article's own address, sent as the referrer when fetching its pictures. */
    private val pageUrl: String? = null,
    private val onOpenLink: (url: String) -> Unit,
    /** The page's first frame with its content is on screen (see [onPageCommitVisible]). */
    private val onFirstVisible: () -> Unit = {},
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null && com.ay.briefyou.brief.perf.PerfLog.enabled && FONT_FILE.containsMatchIn(url)) {
            com.ay.briefyou.brief.perf.PerfLog.note("reading font asked for") // Nightly stutter log
        }
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
        } else if (url != null && request.method == "GET" && stored(url) != null) {
            // A starred article's picture, kept on the phone: shown without the internet.
            val (file, type) = stored(url)!!
            return WebResourceResponse(type, null, 200, "OK", emptyMap(), file.inputStream())
        } else if (url != null && url.isUrl() && request.method == "GET" && isImage(request) &&
            // Plain http pictures too: the web view may block them as insecure.
            (request.url.scheme == "http" || PictureHosts.needsApp(context, request.url.host))
        ) {
            // Pictures load the web view's own way (in parallel, like any page). Only from sites
            // that refused that once (see onReceivedHttpError) are they fetched here, like a
            // browser would (no app header, the article as the referrer, then without one); some
            // image hosts and proxies (Kagi News, for one) refuse the web view's own request.
            // Fetching every picture here held up the page's other loading, the reading font
            // among it: on a page full of pictures the text stayed invisible for a second or two.
            // Here too the answer is given at once and the picture fetched in the background.
            return streamImage(url)
        }
        // Brief You: other requests (scripts, styles, embeds) are left to the web view. They were
        // fetched here first just to look at the answer, then fetched again by the web view.
        return super.shouldInterceptRequest(view, request)
    }

    // Brief You: looked up once per request (it was looked up twice).
    private fun stored(url: String) = com.ay.briefyou.brief.offline.OfflineCopies.imageFile(context, url)

    private fun isImage(request: WebResourceRequest): Boolean {
        val accept = request.requestHeaders["Accept"] ?: request.requestHeaders["accept"]
        if (accept != null && accept.startsWith("image/")) return true
        val path = request.url.path?.lowercase() ?: return false
        return IMAGE_EXTENSIONS.any { path.endsWith(it) }
    }

    /** A picture answered at once; its data follows when [fetchImage] has it (nothing if it fails). */
    private fun streamImage(url: String): WebResourceResponse {
        val asked = android.os.SystemClock.uptimeMillis()
        val fetching =
            PICTURES.submit<java.io.InputStream?> {
                fetchImage(url)?.data.also {
                    com.ay.briefyou.brief.perf.PerfLog.note(
                        "picture ${if (it != null) "arrived" else "failed"} after ${android.os.SystemClock.uptimeMillis() - asked} ms"
                    ) // Nightly stutter log
                }
            }
        val type =
            java.net.URLConnection.guessContentTypeFromName(url.substringBefore('?').substringBefore('#'))
                ?.takeIf { it.startsWith("image/") } ?: "image/*"
        return WebResourceResponse(type, null, 200, "OK", emptyMap(), LateStream(fetching))
    }

    /** Reads from the picture's own stream once it is there; empty when the fetch failed. */
    private class LateStream(private val fetching: java.util.concurrent.Future<java.io.InputStream?>) :
        java.io.InputStream() {
        private var source: java.io.InputStream? = null
        private var opened = false

        private fun source(): java.io.InputStream? {
            if (!opened) {
                opened = true
                source = runCatching { fetching.get(40, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull()
            }
            return source
        }

        override fun read(): Int = source()?.read() ?: -1

        override fun read(b: ByteArray, off: Int, len: Int): Int = source()?.read(b, off, len) ?: -1

        override fun close() {
            if (!opened) fetching.cancel(true)
            runCatching { source?.close() }
        }
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
        view?.evaluateJavascript(OnImgClickScript, null)
        if (com.ay.briefyou.brief.perf.PerfLog.enabled && view != null) {
            // Nightly stutter log: how tall the article is, in screens.
            val px = view.contentHeight * view.resources.displayMetrics.density
            val screen = view.resources.displayMetrics.heightPixels.coerceAtLeast(1)
            com.ay.briefyou.brief.perf.PerfLog.note(
                "page finished: ${"%.1f".format(px / screen)} screens tall, web view ${view.height} px high"
            )
        }
    }

    // A picture the web view couldn't load itself: from then on that site's pictures are fetched
    // by the app (see shouldInterceptRequest), starting with this one again.
    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // Refused (not missing: a 404 says nothing about how the site treats the web view).
        val code = errorResponse?.statusCode ?: return
        if (code == 401 || code == 403) retryPicture(view, request)
    }

    private fun retryPicture(view: WebView?, request: WebResourceRequest?) {
        if (view == null || request == null || request.isForMainFrame || !isImage(request)) return
        val host = request.url.host ?: return
        if (PictureHosts.needsApp(context, host)) return
        PictureHosts.add(context, host)
        com.ay.briefyou.brief.perf.PerfLog.note("pictures from $host refused the web view: fetched by the app from now on") // Nightly stutter log
        val url = JSONObject.quote(request.url.toString())
        view.evaluateJavascript(
            "(function(u){document.querySelectorAll('img').forEach(function(i){if(i.currentSrc===u||i.src===u){i.removeAttribute('srcset');i.src='';i.src=u;}});})($url);",
            null,
        )
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        onFirstVisible()
        com.ay.briefyou.brief.perf.PerfLog.note("page first visible") // Nightly stutter log
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
        Log.e("RLog", "BYWebView onReceivedError: $error")
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }

    companion object {
        /** Pictures fetched in the background, a few at a time (as a browser does per site). */
        private val PICTURES =
            java.util.concurrent.Executors.newFixedThreadPool(6) { r ->
                Thread(r, "brief-pictures").apply { isDaemon = true }
            }
        private val FONT_FILE = Regex("\\.(ttf|otf|woff2?)(\\?|$)", RegexOption.IGNORE_CASE)

        private const val OnImgClickScript = """
            javascript:(function() {
                var imgs = document.getElementsByTagName("img");
                for(var i = 0; i < imgs.length; i++){
                    if (imgs[i].closest(".brief-yt")) continue; // a YouTube card opens the video
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


/** Sites whose pictures the web view couldn't load itself, remembered (see shouldInterceptRequest). */
private object PictureHosts {
    @Volatile private var hosts: MutableSet<String>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("brief_picture_hosts", Context.MODE_PRIVATE)

    private fun all(context: Context): MutableSet<String> =
        hosts ?: synchronized(this) {
            hosts ?: java.util.concurrent.ConcurrentHashMap.newKeySet<String>().also {
                it.addAll(prefs(context).getStringSet("hosts", emptySet()).orEmpty())
                hosts = it
            }
        }

    fun needsApp(context: Context, host: String?): Boolean = host != null && host in all(context)

    fun add(context: Context, host: String) {
        val set = all(context)
        if (set.add(host)) prefs(context).edit().putStringSet("hosts", HashSet(set)).apply()
    }
}
