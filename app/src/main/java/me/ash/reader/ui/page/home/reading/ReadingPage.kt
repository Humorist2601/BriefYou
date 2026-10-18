package me.ash.reader.ui.page.home.reading

import androidx.compose.ui.graphics.toArgb
import me.ash.reader.ui.theme.palette.animateColorScheme
import me.ash.reader.ui.theme.palette.LocalWebViewColorScheme
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import me.ash.reader.infrastructure.preference.LocalAmoledDarkTheme
import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.ui.theme.palette.pictureColorScheme
import me.ash.reader.ui.component.FeedAccent
import me.ash.reader.infrastructure.rss.FeedContent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Row
import me.ash.reader.ui.page.home.reading.tts.ReadAloudSpeedSheet
import me.ash.reader.ui.page.home.flow.readingMinutes
import androidx.compose.runtime.mutableFloatStateOf
import me.ash.reader.ui.ext.openURL
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalOpenLink
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.NavigationAction
import me.ash.reader.ui.page.adaptive.ReaderState
import me.ash.reader.ui.page.home.reading.summary.SummaryRequest
import me.ash.reader.ui.page.home.reading.summary.SummarySheet
import me.ash.reader.ui.page.home.reading.tts.TtsButton

private const val UPWARD = 1
private const val DOWNWARD = -1

/**
 * The reading page, in colours from the article's picture (like music apps with album art): the
 * whole page, bars and sheets included, uses a Material scheme made from the picture's main
 * colour. Articles without a picture (or with a grey one) keep the app's colours. The page opens
 * in the app's colours and fades into the picture's over 0.4 s as soon as they're known.
 */
@Composable
fun ReadingPage(
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val readingUiState = viewModel.readingUiState.collectAsStateValue()
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val article = readingUiState.articleWithFeed?.article
    val html = readerState.content.text
    val pictureUrl =
        remember(article?.id, article?.img, html) {
            FeedContent.listImage(article?.img, article?.link)?.takeIf { it.isNotBlank() }
                ?: html?.let { FeedContent.extractHero(it).first }
        }
    var seed by remember(pictureUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(pictureUrl) {
        if (pictureUrl == null) return@LaunchedEffect
        seed = FeedAccent.pictureColor(context, pictureUrl)?.let { Color(it) }
    }
    val isDark = LocalDarkTheme.current.isDarkTheme()
    val isAmoled = isDark && LocalAmoledDarkTheme.current.value
    val pictureSeed = seed
    val scheme =
        remember(pictureSeed, isDark, isAmoled) {
            pictureSeed?.let { pictureColorScheme(it, isDark, isAmoled) }
        }
    val target = scheme ?: MaterialTheme.colorScheme
    // Everything fades into the new colours; the article's web view takes them straight away.
    CompositionLocalProvider(LocalWebViewColorScheme provides target) {
      MaterialTheme(colorScheme = animateColorScheme(target)) {
        ReadingPageContent(
            viewModel = viewModel,
            navigationAction = navigationAction,
            onLoadArticle = onLoadArticle,
            onNavAction = onNavAction,
            onNavigateToStylePage = onNavigateToStylePage,
            onNavigateToAiSettings = onNavigateToAiSettings,
        )
      }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
private fun ReadingPageContent(
    //    navController: NavHostController,
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val hapticFeedback = LocalHapticFeedback.current
    val isPullToSwitchArticleEnabled = LocalPullToSwitchArticle.current.value
    val readingUiState = viewModel.readingUiState.collectAsStateValue()
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val boldCharacters = LocalReadingBoldCharacters.current
    val coroutineScope = rememberCoroutineScope()

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    // Reading page additions: how far the article is scrolled, and the quick text settings sheet.
    var readingProgress by remember { mutableFloatStateOf(0f) }
    var showTextSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    val totalMinutes = remember(readerState.content.text) { readingMinutes(readerState.content.text ?: "") ?: 1 }
    val timeLeftLabel =
        if (readingProgress < 0.01f) null
        else {
            val left = kotlin.math.ceil(totalMinutes * (1f - readingProgress)).toInt()
            if (left <= 0) stringResource(R.string.reading_almost_done)
            else stringResource(R.string.reading_time_left, left)
        }
    // Follow along: the sentence being read aloud, when it's this article that is being read.
    val tts = viewModel.textToSpeechManager
    val currentArticleId = readerState.articleId
    // The media notification takes the page's colour (from the article's picture when it has one).
    val pageAccent = (LocalWebViewColorScheme.current ?: MaterialTheme.colorScheme).primary.toArgb()
    val startReadAloud: () -> Unit = {
        readerState.content.text?.let { html ->
            tts.readHtml(html, readerState.title, readerState.articleId, pageAccent)
        }
    }
    // Closing the article (going back, or opening another one) stops it being read aloud. Not when
    // the screen is only being rebuilt (rotation, theme change), and not when you leave the app.
    DisposableEffect(currentArticleId) {
        onDispose {
            val changing = context.findActivity()?.isChangingConfigurations == true
            if (!changing && currentArticleId != null && tts.sourceKey == currentArticleId) tts.stop()
        }
    }
    // A new article starts unscrolled.
    LaunchedEffect(readerState.articleId) { readingProgress = 0f }

    var currentImageData by remember { mutableStateOf(ImageData()) }

    val isShowToolBar =
        if (LocalReadingAutoHideToolbar.current.value) {
            readerState.articleId != null && !isReaderScrollingDown
        } else {
            true
        }

    var showTopDivider by remember { mutableStateOf(false) }

    //    LaunchedEffect(readerState.listIndex) {
    //        readerState.listIndex?.let {
    //            navController.previousBackStackEntry?.savedStateHandle?.set("articleIndex", it)
    //        }
    //    }

    var bringToTop by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        content = { paddings ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (readerState.articleId != null) {
                    TopBar(
                        isShow = isShowToolBar,
                        isScrolled = showTopDivider,
                        title = readerState.title,
                        progress = { readingProgress },
                        timeLeft = timeLeftLabel,
                        link = readerState.link,
                        onClick = { bringToTop = true },
                        navigationAction = navigationAction,
                        onNavButtonClick = onNavAction,
                        // The Style button opens the quick text sheet right over the article.
                        onNavigateToStylePage = { showTextSheet = true },
                        // The AI summary button is in the bottom bar now.
                        onSummarize = null,
                    )
                }

                val isNextArticleAvailable = readerState.nextArticle != null
                val isPreviousArticleAvailable = readerState.previousArticle != null

                if (readerState.articleId != null) {
                    // Content
                    AnimatedContent(
                        targetState = readerState,
                        transitionSpec = {
                            val direction =
                                when {
                                    initialState.nextArticle?.articleId == targetState.articleId ->
                                        UPWARD
                                    initialState.previousArticle?.articleId ==
                                        targetState.articleId -> DOWNWARD
                                    initialState.articleId == targetState.articleId -> {
                                        when (targetState.content) {
                                            is ReaderState.Description -> DOWNWARD
                                            else -> UPWARD
                                        }
                                    }

                                    else -> UPWARD
                                }
                            val exit = 100
                            val enter = exit * 2
                            (slideInVertically(
                                initialOffsetY = { (it * 0.2f * direction).toInt() },
                                animationSpec =
                                    spring(
                                        dampingRatio = .9f,
                                        stiffness = Spring.StiffnessLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                            ) +
                                fadeIn(
                                    tween(
                                        delayMillis = exit,
                                        durationMillis = enter,
                                        easing = LinearOutSlowInEasing,
                                    )
                                )) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { (it * -0.2f * direction).toInt() },
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                            visibilityThreshold = IntOffset.VisibilityThreshold,
                                        ),
                                ) +
                                    fadeOut(
                                        tween(durationMillis = exit, easing = FastOutLinearInEasing)
                                    ))
                        },
                        label = "",
                    ) {
                        remember { it }
                            .run {
                                val state =
                                    rememberPullToLoadState(
                                        key = content,
                                        onLoadNext =
                                            if (isNextArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.nextArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                        onLoadPrevious =
                                            if (isPreviousArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.previousArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                    )

                                val listState =
                                    rememberSaveable(
                                        inputs = arrayOf(content),
                                        saver = LazyListState.Saver,
                                    ) {
                                        LazyListState()
                                    }

                                val scrollState = rememberScrollState()

                                val scope = rememberCoroutineScope()

                                LaunchedEffect(bringToTop) {
                                    if (bringToTop) {
                                        scope
                                            .launch {
                                                if (scrollState.value != 0) {
                                                    scrollState.animateScrollTo(0)
                                                } else if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion { bringToTop = false }
                                    }
                                }

                                showTopDivider =
                                    snapshotFlow {
                                            scrollState.value >= 120 ||
                                                listState.firstVisibleItemIndex != 0
                                        }
                                        .collectAsStateValue(initial = false)

                                // How far the article is scrolled (exact, unlike read aloud).
                                LaunchedEffect(scrollState, listState) {
                                    snapshotFlow {
                                            val max = scrollState.maxValue
                                            if (max in 1 until Int.MAX_VALUE) {
                                                scrollState.value.toFloat() / max
                                            } else {
                                                val info = listState.layoutInfo
                                                val total = info.totalItemsCount
                                                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                                if (total <= 1 || listState.firstVisibleItemIndex == 0 &&
                                                    listState.firstVisibleItemScrollOffset == 0
                                                ) 0f
                                                else (last + 1).toFloat() / total
                                            }
                                        }
                                        .collect { readingProgress = it.coerceIn(0f, 1f) }
                                }

                                CompositionLocalProvider(
                                    LocalTextStyle provides
                                        LocalTextStyle.current.run {
                                            merge(
                                                lineHeight =
                                                    if (lineHeight.isSpecified)
                                                        (lineHeight.value *
                                                                LocalReadingTextLineHeight.current)
                                                            .sp
                                                    else TextUnit.Unspecified
                                            )
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Content(
                                            modifier =
                                                Modifier.pullToLoad(
                                                    state = state,
                                                    onScroll = { f ->
                                                        if (abs(f) > 2f)
                                                            isReaderScrollingDown = f < 0f
                                                    },
                                                    enabled = isPullToSwitchArticleEnabled,
                                                ),
                                            contentPadding = paddings,
                                            content = content.text ?: "",
                                            feedName = feedName,
                                            title = title.toString(),
                                            author = author,
                                            link = link,
                                            publishedDate = publishedDate,
                                            isLoading = content is ReaderState.Loading,
                                            scrollState = scrollState,
                                            listState = listState,
                                            onImageClick = { imgUrl, altText ->
                                                currentImageData = ImageData(imgUrl, altText)
                                                showFullScreenImageViewer = true
                                            },
                                            afterArticle = {
                                                // This article's own next one (not the
                                                // page's, which changes while it slides away).
                                                val next = nextArticle
                                                NextArticleCard(
                                                    nextId = next?.articleId,
                                                    load = viewModel::previewArticle,
                                                    onNext = {
                                                        next?.let { (id, index) ->
                                                            onLoadArticle(id, index)
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                        PullToLoadIndicator(
                                            state = state,
                                            canLoadPrevious = isPreviousArticleAvailable,
                                            canLoadNext = isNextArticleAvailable,
                                        )
                                    }
                                }
                            }
                    }
                }
                // Bottom Bar
                if (readerState.articleId != null) {
                    BottomBar(
                        isShow = isShowToolBar,
                        isUnread = readingUiState.isUnread,
                        isStarred = readingUiState.isStarred,
                        isNextArticleAvailable = isNextArticleAvailable,
                        isPreviousArticleAvailable = isPreviousArticleAvailable,
                        isFullContent =
                            readerState.content is ReaderState.FullContent ||
                                readerState.content is ReaderState.Error,
                        isBoldCharacters = boldCharacters.value,
                        onUnread = {
                            viewModel.updateReadStatus(it)
                            // The long press on the star has no visible result, so say what it did.
                            context.showToast(
                                context.getString(
                                    if (it) R.string.reading_marked_unread
                                    else R.string.reading_marked_read
                                )
                            )
                        },
                        onStarred = { viewModel.updateStarredStatus(it) },
                        onNextArticle = {
                            readerState.nextArticle?.let {
                                val (id, index) = it
                                onLoadArticle(id, index)
                            }
                        },
                        onPreviousArticle = {
                            readerState.previousArticle?.let {
                                val (id, index) = it
                                onLoadArticle(id, index)
                            }
                        },
                        onOpenAiSettings = onNavigateToAiSettings,
                        // Long press on full content: the article on its website, in the browser
                        // chosen in settings.
                        onOpenInBrowser = {
                            readerState.link?.takeIf { it.isNotBlank() }?.let {
                                context.openURL(it, openLink, openLinkSpecificBrowser)
                            }
                        },
                        onFullContent = {
                            if (it) viewModel.renderFullContent()
                            else viewModel.renderDescriptionContent()
                        },
                        onBoldCharacters = { (!boldCharacters).put(context, coroutineScope) },
                        onSummarize = { showSummary = true },
                        onReadAloud = startReadAloud,
                        ttsButton = {
                          // Only this article's reading: a summary being read aloud shows as
                          // not reading here, and tapping starts the article instead.
                          val ttsAny = viewModel.textToSpeechManager.stateFlow.collectAsStateValue()
                          val ttsState =
                              if (
                                  ttsAny is TextToSpeechManager.State.Error ||
                                      viewModel.textToSpeechManager.sourceKey == readerState.articleId
                              ) ttsAny
                              else TextToSpeechManager.State.Idle
                            TtsButton(
                                onClick = {
                                    when (it) {
                                        TextToSpeechManager.State.Error -> {
                                            context.showToast("TextToSpeech initialization failed")
                                        }

                                        TextToSpeechManager.State.Idle -> startReadAloud()

                                        is TextToSpeechManager.State.Reading -> {
                                            viewModel.textToSpeechManager.pause()
                                        }

                                        is TextToSpeechManager.State.Paused -> {
                                            viewModel.textToSpeechManager.resume()
                                        }

                                        TextToSpeechManager.State.Preparing -> {
                                            /* no-op */
                                        }
                                    }
                                },
                                state = ttsState,
                                onStop = { viewModel.textToSpeechManager.stop() },
                                onSpeed = { showSpeedSheet = true },
                            )
                        },
                    )
                }
            }
        },
    )
    val summaryArticleId = readerState.articleId
    if (showSummary && summaryArticleId != null) {
        SummarySheet(
            requestKey = summaryArticleId,
            request =
                SummaryRequest.Article(
                    articleId = summaryArticleId,
                    title = readerState.title,
                    html = readerState.content.text ?: "",
                ),
            heading = stringResource(R.string.ai_summary),
            onDismissRequest = { showSummary = false },
        )
    }
    if (showSpeedSheet) {
        val speed = viewModel.textToSpeechManager.speed.collectAsStateValue()
        ReadAloudSpeedSheet(
            speed = speed,
            onSpeed = { viewModel.textToSpeechManager.setSpeed(it) },
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showTextSheet) {
        TextSettingsSheet(
            onDismiss = { showTextSheet = false },
            onAllSettings = {
                showTextSheet = false
                onNavigateToStylePage()
            },
        )
    }
    if (showFullScreenImageViewer) {

        ReaderImageViewer(
            imageData = currentImageData,
            onDownloadImage = {
                viewModel.downloadImage(
                    it,
                    onSuccess = { context.showToast(context.getString(R.string.image_saved)) },
                    onFailure = {
                        // FIXME: crash the app for error report
                        th ->
                        throw th
                    },
                )
            },
            onDismissRequest = { showFullScreenImageViewer = false },
        )
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

