package me.ash.reader.brief.webview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/**
 * One web view made ahead of time, so opening an article doesn't stop to make one.
 *
 * Making a web view takes the main thread several milliseconds (much longer the first time, while
 * Android loads the browser engine), and it used to happen in the first frame of the article's
 * opening animation, which then stuttered. Now a spare one is made a moment after the app starts
 * and after each article opens, at a quiet moment, and the next article takes it. Closing an
 * article frees its web view a moment later, after the closing animation, for the same reason.
 */
object WebViewPool {

    /** How long to wait before making a spare: after the start or the opening animation. */
    private const val PREPARE_AFTER_MS = 1_500L

    /** How long to wait before freeing a closed article's web view: after the animation. */
    private const val DESTROY_AFTER_MS = 800L

    private val main = Handler(Looper.getMainLooper())
    private var spare: WebView? = null
    private var preparing = false
    private var warmed = false

    /**
     * A tiny page in the reading fonts, loaded once into the first spare: the browser engine's page
     * process starts and the fonts are read then, at a quiet moment, instead of during the first
     * article (whose text took about 150 ms longer to appear than later ones).
     */
    private const val WARM_PAGE =
        "<!DOCTYPE html><html><head><style>" +
            "@font-face{font-family:a;src:url('file:///android_res/font/literata.ttf')}" +
            "@font-face{font-family:b;src:url('file:///android_res/font/google_sans_flex.ttf')}" +
            "p{color:transparent}</style></head><body>" +
            "<p style='font-family:a'>Brief You</p><p style='font-family:b'>Brief You</p>" +
            "</body></html>"

    /** A web view for [context]: the spare one when it was made for the same screen, else new. */
    fun take(context: Context): WebView {
        val ready = spare
        spare = null
        if (ready != null && ready.context === context) {
            me.ash.reader.brief.perf.PerfLog.note("web view: the spare one") // Nightly stutter log
            return ready
        }
        ready?.destroy()
        val started = android.os.SystemClock.uptimeMillis()
        return WebView(context).also {
            me.ash.reader.brief.perf.PerfLog.note("web view: made new in ${android.os.SystemClock.uptimeMillis() - started} ms (no spare)") // Nightly stutter log
        }
    }

    /** Makes a spare web view for [context] soon, when the app has nothing else to do. */
    fun prepare(context: Context) {
        if (spare != null || preparing) return
        preparing = true
        main.postDelayed(
            {
                Looper.myQueue().addIdleHandler {
                    preparing = false
                    val gone = (context as? android.app.Activity)?.isDestroyed == true
                    if (spare == null && !gone) spare = runCatching { WebView(context) }.getOrNull()
                    if (!warmed) {
                        spare?.let {
                            warmed = true
                            it.loadDataWithBaseURL(null, WARM_PAGE, "text/html", "UTF-8", null)
                            me.ash.reader.brief.perf.PerfLog.note("web view: warm-up page loaded in the spare") // Nightly stutter log
                        }
                    }
                    false
                }
            },
            PREPARE_AFTER_MS,
        )
    }

    /** Frees [webView] once the closing animation is over. */
    fun release(webView: WebView) {
        webView.stopLoading()
        main.postDelayed({ webView.destroy() }, DESTROY_AFTER_MS)
    }

    /** Frees the spare (the screen it was made for is closing). */
    fun clear() {
        spare?.destroy()
        spare = null
    }
}
