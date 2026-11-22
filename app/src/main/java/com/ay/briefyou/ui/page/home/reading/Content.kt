package com.ay.briefyou.ui.page.home.reading

import androidx.compose.foundation.ScrollState
import com.ay.briefyou.brief.reading.extractReferer // Brief You
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.util.Date
import com.ay.briefyou.ui.component.reader.LocalTextContentWidth
// Brief You: its own web view, and the article's picture, length and alignment.
import com.ay.briefyou.brief.reading.BriefArticleHero
import com.ay.briefyou.brief.reading.briefHeadlinePadding
import com.ay.briefyou.brief.reading.rememberBriefArticle
import com.ay.briefyou.brief.webview.RYWebView
import com.ay.briefyou.ui.ext.extractDomain
import com.ay.briefyou.ui.ext.roundClick

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Content(
    modifier: Modifier = Modifier,
    content: String,
    feedName: String,
    title: String,
    author: String? = null,
    link: String? = null,
    publishedDate: Date,
    scrollState: ScrollState,
    isLoading: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    afterArticle: (@Composable () -> Unit)? = null, // Brief You: the next article card
    afterHeadline: (@Composable () -> Unit)? = null, // Brief You: the translation banner
) {

    val textContentWidth = LocalTextContentWidth.current
    val maxWidthModifier = Modifier.widthIn(max = textContentWidth)
    val uriHandler = LocalUriHandler.current
    val brief = rememberBriefArticle(content) // Brief You: picture on top, actual length

    val headline =
        @Composable {
            Column(modifier = Modifier.then(maxWidthModifier).briefHeadlinePadding()) { // Brief You
                brief.hero?.let { BriefArticleHero(it, title, onImageClick) } // Brief You
                DisableSelection {
                    Metadata(
                        feedName = feedName,
                        title = title,
                        author = author,
                        publishedDate = publishedDate,
                        readingMinutes = brief.minutes, // Brief You
                        modifier = Modifier.roundClick { link?.let { uriHandler.openUri(it) } },
                    )
                }
            }
        }

    if (isLoading) {
        Column { LoadingIndicator(modifier = Modifier.size(56.dp)) }
    } else {
        Column(
            modifier =
                modifier
                    .padding(top = contentPadding.calculateTopPadding())
                    .fillMaxSize() // Brief You: no scroll bar (progress is in the top bar)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.then(maxWidthModifier)) {
                    // Top bar height
                    Spacer(modifier = Modifier.height(64.dp))
                    // padding
                    headline()
                    afterHeadline?.invoke() // Brief You

                    RYWebView(
                        modifier = Modifier.fillMaxSize(),
                        content = brief.body, // Brief You
                        baseUrl = link, // Brief You
                        refererDomain = link.extractReferer(), // Brief You: a real address (#1347)
                        onImageClick = onImageClick,
                        scrolling = { scrollState.isScrollInProgress }, // Brief You: loading shapes hold still meanwhile
                    )
                    afterArticle?.invoke() // Brief You
                    Spacer(modifier = Modifier.height(128.dp))
                    Spacer(
                        modifier = Modifier.height(contentPadding.calculateBottomPadding())
                    )
                }
            }
        }
    }
}
