package com.ay.briefyou.brief.webview

import com.ay.briefyou.ui.component.webview.WebViewScript // Brief You
import com.ay.briefyou.ui.component.webview.WebViewStyle // Brief You

import com.ay.briefyou.brief.ui.LocalWebViewColorScheme
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.key
import org.json.JSONObject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ay.briefyou.infrastructure.preference.LocalReadingBoldCharacters
import com.ay.briefyou.infrastructure.preference.LocalReadingFonts
import com.ay.briefyou.infrastructure.preference.LocalReadingImageHorizontalPadding
import com.ay.briefyou.infrastructure.preference.LocalReadingImageMaximize
import com.ay.briefyou.infrastructure.preference.LocalReadingImageRoundedCorners
import com.ay.briefyou.infrastructure.preference.LocalReadingSubheadAlign
import com.ay.briefyou.infrastructure.preference.LocalReadingVideoHorizontalPadding
import com.ay.briefyou.infrastructure.preference.LocalReadingVideoMaximize
import com.ay.briefyou.infrastructure.preference.LocalReadingVideoRoundedCorners
import com.ay.briefyou.infrastructure.preference.LocalReadingSubheadBold
import com.ay.briefyou.infrastructure.preference.LocalReadingSubheadUpperCase
import com.ay.briefyou.infrastructure.preference.LocalReadingTextAlign
import com.ay.briefyou.infrastructure.preference.LocalReadingTextBold
import com.ay.briefyou.infrastructure.preference.LocalReadingTextFontSize
import com.ay.briefyou.infrastructure.preference.LocalReadingTextHorizontalPadding
import com.ay.briefyou.infrastructure.preference.LocalReadingTextLetterSpacing
import com.ay.briefyou.infrastructure.preference.LocalReadingTextLineHeight
import com.ay.briefyou.infrastructure.preference.ReadingFontsPreference
import com.ay.briefyou.ui.ext.ExternalFonts
import com.ay.briefyou.ui.ext.surfaceColorAtElevation

@Composable
fun BYWebView(
    modifier: Modifier = Modifier,
    content: String,
    /** The article's address, which relative image and link addresses are read against. */
    baseUrl: String? = null,
    refererDomain: String? = null,
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    /** Whether the reading page is scrolling (the pictures' loading shapes hold still meanwhile). */
    scrolling: () -> Boolean = { false },
) {
    val context = LocalContext.current
    // Opens links the way chosen in the settings ("open links in").
    val uriHandler = LocalUriHandler.current
    // On the reading page: the picture's colours straight away, not the fading ones.
    val colorScheme = LocalWebViewColorScheme.current ?: MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface.toArgb()
    // Selected text: the accent colour, see-through over the page, like text selection in the
    // rest of the app (the handles take the accent colour from the theme, see themes.xml).
    val selectionTextColor = colorScheme.onSurface.toArgb()
    val isDarkBackground = colorScheme.surface.luminance() < 0.5f
    val selectionBgColor =
        colorScheme.primary
            .copy(alpha = 0.35f)
            .compositeOver(Color(backgroundColor))
            .toArgb()
    val textColor: Int = colorScheme.onSurfaceVariant.toArgb()
    val textBold: Boolean = LocalReadingTextBold.current.value
    val textAlign: String = LocalReadingTextAlign.current.toTextAlignCSS()
    val textMargin: Int = LocalReadingTextHorizontalPadding.current
    val boldTextColor: Int = colorScheme.onSurface.toArgb()
    val linkTextColor: Int = colorScheme.primary.toArgb()
    val subheadBold: Boolean = LocalReadingSubheadBold.current.value
    val subheadUpperCase: Boolean = LocalReadingSubheadUpperCase.current.value
    val readingFonts = LocalReadingFonts.current
    val fontSize: Int = LocalReadingTextFontSize.current
    val letterSpacing: Float = LocalReadingTextLetterSpacing.current
    val lineHeight: Float = LocalReadingTextLineHeight.current
    val imgMargin: Int = LocalReadingImageHorizontalPadding.current
    val imgBorderRadius: Int = LocalReadingImageRoundedCorners.current
    val imgMaximize: Boolean = LocalReadingImageMaximize.current.value
    val subheadAlign: String = LocalReadingSubheadAlign.current.toTextAlignCSS()
    val videoCorners: Int = LocalReadingVideoRoundedCorners.current
    val videoPadding: Int = LocalReadingVideoHorizontalPadding.current
    val videoMaximize: Boolean = LocalReadingVideoMaximize.current
    val codeTextColor: Int = colorScheme.tertiary.toArgb()
    val codeBgColor: Int =
        colorScheme.surfaceColorAtElevation(6.dp).toArgb()
    val boldCharacters = LocalReadingBoldCharacters.current

    val fontPath =
        if (readingFonts is ReadingFontsPreference.External)
            ExternalFonts.FontType.ReadingFont.toPath(context)
        else if (readingFonts is ReadingFontsPreference.GoogleSans) {
            "/android_res/font/google_sans_flex.ttf"
        } else if (readingFonts is ReadingFontsPreference.Literata) {
            "/android_res/font/literata.ttf"
        } else null

    // Made again only when a setting or colour changes, not on every redraw while the article
    // opens (the styles are long, and the pictures' loading indicator is drawn shape by shape).
    val css =
        remember(
            fontSize, fontPath, lineHeight, letterSpacing, textMargin, textColor, textBold,
            textAlign, boldTextColor, subheadBold, subheadUpperCase, imgMargin, imgBorderRadius,
            linkTextColor, codeTextColor, codeBgColor, selectionTextColor, selectionBgColor,
            isDarkBackground, subheadAlign, imgMaximize, videoCorners, videoPadding, videoMaximize,
        ) {
            WebViewStyle.get(
                fontSize = fontSize,
                fontPath = fontPath,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textMargin = textMargin,
                textColor = textColor,
                textBold = textBold,
                textAlign = textAlign,
                boldTextColor = boldTextColor,
                subheadBold = subheadBold,
                subheadUpperCase = subheadUpperCase,
                imgMargin = imgMargin,
                imgBorderRadius = imgBorderRadius,
                linkTextColor = linkTextColor,
                codeTextColor = codeTextColor,
                codeBgColor = codeBgColor,
                tableMargin = textMargin,
                selectionTextColor = selectionTextColor,
                selectionBgColor = selectionBgColor,
                isDark = isDarkBackground,
                subheadAlign = subheadAlign,
            ) +
                MediaStyle.css(imgMaximize, videoCorners, videoPadding, videoMaximize) +
                // Boxes with a loading indicator for the pictures still loading (no jumping text).
                // (the same tonal colour as code blocks, and the accent colour).
                ImagePlaceholders.css(boxColor = codeBgColor, indicatorColor = linkTextColor) +
                // Softer links; hyphenated justified English text.
                ReadingText.css(justified = textAlign == "justify")
        }
    val script =
        remember(boldCharacters.value) {
            WebViewScript.get(boldCharacters.value) + YouTubeCards.SCRIPT + ImagePlaceholders.SCRIPT + PAGE_READY_SCRIPT +
                (if (com.ay.briefyou.brief.perf.PerfLog.enabled) PERF_SCRIPT else "")
        }

    // Brief You: one web view per article (and reading font), freed when it leaves the screen.
    // When only the styling changes (the picture's colours arriving, the text sheet), the new
    // styles are swapped in place: no reload, no blink, and the scroll position stays. Before,
    // every colour change made a new web view that was never shown or freed, and reloaded the page.
    key(baseUrl, readingFonts) {
        // The article's text fades in once the page says its fonts are loaded and its text drawn
        // (it popped in part way through the page's slide; and on pages with many pictures the
        // web view kept it invisible for a second or two, waiting for the reading font).
        var drawn by remember { androidx.compose.runtime.mutableStateOf(false) }
        val textAlpha by
            androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (drawn) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(180),
                label = "article text",
            )
        // In case the page never reports it (an error): shown anyway.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(700)
            if (!drawn) com.ay.briefyou.brief.perf.PerfLog.note("text shown after 0.7 s without the page saying it was drawn") // Nightly stutter log
            drawn = true
        }
        // What the web view currently shows: its content, styles and script (kept apart, so a
        // redraw doesn't join the whole article into a new string just to compare it).
        val screenSettled = com.ay.briefyou.brief.webview.LocalScreenSettled.current
        val shown = remember { arrayOfNulls<String>(3) }
        // The styles wanted now, and whether the page loaded last is ready for them to be swapped in.
        val wantedCss = remember { arrayOfNulls<String>(1) }
        val pageIsReady = remember { booleanArrayOf(false) }
        val pageReady = remember { arrayOfNulls<() -> Unit>(1) }
        val webView = remember {
            WebViewLayout.get(
                context = context,
                readingFontsPreference = readingFonts,
                webViewClient =
                    WebViewClient(
                        context = context,
                        refererDomain = refererDomain,
                        pageUrl = baseUrl,
                        onOpenLink = { url ->
                            // A YouTube video (its card, or a link) opens in the YouTube app.
                            if (!(YouTubeCards.isVideoLink(url) && YouTubeCards.open(context, url))) uriHandler.openUri(url)
                        },
                    ),
                onImageClick = onImageClick,
                onPageReady = { pageReady[0]?.invoke() },
            )
        }
        pageReady[0] = {
            if (!drawn) com.ay.briefyou.brief.perf.PerfLog.note("text fades in (fonts loaded, text drawn)") // Nightly stutter log
            drawn = true
            pageIsReady[0] = true
            // Styles that changed while the page was loading (the picture's colour arriving late).
            val wanted = wantedCss[0]
            if (wanted != null && shown[1] != wanted) {
                com.ay.briefyou.brief.perf.PerfLog.note("page styles swapped in once ready") // Nightly stutter log
                webView.evaluateJavascript("document.querySelector('style').textContent = ${JSONObject.quote(wanted)};", null)
                shown[1] = wanted
            }
        }
        // The pictures' loading shapes hold still while the page scrolls, and move again shortly
        // after it stops (see ImagePlaceholders).
        LaunchedEffect(webView) {
            androidx.compose.runtime.snapshotFlow { scrolling() }
                .distinctUntilChanged()
                .collectLatest { now ->
                    if (!now) kotlinx.coroutines.delay(150)
                    // Only while a picture is still loading (otherwise there's nothing to hold still).
                    webView.evaluateJavascript(
                        "(function(){var r=document.documentElement;" +
                            "if($now&&!document.querySelector('.brief-box:not(.brief-loaded)'))return;" +
                            "r.classList.toggle('brief-scrolling',$now);})();",
                        null,
                    )
                }
        }
        // Have the next article's web view ready (made at a quiet moment, see WebViewPool).
        LaunchedEffect(webView) { WebViewPool.prepare(context) }
        // Translation: paragraphs are swapped into the page in place (no reload, so no flicker).
        val translation = com.ay.briefyou.brief.ai.LocalTranslation.current
        val appliedPatches = remember { intArrayOf(0) }
        val patchCount = translation?.patches?.size ?: 0
        androidx.compose.runtime.LaunchedEffect(webView, patchCount, content) {
            if (translation == null) return@LaunchedEffect
            while (appliedPatches[0] < translation.patches.size) {
                kotlinx.coroutines.delay(80) // also lets a page that was just loaded replace the old one
                val ready =
                    kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { c ->
                        webView.evaluateJavascript("document.readyState") {
                            c.resumeWith(Result.success(it == "\"complete\"" || it == "\"interactive\""))
                        }
                    }
                if (!ready) continue
                val upto = translation.patches.size
                for (i in appliedPatches[0] until upto) {
                    val (block, html) = translation.patches[i]
                    webView.evaluateJavascript(
                        if (block < 0) "document.querySelectorAll('[data-bt]').forEach(function(e){e.style.opacity='';});"
                        else "(function(){var e=document.querySelector('[data-bt=\"$block\"]');if(e){e.innerHTML=${JSONObject.quote(html)};e.style.opacity='';}})();",
                        null,
                    )
                }
                appliedPatches[0] = upto
            }
        }

        // The text appears by a cover in the page's own colour fading out over it, not by fading the
        // web view: a see-through web view is drawn into an extra layer every frame, which on long
        // pages with pictures made 90-260 ms frames.
        val cover = MaterialTheme.colorScheme.surface
        androidx.compose.foundation.layout.Box(modifier = modifier, propagateMinConstraints = true) {
        AndroidView(
            // A page can report a height no layout can hold (one article made 706,221 px, and
            // Compose crashed: its limit is about 262,000 px at this width); it is cut at 200,000.
            modifier =
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height.coerceAtMost(MaxPageHeightPx)) { placeable.place(0, 0) }
                },
            factory = { webView },
            // Freed after the closing animation, so closing the article doesn't stutter.
            onRelease = { WebViewPool.release(it) },
            update = {
                it.settings.defaultFontSize = fontSize
                val pageChanged = shown[0] != content || shown[2] != script
                wantedCss[0] = css
                // A style change while the page is still loading is swapped in once it is ready
                // (it reloaded the whole page, which then started over).
                // Not while the screen is still sliding in (see LocalScreenSettled); this runs again
                // when it has settled.
                if (pageChanged && !screenSettled()) return@AndroidView
                if (pageChanged) {
                    pageIsReady[0] = false
                    com.ay.briefyou.brief.perf.PerfLog.note("page load starts (${content.length / 1000} k characters${if (shown[0] != null) ", a reload" else ""})") // Nightly stutter log
                    val handing = android.os.SystemClock.uptimeMillis()
                    it.loadDataWithBaseURL(
                        null,
                        BriefWebViewHtml.page(
                            css,
                            // The article's address (this used to read the web view's own, empty
                            // one, so relative images and links didn't work).
                            baseUrl.orEmpty().replace("\"", "%22"),
                            content,
                            script,
                            // Lets justified English text be hyphenated (see ReadingText).
                            english = ReadingText.looksEnglish(content),
                        ),
                        "text/HTML",
                        "UTF-8",
                        null,
                    )
                    com.ay.briefyou.brief.perf.PerfLog.note("page handed to the web view in ${android.os.SystemClock.uptimeMillis() - handing} ms") // Nightly stutter log
                    shown[0] = content
                    shown[1] = css
                    shown[2] = script
                    // The page starts from what the translation had made when this content was set.
                    appliedPatches[0] = translation?.shownPatchCount ?: 0
                } else if (shown[1] != css && pageIsReady[0]) {
                    com.ay.briefyou.brief.perf.PerfLog.note("page styles swapped in place (colours or text settings changed)") // Nightly stutter log
                    it.evaluateJavascript(
                        "document.querySelector('style').textContent = ${JSONObject.quote(css)};",
                        null,
                    )
                    shown[1] = css
                }
            },
        )
        if (textAlpha < 1f) {
            androidx.compose.foundation.layout.Spacer(
                Modifier.matchParentSize().drawBehind { drawRect(cover, alpha = 1f - textAlpha) }
            )
        }
        }
    }
}

/** The tallest an article page is laid out (pixels); see the web view's layout. */
private const val MaxPageHeightPx = 200_000

/** Nightly stutter log: when the page's fonts start and finish loading, and its first frame. */
private const val PERF_SCRIPT = """
;(function () {
  if (!window.BriefPerf) return;
  function n(m) { try { BriefPerf.note('page: ' + m + ' at ' + Math.round(performance.now()) + ' ms'); } catch (e) {} }
  function names(e) { return (e.fontfaces || []).map(function (f) { return f.family + ' ' + f.style + ' ' + f.status; }).join(', '); }
  try {
    document.fonts.addEventListener('loading', function (e) { n('font loading (' + names(e) + ')'); });
    document.fonts.addEventListener('loadingdone', function (e) { n('font loaded (' + names(e) + ')'); });
    document.fonts.addEventListener('loadingerror', function (e) { n('font FAILED (' + names(e) + ')'); });
    document.fonts.ready.then(function () { n('fonts ready (' + document.fonts.status + ')'); });
  } catch (e) { n('no font events: ' + e); }
  requestAnimationFrame(function () { n('first frame'); });
})();
"""
