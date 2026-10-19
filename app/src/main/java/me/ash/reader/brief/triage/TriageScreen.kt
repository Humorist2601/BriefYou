@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.triage

import androidx.compose.material3.LoadingIndicator
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import me.ash.reader.brief.stats.ReadingStats
import me.ash.reader.brief.list.CelebrationBurst
import me.ash.reader.brief.reading.estimateReadingMinutes

import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.animation.core.FastOutSlowInEasing
import me.ash.reader.brief.reading.FeedContent
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.edit
import coil.size.Precision
import coil.size.Scale
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.component.menu.AnimatedDropdownMenu
import me.ash.reader.brief.feeds.rememberFeedAccent
import me.ash.reader.brief.ui.GoogleSansWideFontFamily // Brief You
/** What a card can be swiped to. */
internal enum class TriageAction {
    /** Mark as read and move on. */
    Read,
    /** Star it and mark it as read. */
    Save,
    /** Leave it unread and move on. */
    Skip,
    /** Close triage and open the article. */
    Open,
}

/** Accepting a card (read, save) feels firmer than moving it along (skip, open). */
private fun HapticFeedback.commit(action: TriageAction) =
    performHapticFeedback(
        when (action) {
            TriageAction.Read,
            TriageAction.Save -> HapticFeedbackType.Confirm
            TriageAction.Skip,
            TriageAction.Open -> HapticFeedbackType.ContextClick
        }
    )

internal data class TriageStep(val article: ArticleWithFeed, val action: TriageAction)

/** What triage can continue with once the current group or feed is done. */
data class TriageNext(
    val id: String,
    val name: String,
    val unread: Int,
    /** True for a feed (when triage was started from a feed), false for a group. */
    val isFeed: Boolean = false,
)

/**
 * One triage run: the cards, where you are, and what you did (for Undo). It lives outside the
 * screen, so opening an article from triage and coming back continues exactly where you were. When
 * a group or feed is finished, [startNext] carries on with the next one in the same session.
 */
class TriageSession(
    articles: List<ArticleWithFeed>,
    groupId: String? = null,
    feedId: String? = null,
    title: String? = null,
) {
    var articles by mutableStateOf(articles)
        private set

    /** The group being triaged, or null when it is a single feed or everything. */
    var groupId by mutableStateOf(groupId)
        private set

    /** The feed being triaged, or null when it is a group or everything. */
    var feedId by mutableStateOf(feedId)
        private set

    /** The name of the group or feed, shown in the header. */
    var title by mutableStateOf(title)
        private set

    /** Groups and feeds already triaged in this session, so "next" never goes round in circles. */
    var visited by mutableStateOf(setOfNotNull(groupId, feedId))
        private set

    var index by mutableIntStateOf(0)
    internal val history = mutableStateListOf<TriageStep>()

    /** Auto-advance is running: cards are skipped on their own after a few seconds. */
    var autoRunning by mutableStateOf(false)

    fun startNext(next: TriageNext, cards: List<ArticleWithFeed>) {
        visited = visited + next.id
        groupId = if (next.isFeed) null else next.id
        feedId = if (next.isFeed) next.id else null
        title = next.name
        articles = cards
        index = 0
        history.clear()
    }
}

/**
 * Triage mode: a full-screen stack of cards for clearing a backlog quickly. Swipe right to mark
 * as read, left to save (star), up to open, down to skip; the buttons do the same thing.
 */
@Composable
fun TriageScreen(
    session: TriageSession,
    onRead: (ArticleWithFeed) -> Unit,
    onUnread: (ArticleWithFeed) -> Unit,
    onStar: (ArticleWithFeed, Boolean) -> Unit,
    onOpen: (ArticleWithFeed) -> Unit,
    onClose: () -> Unit,
    findNext: (groupId: String?, feedId: String?, visited: Set<String>) -> TriageNext?,
    loadNext: suspend (TriageNext) -> List<ArticleWithFeed>,
) {
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var exit by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    // Which way the screen is thrown while closing: towards the edge the back gesture started from.
    // Closing with a button (or the back key) has no edge, so it stays 0 and the screen just shrinks
    // and fades towards its center.
    var slideDirection by remember { mutableFloatStateOf(0f) }
    // During a back gesture the screen follows your finger up and down, like one of its cards.
    var touchDy by remember { mutableFloatStateOf(0f) }
    var touchStartY by remember { mutableFloatStateOf(Float.NaN) }
    val maxFollowY = with(LocalDensity.current) { 72.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    // Material 3 Expressive springs: a lively one for the way back, a softer one for throwing away.
    val settleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val tossSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }

    // Finishes the exit and only then removes Triage, so it never vanishes in a single frame.
    suspend fun playExit() {
        if (closing) return
        closing = true
        if (slideDirection != 0f) {
            // A back gesture: throw the whole screen off to the side, springing like the cards do.
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            coroutineScope {
                launch { animate(backProgress, 1f, animationSpec = tossSpec) { v, _ -> backProgress = v } }
                launch { animate(0f, 1f, animationSpec = tossSpec) { v, _ -> exit = v } }
            }
        } else {
            val spec = tween<Float>(durationMillis = 240, easing = FastOutLinearInEasing)
            coroutineScope {
                launch { animate(backProgress, 1f, animationSpec = spec) { v, _ -> backProgress = v } }
                launch { animate(0f, 1f, animationSpec = spec) { v, _ -> exit = v } }
            }
        }
        onClose()
    }

    // Predictive back: while you swipe, the screen shrinks and its corners morph into a rounded
    // card, it tilts a little and follows your finger. Let go to throw it away; drag back to cancel
    // and it springs back.
    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { event ->
                if (touchStartY.isNaN()) touchStartY = event.touchY
                backProgress = event.progress
                slideDirection = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                touchDy = ((event.touchY - touchStartY) * 0.4f).coerceIn(-maxFollowY, maxFollowY)
            }
            playExit()
        } catch (e: CancellationException) {
            if (!closing) {
                scope.launch {
                    coroutineScope {
                        launch { animate(backProgress, 0f, animationSpec = settleSpec) { v, _ -> backProgress = v } }
                        launch { animate(touchDy, 0f, animationSpec = settleSpec) { v, _ -> touchDy = v } }
                    }
                    touchStartY = Float.NaN
                    slideDirection = 0f
                }
            }
        }
    }

    // The card is drawn on two layers. The outer one moves (scale, slide, tilt, fade) and draws a
    // soft shadow around the card; the surface inside is clipped to the rounded shape. Without the
    // shadow the card melts into the list behind it, as both have the same color.
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        modifier =
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val e = enter.value
                    val p = backProgress.coerceIn(0f, 1f)
                    if (slideDirection != 0f) {
                        // The screen becomes a rounded card (the corners morph in quickly), shrinks,
                        // tilts and follows your finger; once released it is thrown off the side it
                        // came from (`exit` runs from 0 to 1 on a spring).
                        val s = (0.94f + 0.06f * e) * (1f - 0.14f * p)
                        scaleX = s
                        scaleY = s
                        translationX = slideDirection * (p * 28.dp.toPx() + exit * size.width)
                        translationY = touchDy
                        rotationZ = slideDirection * (p * 3f + exit * 8f)
                        alpha = e * (1f - exit.coerceIn(0f, 1f))
                    } else {
                        // A button (or the back key): shrink and fade towards the center.
                        val scale =
                            (0.94f + 0.06f * e) * (1f - 0.12f * backProgress) * (1f - 0.06f * exit) *
                                (1f - 0.1f * exit)
                        scaleX = scale
                        scaleY = scale
                        alpha = e * (1f - 0.3f * backProgress) * (1f - exit)
                    }
                }
                .drawBehind {
                    val p = backProgress.coerceIn(0f, 1f)
                    drawSoftShadow(
                        // Fades in quickly as the pull starts, so the card is set apart from the list.
                        strength = (p * 3f).coerceIn(0f, 1f),
                        corner = closingCorner(slideDirection != 0f, p, backProgress),
                        alpha = if (darkTheme) 0.55f else 0.30f,
                    )
                }
    ) {
    Surface(
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                val p = backProgress.coerceIn(0f, 1f)
                shape = RoundedCornerShape(closingCorner(slideDirection != 0f, p, backProgress))
                clip = backProgress > 0f
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        TriageContent(
            session,
            onRead,
            onUnread,
            onStar,
            onOpen,
            onClose = { scope.launch { playExit() } },
            findNext,
            loadNext,
        )
    }
    }
}

/** The corner radius of the screen while it is being closed: it morphs from square to a card. */
private fun androidx.compose.ui.unit.Density.closingCorner(
    gesture: Boolean,
    p: Float,
    backProgress: Float,
): Float =
    if (gesture) 44.dp.toPx() * (p * 2.5f).coerceIn(0f, 1f) else backProgress * 40.dp.toPx()

/**
 * A soft shadow around the whole shape, a little lower than the shape itself. It is built from many
 * thin rounded outlines, one every ~1 dp, each as dark as the shadow should be at that distance
 * from the edge (a smooth curve from [alpha] at the edge down to nothing). They overlap slightly and
 * are too close together to see, so it reads as one continuous fade, not stacked layers.
 */
private fun DrawScope.drawSoftShadow(strength: Float, corner: Float, alpha: Float) {
    if (strength <= 0.01f) return
    val rings = 56
    val spread = 44.dp.toPx() * strength
    val drop = 10.dp.toPx() * strength
    val step = spread / rings
    // From the outermost ring inwards, so the darker rings near the edge are drawn last.
    for (i in rings downTo 0) {
        val distance = step * i
        val t = i / rings.toFloat()
        val falloff = (1f - t) * (1f - t)
        drawRoundRect(
            color = Color.Black.copy(alpha = alpha * strength * falloff),
            topLeft = Offset(-distance, drop - distance),
            size = Size(size.width + 2 * distance, size.height + 2 * distance),
            cornerRadius = CornerRadius(corner + distance),
            style = Stroke(width = step * 1.6f),
        )
    }
}

@Composable
private fun TriageContent(
    session: TriageSession,
    onRead: (ArticleWithFeed) -> Unit,
    onUnread: (ArticleWithFeed) -> Unit,
    onStar: (ArticleWithFeed, Boolean) -> Unit,
    onOpen: (ArticleWithFeed) -> Unit,
    onClose: () -> Unit,
    findNext: (groupId: String?, feedId: String?, visited: Set<String>) -> TriageNext?,
    loadNext: suspend (TriageNext) -> List<ArticleWithFeed>,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var loadingNext by remember { mutableStateOf(false) }
    // Going to the next group or feed: the "All caught up" screen floats up and away, then the new
    // stack of cards swipes up from the bottom of the screen (on a lively Material 3 Expressive spring).
    val doneExit = remember { Animatable(0f) }
    val stackRise = remember { Animatable(0f) }
    val riseSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // Auto-advance: the delay is remembered between sessions; changing it is a long-press on the
    // play button.
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("triage", android.content.Context.MODE_PRIVATE) }
    var autoSeconds by remember { mutableIntStateOf(prefs.getInt("auto_seconds", 5)) }
    val autoProgress = remember { mutableFloatStateOf(0f) }
    // A finger on the card pauses auto-advance; the button at the top shows it.
    var autoHeld by remember { mutableStateOf(false) }
    val articles = session.articles
    val history = session.history
    val index = session.index
    val total = articles.size
    val current = articles.getOrNull(index)

    // When this group or feed is finished: the next one (in the Feeds page order) with unread
    // articles. After a group it is the next group; after a feed, the next feed.
    val done = current == null
    val next =
        remember(session.groupId, session.feedId, session.visited, done) {
            if (done) findNext(session.groupId, session.feedId, session.visited) else null
        }

    fun decide(action: TriageAction) {
        val article = current ?: return
        if (action != TriageAction.Skip) ReadingStats.triaged(context, 1) // Reading stats
        when (action) {
            TriageAction.Read -> onRead(article)
            TriageAction.Save -> {
                onStar(article, true)
                onRead(article)
            }
            TriageAction.Skip -> {}
            // Opening marks it read. The step is recorded first, so the session is already
            // up to date if the screen is hidden while you read the article.
            TriageAction.Open -> {
                history += TriageStep(article, action)
                session.index++
                onOpen(article)
                return
            }
        }
        history += TriageStep(article, action)
        session.index++
    }

    // Undo: the card comes back from where it went (see TriageStack), so this remembers which card
    // and which way it left.
    var returning by remember { mutableStateOf<TriageStep?>(null) }

    fun undo() {
        val step = history.removeLastOrNull() ?: return
        if (step.action != TriageAction.Skip) ReadingStats.triaged(context, -1) // Reading stats
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        returning = step
        when (step.action) {
            TriageAction.Read,
            TriageAction.Open -> onUnread(step.article)
            TriageAction.Save -> {
                onStar(step.article, false)
                onUnread(step.article)
            }
            else -> {}
        }
        session.index = (session.index - 1).coerceAtLeast(0)
    }

    // A confirming tap when the last card is dealt with.
    LaunchedEffect(current == null) {
        if (current == null && history.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            ReadingStats.inboxZero(context) // Reading stats
        }
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        // Top bar: close, title and counter, undo
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.triage),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansWideFontFamily,
                    fontWeight = FontWeight.SemiBold,
                )
                if (current != null) {
                    val left =
                        pluralStringResource(R.plurals.triage_left, total - index, total - index)
                    Text(
                        text = session.title?.takeIf { it.isNotBlank() }?.let { "$it · $left" } ?: left,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AutoAdvanceButton(
                running = session.autoRunning,
                paused = autoHeld && current != null,
                seconds = autoSeconds,
                // No ring on the "All caught up" screen, where there is no card left to skip.
                progress = { if (current != null) autoProgress.floatValue else 0f },
                onToggle = {
                    session.autoRunning = !session.autoRunning
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
                onSeconds = {
                    autoSeconds = it
                    prefs.edit { putInt("auto_seconds", it) }
                },
            )
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = { undo() }, enabled = history.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.triage_undo),
                )
            }
        }

        // Slides to the next step instead of jumping.
        val shownProgress by
            animateFloatAsState(
                targetValue = if (total == 0) 1f else index.toFloat() / total,
                animationSpec = tween(durationMillis = 450),
                label = "triage progress",
            )
        LinearWavyProgressIndicator(
            progress = { shownProgress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        )

        if (current == null) {
            TriageDone(
                modifier =
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        alpha = 1f - doneExit.value
                        translationY = -doneExit.value * 56.dp.toPx()
                    },
                read = history.count { it.action == TriageAction.Read || it.action == TriageAction.Open },
                saved = history.count { it.action == TriageAction.Save },
                skipped = history.count { it.action == TriageAction.Skip },
                nothingToDo = total == 0,
                next = next,
                loadingNext = loadingNext,
                onNext = {
                    val target = next
                    if (target != null && !loadingNext) {
                        loadingNext = true
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        scope.launch {
                            // Load the cards while the "All caught up" screen floats away.
                            val loading = async {
                                runCatching { loadNext(target) }.getOrDefault(emptyList())
                            }
                            doneExit.animateTo(1f, tween(180))
                            val cards = loading.await()
                            // The new cards start below the screen and swipe up into place.
                            stackRise.snapTo(1f)
                            session.startNext(target, cards)
                            doneExit.snapTo(0f)
                            stackRise.animateTo(0f, riseSpec)
                            loadingNext = false
                        }
                    }
                },
                onClose = onClose,
            )
        } else {
            TriageStack(
                modifier =
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        translationY = stackRise.value * size.height
                    },
                current = current,
                next = articles.getOrNull(index + 1),
                autoSeconds = if (session.autoRunning && !loadingNext) autoSeconds else 0,
                onAutoProgress = { autoProgress.floatValue = it },
                onDecide = { decide(it) },
                onHeldChange = { autoHeld = it },
                returnFrom = returning?.takeIf { it.article.article.id == current.article.id }?.action,
                onReturned = { returning = null },
            )
        }
    }
}

@Composable
private fun TriageStack(
    modifier: Modifier,
    current: ArticleWithFeed,
    next: ArticleWithFeed?,
    autoSeconds: Int,
    onAutoProgress: (Float) -> Unit,
    onDecide: (TriageAction) -> Unit,
    onHeldChange: (Boolean) -> Unit = {},
    returnFrom: TriageAction? = null,
    onReturned: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Where a card that was dealt with went: off the side, the top or the bottom of the screen.
    // A card brought back with Undo starts there, so it never shows up in the middle first.
    val screen = LocalConfiguration.current
    val screenW = with(LocalDensity.current) { screen.screenWidthDp.dp.toPx() }
    val screenH = with(LocalDensity.current) { screen.screenHeightDp.dp.toPx() }
    fun exitPoint(action: TriageAction): Pair<Float, Float> =
        when (action) {
            TriageAction.Read -> screenW * 1.4f to 0f
            TriageAction.Save -> -screenW * 1.4f to 0f
            TriageAction.Open -> 0f to -screenH * 1.2f
            TriageAction.Skip -> 0f to screenH * 1.2f
        }
    // A fresh drag state for every card.
    val start = returnFrom?.let { exitPoint(it) } ?: (0f to 0f)
    val dragX = remember(current.article.id) { Animatable(start.first) }
    val dragY = remember(current.article.id) { Animatable(start.second) }
    var busy by remember(current.article.id) { mutableStateOf(false) }
    // Touching the card: being dragged, or held with a finger. Pauses auto-advance.
    var dragging by remember(current.article.id) { mutableStateOf(false) }
    var held by remember(current.article.id) { mutableStateOf(false) }
    // How far the auto-advance countdown has run on this card (0 to 1); kept while it is paused.
    val countdown = remember(current.article.id) { Animatable(0f) }

    // Undo: the card flies back in from where it went, springing into place, and the card behind
    // shrinks back as it lands. Nothing can be done with it until it is back.
    LaunchedEffect(current.article.id, returnFrom) {
        if (returnFrom == null) return@LaunchedEffect
        busy = true
        coroutineScope {
            launch { dragX.animateTo(0f, returnSpec) }
            launch { dragY.animateTo(0f, returnSpec) }
        }
        busy = false
        onReturned()
    }

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val thresholdX = widthPx * 0.28f
            val thresholdY = heightPx * 0.18f

            fun actionFor(x: Float, y: Float): TriageAction? {
                val fx = abs(x) / thresholdX
                val fy = abs(y) / thresholdY
                return when {
                    fx < 1f && fy < 1f -> null
                    fx >= fy -> if (x > 0) TriageAction.Read else TriageAction.Save
                    else -> if (y < 0) TriageAction.Open else TriageAction.Skip
                }
            }

            suspend fun settle(action: TriageAction?, feedback: Boolean = true) {
                if (action == null) {
                    val spec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                    coroutineScope {
                        launch { dragX.animateTo(0f, spec) }
                        launch { dragY.animateTo(0f, spec) }
                    }
                } else {
                    // A swipe gives its feedback as the card is let go; buttons already gave theirs.
                    if (feedback) haptic.commit(action)
                    val (tx, ty) =
                        when (action) {
                            TriageAction.Read -> widthPx * 1.4f to dragY.value
                            TriageAction.Save -> -widthPx * 1.4f to dragY.value
                            TriageAction.Open -> dragX.value to -heightPx * 1.2f
                            TriageAction.Skip -> dragX.value to heightPx * 1.2f
                        }
                    coroutineScope {
                        launch { dragX.animateTo(tx, tween(220)) }
                        launch { dragY.animateTo(ty, tween(220)) }
                    }
                    onDecide(action)
                }
            }

            // Buttons share the same fly-out animation as a swipe.
            val trigger: (TriageAction) -> Unit = { action ->
                if (!busy) {
                    busy = true
                    haptic.commit(action)
                    scope.launch {
                        val (px, py) =
                            when (action) {
                                TriageAction.Read -> thresholdX to 0f
                                TriageAction.Save -> -thresholdX to 0f
                                TriageAction.Open -> 0f to -thresholdY
                                TriageAction.Skip -> 0f to thresholdY
                            }
                        // A short nudge so the overlay shows what is about to happen.
                        coroutineScope {
                            launch { dragX.animateTo(px, tween(120)) }
                            launch { dragY.animateTo(py, tween(120)) }
                        }
                        settle(action, feedback = false)
                    }
                }
            }

            // Auto-advance: when the time is up the card is skipped (left unread). Touching the card
            // pauses the countdown where it is, and letting go carries on from there; every new
            // card starts from the beginning.
            val touching = dragging || held
            LaunchedEffect(touching, autoSeconds) { onHeldChange(touching && autoSeconds > 0) }
            LaunchedEffect(autoSeconds, current.article.id, touching, busy) {
                if (autoSeconds <= 0) {
                    countdown.snapTo(0f)
                    onAutoProgress(0f)
                } else if (!touching && !busy) {
                    onAutoProgress(countdown.value)
                    val remaining =
                        ((1f - countdown.value) * autoSeconds * 1000).toInt().coerceAtLeast(1)
                    countdown.animateTo(1f, tween(remaining, easing = LinearEasing)) {
                        onAutoProgress(value)
                    }
                    trigger(TriageAction.Skip)
                }
            }

            val progress =
                (hypot(dragX.value / thresholdX, dragY.value / thresholdY)).coerceIn(0f, 1f)
            val pending = actionFor(dragX.value, dragY.value)

            // The colored hint follows the drag from the very first pixel: the direction is the
            // dominant axis, and the strength is how far along it you are, eased (smoothstep) so it
            // fades in gently instead of jumping in when you move slowly.
            val fx = abs(dragX.value) / thresholdX
            val fy = abs(dragY.value) / thresholdY
            val strength = maxOf(fx, fy).coerceIn(0f, 1f)
            val hintAction: TriageAction? =
                when {
                    strength < 0.01f -> null
                    fx >= fy -> if (dragX.value > 0) TriageAction.Read else TriageAction.Save
                    else -> if (dragY.value < 0) TriageAction.Open else TriageAction.Skip
                }
            val hintAlpha = strength * strength * (3f - 2f * strength)

            // A light tick as the drag passes the point where letting go would act.
            LaunchedEffect(pending) {
                if (pending != null && !busy) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val backModifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            // The next card waits behind and grows as you drag.
                            val s = 0.93f + 0.07f * progress
                            scaleX = s
                            scaleY = s
                            translationY = (1f - progress) * 16.dp.toPx()
                        }
                    val frontModifier =
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                translationX = dragX.value
                                translationY = dragY.value
                                rotationZ = (dragX.value / widthPx) * 12f
                            }
                            // Holding the card (without moving it) pauses auto-advance.
                            .pointerInput(current.article.id + "hold") {
                                detectTapGestures(
                                    onPress = {
                                        held = true
                                        tryAwaitRelease()
                                        held = false
                                    }
                                )
                            }
                            .pointerInput(current.article.id) {
                                detectDragGestures(
                                    onDragStart = { dragging = true },
                                    onDragEnd = {
                                        dragging = false
                                        if (!busy) {
                                            busy = true
                                            scope.launch {
                                                settle(actionFor(dragX.value, dragY.value))
                                                busy = false
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        scope.launch { settle(null) }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (!busy) {
                                            scope.launch {
                                                dragX.snapTo(dragX.value + amount.x)
                                                dragY.snapTo(dragY.value + amount.y)
                                            }
                                        }
                                    },
                                )
                            }

                    // Back to front, each card keyed by its article. When the next card becomes the
                    // front card it keeps its composition, so its image is already loaded and the
                    // previous card's image can't flash on it.
                    listOfNotNull(next, current).forEach { card ->
                        val isFront = card.article.id == current.article.id
                        key(card.article.id) {
                            TriageCard(
                                article = card,
                                overlay = if (isFront) hintAction else null,
                                overlayAlpha = if (isFront) hintAlpha else 0f,
                                modifier = if (isFront) frontModifier else backModifier,
                            )
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(
                        icon = Icons.Rounded.Star,
                        label = stringResource(R.string.triage_save),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        trigger(TriageAction.Save)
                    }
                    ActionButton(
                        icon = Icons.Rounded.SkipNext,
                        label = stringResource(R.string.triage_skip),
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        trigger(TriageAction.Skip)
                    }
                    ActionButton(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = stringResource(R.string.triage_open),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        trigger(TriageAction.Open)
                    }
                    ActionButton(
                        icon = Icons.Rounded.Check,
                        label = stringResource(R.string.triage_read),
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        big = true,
                    ) {
                        trigger(TriageAction.Read)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(if (big) 68.dp else 56.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = container,
                    contentColor = content,
                ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(if (big) 32.dp else 26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TriageCard(
    article: ArticleWithFeed,
    overlay: TriageAction?,
    overlayAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val feed = article.feed
    val item = article.article
    val minutes = remember(item.id) { estimateReadingMinutes(item.rawDescription) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FeedIcon(feedName = feed.name, iconUrl = feed.icon, size = 24.dp)
                    Text(
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        text = feed.name,
                        style = MaterialTheme.typography.labelLarge,
                        // The same per-website accent the article list uses.
                        color = rememberFeedAccent(feed.icon) ?: MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                    minutes?.let { stringResource(R.string.reading_time_min, it) },
                                    item.dateString?.takeIf { it.isNotBlank() },
                                )
                                .joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                if (item.img != null) {
                    Spacer(Modifier.height(16.dp))
                    RYAsyncImage(
                        modifier =
                            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(24.dp)),
                        data = FeedContent.listImage(item.img, item.link),
                        scale = Scale.FILL,
                        precision = Precision.INEXACT,
                        size = SIZE_1000,
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(12.dp))
                if (item.shortDescription.isNotBlank()) {
                    Text(
                        text = item.shortDescription,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Colored hint that fades in while you drag.
            if (overlay != null && overlayAlpha > 0.005f) {
                val (color, icon, label) = overlayStyle(overlay)
                val strongest = hintAlpha(color)
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(color.copy(alpha = strongest * overlayAlpha)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = overlayAlpha
                                // The icon and label also grow in, instead of just appearing.
                                val grow = 0.7f + 0.3f * overlayAlpha
                                scaleX = grow
                                scaleY = grow
                            },
                    ) {
                        Box(
                            modifier =
                                Modifier.size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansWideFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** How strongly the drag hints cover a card at most. */
private const val HINT_ALPHA = 0.88f

/**
 * How strongly a hint colour may cover the card: no brighter than the read hint, so a lighter
 * colour (the star's, in dark theme) doesn't glare next to the others.
 */
@Composable
private fun hintAlpha(color: Color): Float {
    val card = MaterialTheme.colorScheme.surfaceContainerHigh
    val reference = MaterialTheme.colorScheme.primaryContainer
    return remember(color, card, reference) {
        fun brightness(alpha: Float) = color.copy(alpha = alpha).compositeOver(card).luminance()
        val target = reference.copy(alpha = HINT_ALPHA).compositeOver(card).luminance()
        // Only a colour lighter than the card gets brighter as it covers more of it.
        if (color.luminance() <= card.luminance() || brightness(HINT_ALPHA) <= target) {
            HINT_ALPHA
        } else {
            var low = 0.3f
            var high = HINT_ALPHA
            repeat(12) {
                val mid = (low + high) / 2
                if (brightness(mid) > target) high = mid else low = mid
            }
            low
        }
    }
}

@Composable
private fun overlayStyle(action: TriageAction): Triple<Color, ImageVector, String> =
    when (action) {
        TriageAction.Read ->
            Triple(
                MaterialTheme.colorScheme.primaryContainer,
                Icons.Rounded.Check,
                stringResource(R.string.triage_read),
            )
        TriageAction.Save ->
            Triple(
                MaterialTheme.colorScheme.tertiaryContainer,
                Icons.Rounded.Star,
                stringResource(R.string.triage_save),
            )
        TriageAction.Skip ->
            Triple(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                Icons.Rounded.SkipNext,
                stringResource(R.string.triage_skip),
            )
        TriageAction.Open ->
            Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                Icons.AutoMirrored.Rounded.OpenInNew,
                stringResource(R.string.triage_open),
            )
    }

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriageDone(
    modifier: Modifier,
    read: Int,
    saved: Int,
    skipped: Int,
    nothingToDo: Boolean,
    next: TriageNext?,
    loadingNext: Boolean,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Inbox zero, like the Unread list: when you got through every card, the check pops in on
        // an Expressive cookie shape and small shapes burst around it (once, with a tick).
        val celebrate = !nothingToDo
        val pop = remember { Animatable(if (celebrate) 0f else 1f) }
        val burst = remember { Animatable(if (celebrate) 0f else 1f) }
        val haptic = LocalHapticFeedback.current
        if (celebrate) {
            LaunchedEffect(Unit) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
                burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        }
        Box(contentAlignment = Alignment.Center) {
            if (celebrate) CelebrationBurst(progress = { burst.value })
            Box(
                modifier =
                    Modifier.size(96.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            rotationZ = (1f - pop.value) * -45f
                        }
                        .clip(if (celebrate) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                        // The same tick colour as everywhere else (Unread, end of article).
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text =
                stringResource(
                    if (nothingToDo) R.string.triage_nothing else R.string.triage_all_done
                ),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = GoogleSansWideFontFamily,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (!nothingToDo) {
            Spacer(Modifier.height(24.dp))
            // This session at a glance, in the same order as the swipes: saved (left), skipped
            // (down), read (right).
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionTile(
                    Modifier.weight(1f).fillMaxHeight(),
                    Icons.Rounded.Star,
                    saved,
                    stringResource(R.string.triage_tile_saved),
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                SessionTile(
                    Modifier.weight(1f).fillMaxHeight(),
                    Icons.Rounded.SkipNext,
                    skipped,
                    stringResource(R.string.triage_tile_skipped),
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.onSurface,
                )
                SessionTile(
                    Modifier.weight(1f).fillMaxHeight(),
                    Icons.Rounded.Check,
                    read,
                    stringResource(R.string.triage_tile_read),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (next != null) {
            NextCard(next = next, loading = loadingNext, onClick = onNext)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) { Text(stringResource(R.string.triage_back_to_list)) }
        } else {
            Button(onClick = onClose) { Text(stringResource(R.string.triage_back_to_list)) }
        }
    }
}

/** One number of the finished session (read, saved or skipped) on a small tonal tile. */
@Composable
private fun SessionTile(
    modifier: Modifier,
    icon: ImageVector,
    count: Int,
    label: String,
    container: Color,
    content: Color,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansWideFontFamily,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The next group (or feed) with unread articles, as a big card: tap to carry on with it. Shows a
 * loading indicator while its cards are fetched.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NextCard(next: TriageNext, loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(52.dp)
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        if (next.isFeed) Icons.Rounded.RssFeed else Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.triage_next_up),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = next.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.triage_next_unread, next.unread),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Starts and stops auto-advance. While it runs, a ring around the button counts down to the next
 * skip. A long press opens the delay picker.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AutoAdvanceButton(
    running: Boolean,
    paused: Boolean = false,
    seconds: Int,
    progress: () -> Float,
    onToggle: () -> Unit,
    onSeconds: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    // Where the button is on screen: the popup is centered on it, but kept inside the screen edges.
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val halfMenu = with(density) { 120.dp.roundToPx() }
    val edge = with(density) { 8.dp.roundToPx() }
    var anchorX by remember { mutableIntStateOf(0) }
    // Always 48dp, the same touch area as the undo button, so nothing around it moves.
    Box(
        modifier =
            Modifier.size(48.dp).onGloballyPositioned {
                anchorX = it.boundsInWindow().center.x.roundToInt()
            },
        contentAlignment = Alignment.Center,
    ) {
        // As big as the undo button (40dp). While the countdown runs, the ring takes that size
        // and the button shrinks inside it, with room for the waves, so the two together are
        // still as big as the undo button.
        val buttonSize by
            animateDpAsState(if (running) 26.dp else 40.dp, animationSpec = tween(250), label = "auto button")
        Box(
            modifier =
                Modifier.size(buttonSize)
                    .clip(CircleShape)
                    .background(
                        if (running && !paused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // While a finger holds the card, the button shows the pause sign on a quieter color,
            // and the ring stops where it was.
            Icon(
                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                    stringResource(
                        when {
                            paused -> R.string.triage_auto_paused
                            running -> R.string.triage_auto_stop
                            else -> R.string.triage_auto_start
                        }
                    ),
                tint =
                    if (running && !paused) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(if (running) 18.dp else 24.dp),
            )
        }
        if (running) {
            // The same Expressive ring as read aloud: wavy while it counts down, still while a
            // finger holds the card, over a faint track.
            val ringColor =
                if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
            if (paused) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 4.dp,
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.2f),
                )
            } else {
                CircularWavyProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(40.dp),
                    color = ringColor,
                    trackColor = ringColor.copy(alpha = 0.2f),
                )
            }
        }
        // The same popup as the one on an article's long-press: same component, shape and item style.
        AnimatedDropdownMenu(
            modifier = Modifier.padding(horizontal = 12.dp),
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset =
                IntOffset(
                    anchorX.coerceIn(halfMenu + edge, (screenWidth - halfMenu - edge).coerceAtLeast(halfMenu + edge)),
                    0,
                ),
        ) {
            Text(
                text = stringResource(R.string.triage_auto_delay),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            listOf(3, 5, 8, 10, 15, 30).forEach { option ->
                val selected = option == seconds
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.triage_auto_seconds, option),
                            color =
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onSeconds(option)
                        menuOpen = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (selected) Icons.Rounded.Check else Icons.Outlined.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint =
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
