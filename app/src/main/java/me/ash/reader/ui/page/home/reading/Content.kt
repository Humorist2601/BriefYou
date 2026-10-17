package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.size.Scale
import coil.size.Precision
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.rss.FeedContent
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import java.util.Date
import me.ash.reader.infrastructure.preference.LocalReadingRenderer
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.ReadingRendererPreference
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.Reader
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.webview.RYWebView
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.roundClick
import androidx.compose.runtime.remember
import me.ash.reader.ui.page.home.flow.readingMinutes

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
    listState: LazyListState,
    isLoading: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    /** Shown between the title and the article (the AI key points card). */
    beforeArticle: (@Composable () -> Unit)? = null,
    /** Shown after the article (the next article card). */
    afterArticle: (@Composable () -> Unit)? = null,
    /** The text selected in the article, as it changes ("" when nothing is selected). */
    onSelectionChanged: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val renderer = LocalReadingRenderer.current
    val textHorizontalPadding = LocalReadingTextHorizontalPadding.current

    val textContentWidth = LocalTextContentWidth.current
    val maxWidthModifier = Modifier.widthIn(max = textContentWidth)
    val uriHandler = LocalUriHandler.current

    // Actual length of what is being shown (full content when it has been fetched).
    val minutesToRead = remember(content) { readingMinutes(content) }
    // An article that starts with a picture shows it large above the title (the hero) instead.
    val (hero, body) = remember(content) { FeedContent.extractHero(content) }
    val imageCorners = LocalReadingImageRoundedCorners.current

    val headline =
        @Composable {
            // Lined up with the article text: the reading text padding setting, less the 12 dp
            // the title block has inside it.
            Column(
                modifier =
                    Modifier.then(maxWidthModifier)
                        .padding(horizontal = (textHorizontalPadding - 12).coerceAtLeast(0).dp)
            ) {
                hero?.let { heroUrl ->
                    // Slides a little slower than the text while scrolling (a gentle parallax).
                    RYAsyncImage(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 8.dp, bottom = 8.dp)
                                .heightIn(max = 280.dp)
                                .aspectRatio(16f / 10f)
                                .graphicsLayer {
                                    if (renderer == ReadingRendererPreference.WebView) {
                                        translationY = scrollState.value * 0.25f
                                    }
                                }
                                .clip(RoundedCornerShape(imageCorners.dp.coerceAtLeast(16.dp)))
                                .clickable { onImageClick?.invoke(heroUrl, title) },
                        data = heroUrl,
                        scale = Scale.FILL,
                        precision = Precision.INEXACT,
                        size = SIZE_1000,
                        contentScale = ContentScale.Crop,
                        contentDescription = title,
                    )
                }
                DisableSelection {
                    Metadata(
                        feedName = feedName,
                        title = title,
                        author = author,
                        publishedDate = publishedDate,
                        readingMinutes = minutesToRead,
                        modifier = Modifier.roundClick { link?.let { uriHandler.openUri(it) } },
                    )
                }
            }
        }

    if (isLoading) {
        Column { LoadingIndicator(modifier = Modifier.size(56.dp)) }
    } else {

        when (renderer) {
            ReadingRendererPreference.WebView -> {
                Column(
                    modifier =
                        modifier
                            .padding(top = contentPadding.calculateTopPadding())
                            .fillMaxSize()
                            .drawVerticalScrollIndicator(scrollState)
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
                            beforeArticle?.invoke()

                            RYWebView(
                                modifier = Modifier.fillMaxSize(),
                                content = body,
                                baseUrl = link,
                                refererDomain = link.extractDomain(),
                                onImageClick = onImageClick,
                                onSelectionChanged = onSelectionChanged,
                            )
                            afterArticle?.invoke()
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }

            ReadingRendererPreference.NativeComponent -> {
                SelectionContainer {
                    LazyColumn(
                        modifier = modifier.fillMaxSize().drawVerticalScrollIndicator(listState),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                            headline()
                            beforeArticle?.invoke()
                        }

                        Reader(
                            context = context,
                            subheadUpperCase = subheadUpperCase.value,
                            link = link ?: "",
                            content = body,
                            onImageClick = onImageClick,
                            onLinkClick = { uriHandler.openUri(it) },
                        )

                        afterArticle?.let { item { it() } }

                        item {
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }
        }
    }
}
