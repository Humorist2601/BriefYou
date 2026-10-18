package me.ash.reader.ui.component.webview

import androidx.compose.runtime.SideEffect
import me.ash.reader.ui.theme.palette.LocalWebViewColorScheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.compositeOver
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingImageHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalReadingSubheadBold
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.LocalReadingTextAlign
import me.ash.reader.infrastructure.preference.LocalReadingTextBold
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingTextLetterSpacing
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.surfaceColorAtElevation

@Composable
fun RYWebView(
    modifier: Modifier = Modifier,
    content: String,
    /** The article's address, which relative image and link addresses are read against. */
    baseUrl: String? = null,
    refererDomain: String? = null,
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    /**
     * Follow along: the number of the sentence being read aloud (from [pageText]'s list; null
     * when not reading), highlighted on the page. A State, so a new sentence doesn't reload.
     */
    readAloudPart: State<Int?>? = null,
    /** Gives the reading page the article's sentences, for read aloud. */
    pageText: ArticlePageText? = null,
    /** Where the highlighted sentence's top is in the web view, in pixels. */
    onReadAloudPosition: ((topPx: Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp.value
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val tonalElevation = LocalReadingPageTonalElevation.current
    // On the reading page: the picture's colours straight away, not the fading ones.
    val colorScheme = LocalWebViewColorScheme.current ?: MaterialTheme.colorScheme
    val backgroundColor =
        colorScheme.surfaceColorAtElevation(tonalElevation.value.dp).toArgb()
    // Selected text: the accent colour, see-through over the page, like text selection in the
    // rest of the app (the handles take the accent colour from the theme, see themes.xml).
    val selectionTextColor = colorScheme.onSurface.toArgb()
    val isDarkBackground = colorScheme.surface.luminance() < 0.5f
    val readAloudColor =
        colorScheme.primary
            .copy(alpha = 0.22f)
            .compositeOver(Color(backgroundColor))
            .toArgb()
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
    val codeTextColor: Int = colorScheme.tertiary.toArgb()
    val codeBgColor: Int =
        colorScheme.surfaceColorAtElevation((tonalElevation.value + 6).dp).toArgb()
    val boldCharacters = LocalReadingBoldCharacters.current

    val webView by
        remember(backgroundColor) {
            mutableStateOf(
                WebViewLayout.get(
                    context = context,
                    readingFontsPreference = readingFonts,
                    webViewClient =
                        WebViewClient(
                            context = context,
                            refererDomain = refererDomain,
                            pageUrl = baseUrl,
                            onOpenLink = { url ->
                                context.openURL(url, openLink, openLinkSpecificBrowser)
                            },
                        ),
                    onImageClick = onImageClick,
                )
            )
        }

    val fontPath =
        if (readingFonts is ReadingFontsPreference.External)
            ExternalFonts.FontType.ReadingFont.toPath(context)
        else if (readingFonts is ReadingFontsPreference.GoogleSans) {
            "/android_res/font/google_sans_flex.ttf"
        } else if (readingFonts is ReadingFontsPreference.Literata) {
            "/android_res/font/literata.ttf"
        } else null

    // Read aloud takes the sentences from this page; follow along highlights each new one and
    // reports where it is (the page is loaded once; only this runs as reading moves on).
    SideEffect { pageText?.webView = webView }
    val latestOnPosition by rememberUpdatedState(onReadAloudPosition)
    val density = LocalDensity.current.density
    if (readAloudPart != null) {
        LaunchedEffect(webView, readAloudPart) {
            snapshotFlow { readAloudPart.value }
                .collect { part ->
                    webView.evaluateJavascript(
                        "(typeof briefShow === 'function') ? briefShow(${part ?: -1}) : -1"
                    ) { result ->
                        val top = result?.toFloatOrNull() ?: -1f
                        if (part != null && top >= 0f) latestOnPosition?.invoke(top * density)
                    }
                }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        update = {
            it.apply {
                Log.i("RLog", "maxWidth: ${maxWidth}")
                Log.i("RLog", "readingFont: ${context.filesDir.absolutePath}")
                Log.i("RLog", "CustomWebView: ${content}")
                settings.defaultFontSize = fontSize
                loadDataWithBaseURL(
                    null,
                    WebViewHtml.HTML.format(
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
                            readAloudColor = readAloudColor,
                        ),
                        // The article's address (this used to read the web view's own, empty one,
                        // so relative images and links didn't work).
                        baseUrl.orEmpty().replace("\"", "%22"),
                        content,
                        WebViewScript.get(boldCharacters.value),
                    ),
                    "text/HTML",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}
