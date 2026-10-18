package me.ash.reader.brief.reading

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlin.math.ceil
import me.ash.reader.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.brief.list.BriefListViewModel
import me.ash.reader.brief.ai.SummaryRequest
import me.ash.reader.brief.ai.SummarySheet
import me.ash.reader.brief.feeds.FeedAccent
import me.ash.reader.brief.readaloud.ReadAloudSpeedSheet
import me.ash.reader.brief.readaloud.TextToSpeechManager
import me.ash.reader.brief.readaloud.TtsButton
import me.ash.reader.brief.ui.LocalWebViewColorScheme
import me.ash.reader.brief.ui.animateColorScheme
import me.ash.reader.brief.ui.pictureColorScheme
import me.ash.reader.infrastructure.preference.LocalAmoledDarkTheme
import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.NavigationAction
import me.ash.reader.ui.page.home.reading.ReadingPage
import me.ash.reader.ui.page.adaptive.ReaderState
import me.ash.reader.ui.page.adaptive.ReadingUiState

/*
 * Brief You's additions to Read You's reading page (ReadingPage), kept here so Read You's file
 * only needs a few calls: the picture's colours, reading progress and time left, the quick text
 * sheet, the AI summary, read aloud (with pause, speed and the media notification), the next
 * article card and Brief You's reading bar.
 */

/**
 * The reading page in colours from the article's picture (like music apps with album art): the
 * whole page, bars and sheets included, uses a Material scheme made from the picture's main
 * colour. Articles without a picture (or with a grey one) keep the app's colours. The page opens
 * in the app's colours and fades into the picture's as soon as they're known; the article's web
 * view takes them straight away.
 */
@Composable
fun BriefReadingTheme(viewModel: ArticleListReaderViewModel, content: @Composable () -> Unit) {
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
    CompositionLocalProvider(LocalWebViewColorScheme provides target) {
        MaterialTheme(colorScheme = animateColorScheme(target), content = content)
    }
}

/** Brief You's state on the reading page. */
@Stable
class BriefReadingState internal constructor() {
    /** How far the article is scrolled, 0 to 1. */
    var progress by mutableFloatStateOf(0f)
        internal set

    var showSummary by mutableStateOf(false)
    var showTextSheet by mutableStateOf(false)
    internal var showSpeedSheet by mutableStateOf(false)
}

/**
 * Brief You's state for the article being read. Closing the article (going back, or opening
 * another one) stops it being read aloud; not when the screen is only being rebuilt (rotation,
 * theme change), and not when you leave the app. A new article starts unscrolled.
 */
@Composable
fun rememberBriefReadingState(
    viewModel: ArticleListReaderViewModel,
    readerState: ReaderState,
): BriefReadingState {
    val context = LocalContext.current
    val state = remember { BriefReadingState() }
    val tts = viewModel.textToSpeechManager
    val articleId = readerState.articleId
    DisposableEffect(articleId) {
        onDispose {
            val changing = context.findActivity()?.isChangingConfigurations == true
            if (!changing && articleId != null && tts.sourceKey == articleId) tts.stop()
        }
    }
    LaunchedEffect(articleId) { state.progress = 0f }
    return state
}

/** "4 min left" (or "Almost done") once the article is scrolled, for the top bar. */
@Composable
fun briefTimeLeft(state: BriefReadingState, readerState: ReaderState): String? {
    val totalMinutes =
        remember(readerState.content.text) { readingMinutes(readerState.content.text ?: "") ?: 1 }
    val progress = state.progress
    if (progress < 0.01f) return null
    val left = ceil(totalMinutes * (1f - progress)).toInt()
    return if (left <= 0) stringResource(R.string.reading_almost_done)
    else stringResource(R.string.reading_time_left, left)
}

/** Follows how far the article is scrolled (exactly, unlike read aloud's position). */
@Composable
fun BriefTrackReadingProgress(
    state: BriefReadingState,
    scrollState: ScrollState,
    listState: LazyListState,
) {
    LaunchedEffect(scrollState, listState) {
        snapshotFlow {
                val max = scrollState.maxValue
                if (max in 1 until Int.MAX_VALUE) {
                    scrollState.value.toFloat() / max
                } else {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    if (
                        total <= 1 ||
                            listState.firstVisibleItemIndex == 0 &&
                                listState.firstVisibleItemScrollOffset == 0
                    )
                        0f
                    else (last + 1).toFloat() / total
                }
            }
            .collect { state.progress = it.coerceIn(0f, 1f) }
    }
}

/**
 * At the end of an article: the next one in a card (tap it to go there), or "You're all caught
 * up". [next] is this article's own next one (not the page's, which changes while it slides away).
 */
@Composable
fun BriefNextArticle(
    next: ReaderState.PrefetchResult?,
    @Suppress("UNUSED_PARAMETER") viewModel: ArticleListReaderViewModel,
    onLoadArticle: (String, Int) -> Unit,
    briefViewModel: BriefListViewModel = hiltViewModel(),
) {
    NextArticleCard(
        nextId = next?.articleId,
        load = briefViewModel::previewArticle,
        onNext = { next?.let { (id, index) -> onLoadArticle(id, index) } },
    )
}

/** Brief You's reading bar, in place of Read You's bottom bar. */
@Composable
fun BriefReadingBar(
    state: BriefReadingState,
    viewModel: ArticleListReaderViewModel,
    readingUiState: ReadingUiState,
    readerState: ReaderState,
    isShow: Boolean,
    onLoadArticle: (String, Int) -> Unit,
    onNavigateToAiSettings: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Opens links the way chosen in the settings ("open links in").
    val uriHandler = LocalUriHandler.current
    val boldCharacters = LocalReadingBoldCharacters.current
    val tts = viewModel.textToSpeechManager
    // The media notification takes the page's colour (from the article's picture when it has one).
    val pageAccent = (LocalWebViewColorScheme.current ?: MaterialTheme.colorScheme).primary.toArgb()
    val startReadAloud: () -> Unit = {
        readerState.content.text?.let { html ->
            tts.readHtml(html, readerState.title, readerState.articleId, pageAccent)
        }
    }
    BottomBar(
        isShow = isShow,
        isUnread = readingUiState.isUnread,
        isStarred = readingUiState.isStarred,
        isNextArticleAvailable = readerState.nextArticle != null,
        isPreviousArticleAvailable = readerState.previousArticle != null,
        isFullContent =
            readerState.content is ReaderState.FullContent ||
                readerState.content is ReaderState.Error,
        isBoldCharacters = boldCharacters.value,
        onUnread = {
            viewModel.updateReadStatus(it)
            // The long press on the star has no visible result, so say what it did.
            context.showToast(
                context.getString(
                    if (it) R.string.reading_marked_unread else R.string.reading_marked_read
                )
            )
        },
        onStarred = { viewModel.updateStarredStatus(it) },
        onNextArticle = { readerState.nextArticle?.let { (id, index) -> onLoadArticle(id, index) } },
        onPreviousArticle = {
            readerState.previousArticle?.let { (id, index) -> onLoadArticle(id, index) }
        },
        onOpenAiSettings = onNavigateToAiSettings,
        // Long press on full content: the article on its website, in the browser chosen in settings.
        onOpenInBrowser = {
            readerState.link
                ?.takeIf { it.isNotBlank() }
                ?.let { uriHandler.openUri(it) }
        },
        onFullContent = {
            if (it) viewModel.renderFullContent() else viewModel.renderDescriptionContent()
        },
        onBoldCharacters = { (!boldCharacters).put(context, coroutineScope) },
        onSummarize = { state.showSummary = true },
        onReadAloud = startReadAloud,
        ttsButton = {
            // Only this article's reading: a summary being read aloud shows as not reading here,
            // and tapping starts the article instead.
            val ttsAny = tts.stateFlow.collectAsStateValue()
            val ttsState =
                if (ttsAny is TextToSpeechManager.State.Error || tts.sourceKey == readerState.articleId)
                    ttsAny
                else TextToSpeechManager.State.Idle
            TtsButton(
                onClick = {
                    when (it) {
                        TextToSpeechManager.State.Error ->
                            context.showToast("TextToSpeech initialization failed")
                        TextToSpeechManager.State.Idle -> startReadAloud()
                        is TextToSpeechManager.State.Reading -> tts.pause()
                        is TextToSpeechManager.State.Paused -> tts.resume()
                        TextToSpeechManager.State.Preparing -> {
                            /* no-op */
                        }
                    }
                },
                state = ttsState,
                onStop = { tts.stop() },
                onSpeed = { state.showSpeedSheet = true },
            )
        },
    )
}

/** The reading page's sheets: AI summary, read aloud speed and the quick text settings. */
@Composable
fun BriefReadingSheets(
    state: BriefReadingState,
    viewModel: ArticleListReaderViewModel,
    readerState: ReaderState,
    onNavigateToStylePage: () -> Unit,
) {
    val summaryArticleId = readerState.articleId
    if (state.showSummary && summaryArticleId != null) {
        SummarySheet(
            requestKey = summaryArticleId,
            request =
                SummaryRequest.Article(
                    articleId = summaryArticleId,
                    title = readerState.title,
                    html = readerState.content.text ?: "",
                ),
            heading = stringResource(R.string.ai_summary),
            onDismissRequest = { state.showSummary = false },
        )
    }
    if (state.showSpeedSheet) {
        val speed = viewModel.textToSpeechManager.speed.collectAsStateValue()
        ReadAloudSpeedSheet(
            speed = speed,
            onSpeed = { viewModel.textToSpeechManager.setSpeed(it) },
            onDismiss = { state.showSpeedSheet = false },
        )
    }
    if (state.showTextSheet) {
        TextSettingsSheet(
            onDismiss = { state.showTextSheet = false },
            onAllSettings = {
                state.showTextSheet = false
                onNavigateToStylePage()
            },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Read You's reading page in the picture's colours ([BriefReadingTheme]); same arguments as
 * ReadingPage, so the call in Read You's code only changes its name.
 */
@Composable
fun BriefReadingPage(
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit,
    onNavigateToAiSettings: () -> Unit = {},
) {
    BriefReadingTheme(viewModel) {
        ReadingPage(
            viewModel = viewModel,
            navigationAction = navigationAction,
            onLoadArticle = onLoadArticle,
            onNavAction = onNavAction,
            onNavigateToStylePage = onNavigateToStylePage,
            onNavigateToAiSettings = onNavigateToAiSettings,
        )
    }
}
