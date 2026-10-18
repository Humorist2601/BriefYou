package me.ash.reader.ui.page.home.reading

import me.ash.reader.ui.component.FloatingBarGap
import me.ash.reader.R
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
    /** Shown after the article (the next article card). */
    afterArticle: (@Composable () -> Unit)? = null,
    /** Follow along: the sentence being read aloud from this article (null when not reading). */
    readAloudSentence: State<String?>? = null,
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
                    RYAsyncImage(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 8.dp, bottom = 8.dp)
                                .heightIn(max = 280.dp)
                                .aspectRatio(16f / 10f)
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
                // Follow along: the page keeps the sentence being read in view (about a third
                // down the screen). Scrolling yourself stops that until "Back to reading".
                val scope = rememberCoroutineScope()
                var webViewTop by remember { mutableFloatStateOf(0f) }
                var viewportHeight by remember { mutableIntStateOf(0) }
                var following by remember { mutableStateOf(true) }
                var autoScrolling by remember { mutableStateOf(false) }
                var sentenceTop by remember { mutableFloatStateOf(-1f) }
                val readingAloud = readAloudSentence?.value != null
                fun showSentence(top: Float) {
                    val target =
                        (webViewTop + top - viewportHeight * 0.3f)
                            .toInt()
                            .coerceIn(0, scrollState.maxValue)
                    scope.launch {
                        autoScrolling = true
                        try {
                            scrollState.animateScrollTo(target)
                        } finally {
                            autoScrolling = false
                        }
                    }
                }
                LaunchedEffect(scrollState) {
                    snapshotFlow { scrollState.isScrollInProgress }
                        .collect { moving ->
                            if (moving && !autoScrolling && readAloudSentence?.value != null) {
                                following = false
                            }
                        }
                }
                LaunchedEffect(readingAloud) { if (!readingAloud) following = true }
                Box(
                    modifier =
                        modifier
                            .padding(top = contentPadding.calculateTopPadding())
                            .fillMaxSize()
                ) {
                Column(modifier = Modifier.fillMaxSize().onSizeChanged { viewportHeight = it.height }) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(modifier = Modifier.then(maxWidthModifier)) {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            headline()

                            RYWebView(
                                modifier =
                                    Modifier.fillMaxSize().onGloballyPositioned {
                                        webViewTop = it.positionInParent().y
                                    },
                                content = body,
                                baseUrl = link,
                                refererDomain = link.extractDomain(),
                                onImageClick = onImageClick,
                                readAloudSentence = readAloudSentence,
                                onReadAloudPosition = { top ->
                                    sentenceTop = top
                                    if (following) showSentence(top)
                                },
                            )
                            afterArticle?.invoke()
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = readingAloud && !following,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    // Above the reading bar, with the same gap as the reading bar has below it.
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = FloatingBarGap + ReadingBarHeight + FloatingBarGap),
                ) {
                    Surface(
                        onClick = {
                            following = true
                            if (sentenceTop >= 0f) showSentence(sentenceTop)
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.reading_back_to_reading),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                }
            }

            ReadingRendererPreference.NativeComponent -> {
                SelectionContainer {
                    LazyColumn(
                        modifier = modifier.fillMaxSize(),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                            headline()
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
