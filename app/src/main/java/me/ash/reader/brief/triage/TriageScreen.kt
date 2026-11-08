@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.util.lerp
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalViewConfiguration
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
import me.ash.reader.brief.ui.LocalBackStack
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.component.menu.AnimatedDropdownMenu
import me.ash.reader.brief.feeds.rememberFeedAccent
import me.ash.reader.brief.ui.GoogleSansWideFontFamily // Brief You
import me.ash.reader.brief.ui.TonalCircleButton
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import me.ash.reader.brief.ui.TopBarButtonGroup
import me.ash.reader.brief.ui.TopBarAction
import androidx.compose.material.icons.outlined.Share
import me.ash.reader.infrastructure.preference.LocalSharedContent
import androidx.compose.foundation.Canvas
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
/** How long a finger must stay still on a card before the long-press glow starts. */
private const val CHARGE_DELAY_MS = 180L

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
    /** The feed's icon, shown instead of a generic one (feeds only). */
    val icon: String? = null,
)

/** The unread articles left elsewhere once a group or feed is done, and in how many places. */
data class TriageLeft(val unread: Int, val places: Int, val isFeeds: Boolean)

/** The end screen's ring: how much of everything unread is cleared, and how many places are clear. */
internal data class TriageProgress(
    val fraction: Float,
    val clear: Int,
    val total: Int,
    val remaining: Int,
    val isFeeds: Boolean,
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

    /** How many cards each group (or feed) of the run had when it was started, for the run's bar. */
    var placeSizes by mutableStateOf(listOfNotNull(feedId ?: groupId).associateWith { articles.size })
        private set

    /**
     * Every group (or feed) this run has included, in the Feeds page order. The Feeds page drops a
     * group once nothing in it is unread, so without this the groups you finish would vanish from
     * the run bar and the groups sheet.
     */
    internal var knownPlaces: List<TriageNext> = emptyList()

    /** [live] (the Feeds page's groups with unread now) merged into [knownPlaces], order kept. */
    internal fun mergePlaces(live: List<TriageNext>): List<TriageNext> {
        val byId = live.associateBy { it.id }
        val known = knownPlaces.map { byId[it.id] ?: it.copy(unread = 0) }
        val added = live.filter { l -> knownPlaces.none { it.id == l.id } }
        knownPlaces = known + added
        return knownPlaces
    }

    var index by mutableIntStateOf(0)
    internal val history = mutableStateListOf<TriageStep>()

    /** Auto-advance is running: cards are skipped on their own after a few seconds. */
    var autoRunning by mutableStateOf(false)

    /** Everything unread when the run started (all groups, or all feeds), and in how many. */
    var startUnread = 0
        private set
    var startPlaces = 0
        private set

    /** Cards skipped in the groups (or feeds) done before this one, and in how many of them. */
    var skippedBefore = 0
        private set

    /** Cards read (or opened) and saved in the groups (or feeds) done before this one. */
    var readBefore = 0
        private set
    var savedBefore = 0
        private set
    var placesWithSkips = 0
        private set

    /**
     * Cards skipped with a swipe in the groups before this one, for the totals. Cards not reached
     * because you jumped to another group aren't skipped, just not reached. It counts what you did,
     * so it isn't taken back when you return to a group and read the cards you skipped there.
     */
    var swipedSkipsBefore = 0
        private set

    /** Cards not reached in each group you jumped away from (its bar piece fills only as far as you got). */
    var notReachedIn by mutableStateOf(mapOf<String, Int>())
        private set

    fun setStart(unread: Int, places: Int) {
        startUnread = unread
        startPlaces = places
    }

    /**
     * Cards left unread in each group (or feed) you moved on from: skipped, or not reached yet when
     * you jumped away from the header. Those groups can be opened again from the header's sheet.
     */
    var leftIn by mutableStateOf(mapOf<String, Int>())
        private set

    /** Everything about the group (or feed) you just left, so Undo can step back into it. */
    private class Saved(
        val articles: List<ArticleWithFeed>,
        val groupId: String?,
        val feedId: String?,
        val title: String?,
        val visited: Set<String>,
        val placeSizes: Map<String, Int>,
        val index: Int,
        val history: List<TriageStep>,
        val skippedBefore: Int,
        val readBefore: Int,
        val savedBefore: Int,
        val placesWithSkips: Int,
        val leftIn: Map<String, Int>,
        val swipedSkipsBefore: Int,
        val notReachedIn: Map<String, Int>,
    )

    private val previous = ArrayDeque<Saved>()

    /** Undo on the first card of a group (or feed) can go back into the one before it. */
    val canGoBack: Boolean
        get() = previous.isNotEmpty()

    /**
     * Steps back into the group (or feed) you were in before this one, exactly as you left it, so
     * Undo can then take back its last card. Only when nothing was done here yet.
     */
    fun goBack(): Boolean {
        if (history.isNotEmpty()) return false
        val saved = previous.removeLastOrNull() ?: return false
        articles = saved.articles
        groupId = saved.groupId
        feedId = saved.feedId
        title = saved.title
        visited = saved.visited
        placeSizes = saved.placeSizes
        index = saved.index
        history.clear()
        history.addAll(saved.history)
        skippedBefore = saved.skippedBefore
        readBefore = saved.readBefore
        savedBefore = saved.savedBefore
        placesWithSkips = saved.placesWithSkips
        leftIn = saved.leftIn
        swipedSkipsBefore = saved.swipedSkipsBefore
        notReachedIn = saved.notReachedIn
        return true
    }

    /**
     * More cards of the group (or feed) you're in, added behind the ones left: the first group
     * starts from what the list had loaded, and a group loads at most 200 cards at a time.
     */
    fun appendCards(more: List<ArticleWithFeed>) {
        if (more.isEmpty()) return
        articles = articles + more
        (feedId ?: groupId)?.let { here -> placeSizes = placeSizes + (here to ((placeSizes[here] ?: 0) + more.size)) }
    }

    fun startNext(next: TriageNext, cards: List<ArticleWithFeed>) {
        previous.addLast(
            Saved(
                articles, groupId, feedId, title, visited, placeSizes, index, history.toList(),
                skippedBefore, readBefore, savedBefore, placesWithSkips, leftIn,
                swipedSkipsBefore, notReachedIn,
            )
        )
        // Skipped cards stay unread: they still count as left when the ring is worked out. So do
        // the cards not reached yet when you jump to another group (or feed) from the header.
        val swiped = history.count { it.action == TriageAction.Skip }
        val notReached = (articles.size - index).coerceAtLeast(0)
        val skipped = swiped + notReached
        skippedBefore += skipped
        if (skipped > 0) placesWithSkips++
        swipedSkipsBefore += swiped
        (feedId ?: groupId)?.let { here ->
            leftIn = leftIn + (here to skipped)
            notReachedIn = if (notReached > 0) notReachedIn + (here to notReached) else notReachedIn - here
        }
        // Going back to a group left with cards unread: they're dealt with again here, so they no
        // longer count as skipped before.
        leftIn[next.id]?.let { before ->
            skippedBefore -= before
            if (before > 0) placesWithSkips--
            leftIn = leftIn - next.id
        }
        // Back in a group you jumped away from: its piece fills again as you go.
        notReachedIn = notReachedIn - next.id
        readBefore += history.count { it.action == TriageAction.Read || it.action == TriageAction.Open }
        savedBefore += history.count { it.action == TriageAction.Save }
        visited = visited + next.id
        if (next.id !in placeSizes) placeSizes = placeSizes + (next.id to cards.size)
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
    findLeft: (groupId: String?, feedId: String?, visited: Set<String>) -> TriageLeft?,
    loadNext: suspend (TriageNext) -> List<ArticleWithFeed>,
    listPlaces: (feeds: Boolean, visited: Set<String>) -> List<TriageNext> = { _, _ -> emptyList() },
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
        // Always tells the list Triage is gone, even if the animation is cut short: otherwise
        // Triage would stay open but invisible, hiding the bottom bar and swallowing back gestures.
        try {
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
        } finally {
            onClose()
        }
    }

    // Predictive back: while you swipe, the screen shrinks and its corners morph into a rounded
    // card, it tilts a little and follows your finger. Let go to throw it away; drag back to cancel
    // and it springs back.
    // Off once Triage is closing: a second quick back gesture then goes to the list instead of
    // cutting Triage's exit short.
    PredictiveBackHandler(enabled = !closing) { progress ->
        try {
            progress.collect { event ->
                if (touchStartY.isNaN()) touchStartY = event.touchY
                backProgress = event.progress
                slideDirection = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                touchDy = ((event.touchY - touchStartY) * 0.4f).coerceIn(-maxFollowY, maxFollowY)
            }
            // Its own coroutine: a new gesture can't cancel the exit halfway.
            scope.launch { playExit() }
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
            findLeft,
            loadNext,
            listPlaces,
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
    findLeft: (groupId: String?, feedId: String?, visited: Set<String>) -> TriageLeft?,
    loadNext: suspend (TriageNext) -> List<ArticleWithFeed>,
    listPlaces: (feeds: Boolean, visited: Set<String>) -> List<TriageNext>,
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
    val nextNew =
        remember(session.groupId, session.feedId, session.visited, done) {
            if (done) findNext(session.groupId, session.feedId, session.visited) else null
        }
    val left =
        remember(session.groupId, session.feedId, session.visited, done) {
            if (done) findLeft(session.groupId, session.feedId, session.visited) else null
        }
    val skippedCount = history.count { it.action == TriageAction.Skip }
    // "All caught up" (and an inbox zero in Reading stats) only when nothing is unread anywhere:
    // nothing left in other groups or feeds, and no card skipped here.
    // Also nothing left behind in the groups of this run: cards skipped by swiping and cards not
    // reached when you jumped to another group are still unread.
    val allRead =
        // (A group you came back to may turn out empty, read in the list meanwhile: that's still
        // the end of the run, not "nothing to do".)
        done && (total > 0 || session.canGoBack) && nextNew == null && (left?.unread ?: 0) == 0 &&
            skippedCount == 0 && session.skippedBefore == 0
    // The last card's countdown doesn't stay in the ring on the end screen.
    LaunchedEffect(done) { if (done) autoProgress.floatValue = 0f }
    // The ring: how much of everything unread at the start is cleared now (what's left: the
    // unread in groups not done yet, and the cards skipped along the way), and how many groups
    // (or feeds) are clear. Null when it isn't known (Triage on everything).
    val progress =
        if (done && left != null && session.startUnread > 0) {
            val remaining = left.unread + session.skippedBefore + skippedCount
            TriageProgress(
                fraction = (1f - remaining.toFloat() / session.startUnread).coerceIn(0f, 1f),
                clear =
                    (session.startPlaces - left.places - session.placesWithSkips -
                            (if (skippedCount > 0) 1 else 0))
                        .coerceIn(0, session.startPlaces),
                total = session.startPlaces,
                remaining = remaining,
                isFeeds = left.isFeeds,
            )
        } else null

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
        // On the first card of a group (or feed): back into the one before, then take back its
        // last card as usual.
        if (history.isEmpty() && !session.goBack()) return
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

    // The run's groups (or feeds), for the header's sheet and the end screen's choices. Only for a
    // run started from a group or a feed (Triage on everything has nowhere else to go).
    val isFeeds = session.feedId != null
    val hasPlaces = session.groupId != null || session.feedId != null
    var sheetOpen by remember { mutableStateOf(false) }
    val hereId = session.feedId ?: session.groupId
    val runPlaces =
        remember(session.visited, hereId, done) {
            if (hasPlaces) session.mergePlaces(listPlaces(isFeeds, session.visited)) else emptyList()
        }
    // Where this group (or feed) is in the run, in the Feeds page order: "2 of 5".
    val position = runPlaces.indexOfFirst { it.id == hereId } + 1
    // As a group starts, and a few cards before its end, check it for more unread cards than the
    // stack holds
    // (the first group starts from what the list had loaded; a group loads at most 200 at a time)
    // and add them behind, so a big group is never taken as finished early. Cards already in the
    // stack (read, skipped or still to come) are left out.
    val wantMore = current != null && (index == 0 || total - index <= 3)
    LaunchedEffect(hereId, wantMore, total) {
        val here = hereId
        if (!wantMore || !hasPlaces || here == null) return@LaunchedEffect
        val seen = session.articles.mapTo(HashSet()) { it.article.id }
        val more =
            runCatching { loadNext(TriageNext(here, session.title.orEmpty(), 0, isFeed = isFeeds)) }
                .getOrDefault(emptyList())
                .filter { it.article.id !in seen }
        // Still in the same group (not moved on while loading).
        if ((session.feedId ?: session.groupId) == here) session.appendCards(more)
    }
    // When no group of the run is still untouched: back to one you left cards in (jumped away
    // from, or skipped some), in the Feeds order, with how many are left there.
    val backTo =
        if (done && nextNew == null && hasPlaces)
            runPlaces
                .firstOrNull { it.id != hereId && (session.leftIn[it.id] ?: 0) > 0 }
                ?.let { it.copy(unread = session.leftIn.getValue(it.id)) }
        else null
    val next = nextNew ?: backTo
    // Carries on with [target]: the end screen floats away (or the cards drop out of the way when
    // jumping from the middle of a group), then the new stack swipes up from the bottom.
    fun goTo(target: TriageNext) {
        if (loadingNext) return
        loadingNext = true
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        scope.launch {
            // Load the cards while the current screen moves out of the way.
            val loading = async { runCatching { loadNext(target) }.getOrDefault(emptyList()) }
            if (done) doneExit.animateTo(1f, tween(180))
            else stackRise.animateTo(1f, tween(200, easing = FastOutLinearInEasing))
            val cards = loading.await()
            stackRise.snapTo(1f)
            session.startNext(target, cards)
            doneExit.snapTo(0f)
            stackRise.animateTo(0f, riseSpec)
            loadingNext = false
        }
    }

    // A confirming tap when the last card is dealt with.
    LaunchedEffect(current == null) {
        if (current == null && history.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            if (allRead) ReadingStats.inboxZero(context) // Reading stats
        }
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        // Top bar: close, title and counter, the buttons. 12dp wider on each side than the cards,
        // so the buttons sit exactly where the other screens' top-bar buttons do (12dp from the
        // screen edges, like a top app bar's 4dp inset plus the buttons' own 8dp).
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .layout { measurable, constraints ->
                        val extra = 24.dp.roundToPx()
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + extra,
                                    maxWidth = constraints.maxWidth + extra,
                                )
                            )
                        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
                    }
                    .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same tonal circle as the article list's back button.
            TonalCircleButton(
                icon = Icons.Rounded.Close,
                description = stringResource(R.string.close),
                onClick = onClose,
            )
            // "Triage", and under it the group (or feed) you're in as a small tonal button, then the
            // cards left. Tap the button to see the run's groups and jump or skip. The count comes
            // after it, so the button only changes when the group does.
            val canJump = hasPlaces && position > 0
            val title = session.title?.takeIf { it.isNotBlank() }
            val leftText =
                if (current != null) pluralStringResource(R.plurals.triage_left, total - index, total - index)
                else null
            // A gap before the buttons, so a long group name shortens instead of pushing the count
            // against them.
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 12.dp)) {
                Text(
                    text = stringResource(R.string.triage),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansWideFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (canJump && title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Without this the button would take the 48dp minimum touch height and
                        // push this line away from "Triage".
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    sheetOpen = true
                                },
                                enabled = !loadingNext,
                                // Quiet, like the top-right pill: the same barely-there tone, a
                                // little apart from "Triage", in normal weight.
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp).weight(1f, fill = false),
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // A plain symbol: the feed's own icon is already on every card.
                                    Icon(
                                        if (isFeeds) Icons.Rounded.RssFeed else Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (leftText != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = leftText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                // Lined up with the button, which sits a little lower.
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    val sub = listOfNotNull(title, leftText).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // The same quiet joined pill as the other screens: Auto-advance, Share, Undo. While
            // auto-advance runs it leaves the pill as its own circle with the countdown ring, and
            // goes back in when it's turned off. Long press it (either way) to choose the delay.
            val toggleAuto = {
                session.autoRunning = !session.autoRunning
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
            var delayMenuOpen by remember { mutableStateOf(false) }
            Box {
                TriageTopButtons(
                    running = session.autoRunning,
                    // The same pause for a finger on the card, a card showing its summary, and
                    // the groups sheet open.
                    paused = (autoHeld && current != null) || sheetOpen,
                    // On a card: the countdown to the next card. On the end screen: the countdown
                    // to the next group (none on "All caught up").
                    progress = { autoProgress.floatValue },
                    onToggle = toggleAuto,
                    onLongPress = { delayMenuOpen = true },
                    // Share is on the card itself, so the top keeps just Play and Undo.
                    others =
                        listOf(
                            TopBarAction(
                                Icons.AutoMirrored.Rounded.Undo,
                                stringResource(R.string.triage_undo),
                                enabled = history.isNotEmpty() || session.canGoBack,
                            ) {
                                undo()
                            },
                        ),
                )
                AutoDelayMenu(
                    expanded = delayMenuOpen,
                    seconds = autoSeconds,
                    onSeconds = {
                        autoSeconds = it
                        prefs.edit { putInt("auto_seconds", it) }
                    },
                    onDismiss = { delayMenuOpen = false },
                )
            }
        }

        // This group's cards; slides to the next step instead of jumping.
        val targetProgress = if (total == 0) 1f else index.toFloat() / total
        // A new group starts from where it is (empty), it doesn't slide down from the last one.
        val progressAnim = remember(hereId) { Animatable(targetProgress) }
        LaunchedEffect(progressAnim, targetProgress) {
            progressAnim.animateTo(targetProgress, tween(durationMillis = 450))
        }
        // While swiping: this group's wavy bar. Once it's done: the whole run, one piece per
        // group, and the bar turns into one into the other (see TriageBar).
        TriageBar(
            groupProgress = { progressAnim.value },
            showRun = done && runPlaces.size > 1,
            runPlaces = runPlaces,
            sizes = session.placeSizes,
            visited = session.visited,
            notReached = session.notReachedIn,
            hereId = hereId,
        )

        if (current == null) {
            TriageDone(
                modifier =
                    Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                        alpha = 1f - doneExit.value
                        translationY = -doneExit.value * 56.dp.toPx()
                    },
                // All caught up: the whole run, every group (or feed) of it; otherwise this one.
                read =
                    history.count { it.action == TriageAction.Read || it.action == TriageAction.Open } +
                        (if (allRead) session.readBefore else 0),
                saved =
                    history.count { it.action == TriageAction.Save } +
                        (if (allRead) session.savedBefore else 0),
                // Only cards skipped with a swipe: those left behind by jumping to another group
                // aren't skipped, just not reached.
                skipped = skippedCount + (if (allRead) session.swipedSkipsBefore else 0),
                // Only a run that started empty; a group you came back to that turned out empty
                // shows the run's end screen instead.
                nothingToDo = total == 0 && !session.canGoBack,
                returning = backTo != null,
                allRead = allRead,
                progress = progress,
                // With auto-advance running, the next group starts by itself after the delay; the
                // countdown shows in the play button's ring at the top, as on the cards.
                // Paused while the groups sheet is open.
                // "Back to" a group you left cards in never starts by itself: auto-advance would
                // skip the same cards again and go round in circles.
                autoSeconds = if (session.autoRunning && backTo == null) autoSeconds else 0,
                paused = sheetOpen,
                onCountdown = { autoProgress.floatValue = it },
                title = session.title,
                left = left,
                next = next,
                loadingNext = loadingNext,
                onNext = { next?.let { goTo(it) } },
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
                paused = sheetOpen,
                onAutoProgress = { autoProgress.floatValue = it },
                onDecide = { decide(it) },
                onHeldChange = { autoHeld = it },
                returnFrom = returning?.takeIf { it.article.article.id == current.article.id }?.action,
                onReturned = { returning = null },
            )
        }
    }

    if (sheetOpen) {
        TriagePlacesSheet(
            // The run's groups, finished ones included (ticked "Done").
            places = runPlaces,
            currentId = session.feedId ?: session.groupId,
            currentLeft = (total - index).coerceAtLeast(0),
            visited = session.visited,
            leftIn = session.leftIn,
            isFeeds = isFeeds,
            title = session.title,
            skipTarget = remember(session.visited) { findNext(session.groupId, session.feedId, session.visited) },
            onPick = {
                sheetOpen = false
                goTo(it)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun TriageStack(
    modifier: Modifier,
    current: ArticleWithFeed,
    next: ArticleWithFeed?,
    autoSeconds: Int,
    /** Paused from outside the card (the groups sheet is open): the countdown stops where it is. */
    paused: Boolean = false,
    onAutoProgress: (Float) -> Unit,
    onDecide: (TriageAction) -> Unit,
    onHeldChange: (Boolean) -> Unit = {},
    returnFrom: TriageAction? = null,
    onReturned: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Long-press summaries (see TriageSummary.kt), kept for the whole run so Undo keeps them.
    val summaries = rememberCardSummaries()
    val currentSummary = summaries[current.article.id]
    // Holding the card builds up to the summary: it sinks a little and its border starts to
    // glow over the long-press time, then springs back as the summary starts.
    val charge = remember(current.article.id) { Animatable(0f) }
    // A summary still being written stops when its card leaves.
    DisposableEffect(current.article.id) {
        val id = current.article.id
        onDispose { summaries.stop(id) }
    }
    val backStack = LocalBackStack.current
    val openAiSettings = {
        @Suppress("UNCHECKED_CAST")
        (backStack as? MutableList<Any>)?.add(Route.AiModel)
        Unit
    }
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
            // card starts from the beginning. A card showing its summary stays paused.
            // (A card switched back to its description with a tap carries on counting down.)
            val touching = dragging || held || summaries.shown(current.article.id) != null || paused
            val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
            LaunchedEffect(held, dragging, currentSummary == null) {
                if (held && !dragging && currentSummary == null) {
                    // Only once the finger has stayed still for a moment: a swipe starts moving
                    // well before this, so swiping a card never shows the glow or the sink.
                    kotlinx.coroutines.delay(CHARGE_DELAY_MS)
                    charge.animateTo(
                        1f,
                        tween((longPressMs - CHARGE_DELAY_MS.toInt()).coerceAtLeast(120), easing = LinearEasing),
                    )
                } else {
                    charge.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                }
            }
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
                                // Sinks a little while held for a summary.
                                val press = 1f - 0.04f * charge.value
                                scaleX = press
                                scaleY = press
                            }
                            // Holding the card (without moving it) pauses auto-advance.
                            .pointerInput(current.article.id + "hold") {
                                detectTapGestures(
                                    onPress = {
                                        held = true
                                        tryAwaitRelease()
                                        held = false
                                    },
                                    // Holding still for a moment: a short AI summary on the card.
                                    onLongPress = {
                                        if (!busy && summaries[current.article.id] == null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            summaries.request(current, scope)
                                        }
                                    },
                                    // A tap on a summarised card: back to its own description,
                                    // and another tap shows the summary again.
                                    onTap = {
                                        if (summaries.toggle(current.article.id)) {
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    },
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
                                summary = summaries.shown(card.article.id),
                                hasHiddenSummary =
                                    summaries[card.article.id] != null &&
                                        summaries.shown(card.article.id) == null,
                                onOpenAiSettings = openAiSettings,
                                modifier =
                                    if (isFront) {
                                        frontModifier.aiGlow(
                                            summaries.shown(card.article.id),
                                            CARD_CORNER,
                                            charging = held && !dragging && currentSummary == null,
                                            charge = { charge.value },
                                        )
                                    } else backModifier,
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
    // Pressed, the circle squeezes into a rounded square and springs back, a little past round,
    // when let go: the same Expressive press as the back and top-bar buttons.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val size = if (big) 68.dp else 56.dp
    val bounce = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    val roundness by animateFloatAsState(if (pressed) 0.55f else 1f, bounce, label = "press shape")
    val squeeze by animateFloatAsState(if (pressed) 0.94f else 1f, bounce, label = "press size")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            interactionSource = interaction,
            // Corner as a share of half the size: 1 is a circle, lower is a squarer shape. The
            // spring lets it overshoot a little on the way back.
            shape = RoundedCornerShape(size / 2 * roundness.coerceIn(0.3f, 1f)),
            modifier =
                Modifier.size(size).graphicsLayer {
                    scaleX = squeeze
                    scaleY = squeeze
                },
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
    summary: CardSummary? = null,
    hasHiddenSummary: Boolean = false,
    onOpenAiSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val feed = article.feed
    val item = article.article
    val minutes = remember(item.id) { estimateReadingMinutes(item.rawDescription) }
    // Hold Share to share the card as a picture: what it shows (its summary, when it shows one),
    // drawn from the card itself, without the Share button.
    val context = LocalContext.current
    val cardLayer = rememberGraphicsLayer()
    var capturing by remember { mutableStateOf(false) }
    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        // Two frames, so the card is drawn again without the Share button before it's taken.
        withFrameNanos {}
        withFrameNanos {}
        val picture = runCatching { cardLayer.toImageBitmap() }.getOrNull()
        capturing = false
        if (picture != null) shareCardPicture(context, picture, item.link)
    }

    Surface(
        modifier =
            modifier.drawWithContent {
                cardLayer.record { this@drawWithContent.drawContent() }
                drawLayer(cardLayer)
            },
        shape = RoundedCornerShape(CARD_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FeedIcon(feedName = feed.name, iconUrl = feed.icon, size = 24.dp)
                    Text(
                        // A gap before the time, so a long feed name ends in "…" instead of
                        // running into it.
                        modifier = Modifier.weight(1f).padding(start = 10.dp, end = 10.dp),
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
                    // Share this article, right on its card: tap for the link (the app's setting:
                    // the link only, or the title and link), hold for the card as a picture. A
                    // tonal button, so it stands out from the card like the buttons below; hidden
                    // while the picture is taken.
                    val sharedContent = LocalSharedContent.current
                    val haptic = LocalHapticFeedback.current
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier.size(34.dp)
                                .graphicsLayer { alpha = if (capturing) 0f else 1f }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .combinedClickable(
                                    onClickLabel = stringResource(R.string.share),
                                    onLongClickLabel = stringResource(R.string.triage_share_picture),
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        capturing = true
                                    },
                                    onClick = { sharedContent.share(context, item.title, item.link) },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // The picture folds away to make room for a summary.
                val imageHeight by
                    animateDpAsState(
                        if (summary == null) 150.dp else 0.dp,
                        MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "picture",
                    )
                if (item.img != null && imageHeight > 0.5.dp) {
                    Spacer(Modifier.height(16.dp * (imageHeight / 150.dp)))
                    RYAsyncImage(
                        modifier =
                            Modifier.fillMaxWidth().height(imageHeight).clip(RoundedCornerShape(24.dp)),
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
                if (hasHiddenSummary) {
                    SummaryHint()
                    Spacer(Modifier.height(8.dp))
                }
                if (summary != null) {
                    CardSummaryContent(summary, onOpenAiSettings)
                } else if (item.shortDescription.isNotBlank()) {
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
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(hintColor(color).copy(alpha = HINT_ALPHA * overlayAlpha)),
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

/** The cards' corner radius. */
private val CARD_CORNER = 36.dp

/** How strongly the drag hints cover a card at most. */
private const val HINT_ALPHA = 0.88f

/**
 * A hint colour no brighter than the read hint, so a lighter colour (the star's, in dark theme)
 * doesn't glare next to the others. It is deepened (mixed with a little black) rather than made
 * see-through, so it keeps its full colour instead of looking washed out.
 */
@Composable
private fun hintColor(color: Color): Color {
    val card = MaterialTheme.colorScheme.surfaceContainerHigh
    val reference = MaterialTheme.colorScheme.primaryContainer
    return remember(color, card, reference) {
        fun shade(amount: Float) = lerp(color, Color.Black, amount)
        fun brightness(c: Color) = c.copy(alpha = HINT_ALPHA).compositeOver(card).luminance()
        val target = brightness(reference)
        if (brightness(color) <= target) {
            color
        } else {
            var low = 0f
            var high = 0.6f
            repeat(12) {
                val mid = (low + high) / 2
                if (brightness(shade(mid)) > target) low = mid else high = mid
            }
            shade(high)
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
    /** [next] is a group of this run you left cards in ("Back to"), not a new one. */
    returning: Boolean = false,
    allRead: Boolean,
    progress: TriageProgress?,
    autoSeconds: Int,
    /** The groups sheet is open: the countdown to the next group stops where it is. */
    paused: Boolean = false,
    onCountdown: (Float) -> Unit,
    title: String?,
    left: TriageLeft?,
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
        // Inbox zero, like the Unread list: when nothing is unread anywhere any more, the check pops
        // in on an Expressive cookie shape and small shapes burst around it (once, with a tick).
        // A group or feed done while more is unread elsewhere: a calm check on a cookie shape.
        val celebrate = allRead
        val groupDone = !allRead && !nothingToDo
        val pop =remember { Animatable(if (celebrate) 0f else 1f) }
        val burst = remember { Animatable(if (celebrate) 0f else 1f) }
        val haptic = LocalHapticFeedback.current
        if (celebrate) {
            LaunchedEffect(Unit) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
                burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        }
        if (groupDone && progress != null) {
            // How much of everything unread is cleared, as a wavy Expressive ring that fills in.
            ProgressRing(progress.fraction)
        } else Box(contentAlignment = Alignment.Center) {
            if (celebrate) CelebrationBurst(progress = { burst.value })
            Box(
                modifier =
                    Modifier.size(96.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            rotationZ = (1f - pop.value) * -45f
                        }
                        .clip(
                            if (nothingToDo) CircleShape else MaterialShapes.Cookie9Sided.toShape()
                        )
                        // The same tick colour as everywhere else (Unread, end of article) for
                        // inbox zero; the accent's container for a group done.
                        .background(
                            if (groupDone) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint =
                        if (groupDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text =
                when {
                    nothingToDo -> stringResource(R.string.triage_nothing)
                    allRead -> stringResource(R.string.triage_all_done)
                    // How many groups (or feeds) are clear, of all that had unread at the start.
                    progress != null ->
                        pluralStringResource(
                            if (progress.isFeeds) R.plurals.triage_feeds_clear
                            else R.plurals.triage_groups_clear,
                            progress.total,
                            progress.clear,
                            progress.total,
                        )
                    // Triage on everything: every card dealt with.
                    else -> {
                        val cleared = read + saved + skipped
                        pluralStringResource(R.plurals.triage_cards_cleared, cleared, cleared)
                    }
                },
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = GoogleSansWideFontFamily,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        // Which group (or feed) is clear, and what's left: how much is still unread elsewhere, or
        // the cards skipped here.
        val clearText = title?.let { stringResource(R.string.triage_group_clear, it) }
        val remainText =
            when {
                left != null && left.unread > 0 ->
                    pluralStringResource(
                        if (left.isFeeds) R.plurals.triage_left_feeds else R.plurals.triage_left_groups,
                        left.places,
                        left.unread,
                        left.places,
                    )
                skipped > 0 -> pluralStringResource(R.plurals.triage_skipped_left, skipped, skipped)
                else -> null
            }
        // With the ring: what's still unread, and where (by name when it's one group).
        val placesLeft = progress?.let { it.total - it.clear } ?: 0
        val ringText =
            when {
                progress == null || progress.remaining <= 0 -> null
                placesLeft == 1 && next != null && (left?.places ?: 0) == 1 ->
                    stringResource(R.string.triage_left_in_name, progress.remaining, next.name)
                else ->
                    pluralStringResource(
                        if (progress.isFeeds) R.plurals.triage_left_feeds else R.plurals.triage_left_groups,
                        placesLeft,
                        progress.remaining,
                        placesLeft,
                    )
            }
        val leftText =
            when {
                nothingToDo -> null
                allRead -> stringResource(R.string.triage_everything_read)
                progress != null -> ringText
                else -> listOfNotNull(clearText, remainText).joinToString(" · ").ifEmpty { null }
            }
        if (leftText != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = leftText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
            // Auto-advance: counts down (in the play button's ring at the top), then carries on.
            val countdown = remember(next) { Animatable(0f) }
            val continueNow by rememberUpdatedState(onNext)
            val showCountdown by rememberUpdatedState(onCountdown)
            LaunchedEffect(next, autoSeconds, loadingNext, paused) {
                if (autoSeconds <= 0 || loadingNext) {
                    countdown.snapTo(0f)
                    showCountdown(0f)
                    return@LaunchedEffect
                }
                // Paused: stays where it is, and carries on from there afterwards.
                showCountdown(countdown.value)
                if (paused) return@LaunchedEffect
                val remaining = ((1f - countdown.value) * autoSeconds * 1000).toInt().coerceAtLeast(1)
                countdown.animateTo(1f, tween(remaining, easing = LinearEasing)) {
                    showCountdown(value)
                }
                showCountdown(0f)
                continueNow()
            }
            NextCard(next = next, loading = loadingNext, returning = returning, onClick = onNext)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose) { Text(stringResource(R.string.triage_back_to_list)) }
        } else {
            Button(onClick = onClose) { Text(stringResource(R.string.triage_back_to_list)) }
        }
    }
}

/**
 * How much of everything unread is cleared: a wavy Expressive ring that fills in from where it
 * starts, with the percentage in the middle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressRing(fraction: Float) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(fraction) { shown.animateTo(fraction, tween(900, easing = FastOutSlowInEasing)) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
        // A softer wave than the default: a bit lower and longer, between calm and lively.
        CircularWavyProgressIndicator(
            progress = { shown.value },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            amplitude = { p -> if (p <= 0.05f || p >= 0.97f) 0f else 0.7f },
            wavelength = 22.dp,
        )
        Text(
            text = stringResource(R.string.triage_percent, (shown.value * 100).roundToInt()),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansWideFontFamily,
            fontWeight = FontWeight.SemiBold,
        )
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
private fun NextCard(
    next: TriageNext,
    loading: Boolean,
    returning: Boolean = false,
    onClick: () -> Unit,
) {
    // The same press as the four buttons: the card squeezes a little, its corners tighten, and it
    // springs back a little past its size when let go. The cookie turns by one of its bumps.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounce = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    val squeeze by animateFloatAsState(if (pressed) 0.96f else 1f, bounce, label = "next press size")
    val corner by animateDpAsState(if (pressed) 18.dp else 28.dp, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "next press corner")
    val turn by animateFloatAsState(if (pressed) 360f / 7 else 0f, bounce, label = "next press turn")
    Surface(
        onClick = onClick,
        enabled = !loading,
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(corner),
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            },
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(52.dp)
                        .graphicsLayer { rotationZ = turn }
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                // Turned back the other way, so only the shape turns and the icon stays upright.
                Box(Modifier.graphicsLayer { rotationZ = -turn }, contentAlignment = Alignment.Center) {
                if (loading) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else if (next.isFeed) {
                    // The feed's icon fills the whole cookie shape: it is round and as big as the
                    // shape, so the shape's clip cuts it into the cookie.
                    FeedIcon(feedName = next.name, iconUrl = next.icon, size = 52.dp)
                } else {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (returning) R.string.triage_back_to else R.string.triage_next_up),
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
                    text =
                        if (returning) pluralStringResource(R.plurals.triage_left, next.unread, next.unread)
                        else stringResource(R.string.triage_next_unread, next.unread),
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
/**
 * Auto-advance joined to the quiet pill of Share and Undo, like the buttons on the other screens.
 * Turned on, its segment moves away from the pill and becomes a circle with the countdown ring
 * (the pill's first edge rounding off at the same time); turned off, it goes back. One shape that
 * moves, so the row never jumps.
 */
@Composable
private fun TriageTopButtons(
    running: Boolean,
    paused: Boolean,
    progress: () -> Float,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    others: List<TopBarAction>,
) {
    val haptic = LocalHapticFeedback.current
    // 0: a segment of the pill, 1: its own circle.
    val detach by animateFloatAsState(
        if (running) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "auto detach",
    )
    val t = detach.coerceIn(0f, 1f)
    // While running it looks and moves like read aloud's button: the same wavy ring (still while
    // paused) with the pause sign inside, no filled button. Off, it's a segment of the pill.
    val ringColor = if (paused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    val container = lerp(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f), t)
    val content = lerp(MaterialTheme.colorScheme.onSurface, ringColor, t)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // From a pill segment (48 x 40, round outer edge, flat inner one) to a 40dp circle.
        val lead = androidx.compose.ui.unit.lerp(20.dp, 20.dp, t)
        val trail = androidx.compose.ui.unit.lerp(6.dp, 20.dp, t)
        Box(
            modifier =
                Modifier.size(width = androidx.compose.ui.unit.lerp(48.dp, 40.dp, t), height = 40.dp)
                    .clip(RoundedCornerShape(topStart = lead, bottomStart = lead, topEnd = trail, bottomEnd = trail))
                    .background(container)
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (t > 0.01f) {
                CompositionLocalProvider(LocalContentColor provides ringColor) {
                    me.ash.reader.brief.readaloud.ReadAloudRing(
                        progress = progress(),
                        playing = running && !paused,
                        modifier = Modifier.size(40.dp).graphicsLayer { alpha = t },
                    )
                }
            }
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
                tint = content,
                modifier = Modifier.size(androidx.compose.ui.unit.lerp(22.dp, 20.dp, t)),
            )
        }
        Spacer(Modifier.width(androidx.compose.ui.unit.lerp(2.dp, 6.dp, t)))
        TopBarButtonGroup(others, leadingCorner = androidx.compose.ui.unit.lerp(6.dp, 20.dp, t))
    }
}

/** The auto-advance delay, from a long press on its button: the same popup as an article's long-press. */
@Composable
private fun AutoDelayMenu(
    expanded: Boolean,
    seconds: Int,
    onSeconds: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedDropdownMenu(
        modifier = Modifier.padding(horizontal = 12.dp),
        expanded = expanded,
        onDismissRequest = onDismiss,
        // Under the buttons, lined up with their right edge.
        alignToAnchorEnd = true,
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
                        onDismiss()
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

/**
 * The groups (or feeds) of this Triage run, opened from the header: done ones are ticked, the one
 * you're in is highlighted with the cards left, the rest show their unread count. Tap one to go
 * there now (the cards left here stay unread), or skip to the next.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TriagePlacesSheet(
    places: List<TriageNext>,
    currentId: String?,
    currentLeft: Int,
    visited: Set<String>,
    leftIn: Map<String, Int>,
    isFeeds: Boolean,
    title: String?,
    skipTarget: TriageNext?,
    onPick: (TriageNext) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    // Buttons slide the sheet down first, like a swipe down does, instead of it vanishing.
    fun close(then: () -> Unit) {
        sheetScope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(if (isFeeds) R.string.triage_places_feeds else R.string.triage_places_groups),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansWideFontFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                text = stringResource(R.string.triage_jump_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(places, key = { it.id }) { place ->
                    val here = place.id == currentId
                    // A group you moved on from with cards still unread can be opened again.
                    val leftBehind = if (here) 0 else leftIn[place.id] ?: 0
                    val doneHere = !here && place.id in visited && leftBehind == 0
                    Surface(
                        onClick = { close { onPick(place) } },
                        enabled = !here && !doneHere,
                        shape = RoundedCornerShape(if (here) 20.dp else 16.dp),
                        color =
                            if (here) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            if (here) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isFeeds && !doneHere) {
                                // The feed's own icon; done ones get the tick like groups.
                                FeedIcon(feedName = place.name, iconUrl = place.icon, size = 26.dp)
                            } else {
                                Box(
                                    modifier =
                                        Modifier.size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (here || doneHere) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (doneHere) Icons.Rounded.Check else Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint =
                                            if (here || doneHere) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (doneHere) MaterialTheme.colorScheme.onSurfaceVariant
                                    else androidx.compose.ui.graphics.Color.Unspecified,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text =
                                    when {
                                        // The highlight already says it's the one you're in.
                                        here -> pluralStringResource(R.plurals.triage_left, currentLeft, currentLeft)
                                        doneHere -> stringResource(R.string.triage_place_done)
                                        // Only the group you're in says "N left"; the others just show a count.
                                        leftBehind > 0 -> leftBehind.toString()
                                        else -> place.unread.toString()
                                    },
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (here) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (skipTarget != null) {
                    FilledTonalButton(onClick = { close { onPick(skipTarget) } }, modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.triage_skip_place, title ?: ""),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar under the header. While you swipe it is this group's wavy bar. When the group is done it
 * becomes the whole run, one piece per group in the Feeds page order (as wide as the cards it had,
 * or has left for those still to come; done ones filled): the bar, by then full and straight,
 * shrinks into this group's piece while the others fade in around it. Starting the next group does
 * the reverse: its empty piece grows to the full width while the others fade out, and the wave
 * comes back as you swipe.
 */
@Composable
private fun TriageBar(
    groupProgress: () -> Float,
    showRun: Boolean,
    runPlaces: List<TriageNext>,
    sizes: Map<String, Int>,
    visited: Set<String>,
    notReached: Map<String, Int> = emptyMap(),
    hereId: String?,
) {
    // 0: this group's bar, 1: the whole run.
    val morph = remember { Animatable(if (showRun) 1f else 0f) }
    // The piece the bar shrinks into (or grows out of) is always the group you're in. It used to be
    // updated a moment late, so for a frame or two the group you just finished took the new
    // group's (empty) progress and its piece flashed empty.
    val focusId = hereId
    // Linear here: each of the two steps below gets its own smooth in-and-out curve.
    val spec = tween<Float>(durationMillis = 800, easing = LinearEasing)
    // The wave calms down before the bar turns into the run (and comes back after the reverse),
    // so it never jumps from wavy to straight.
    var calm by remember { mutableStateOf(showRun) }
    LaunchedEffect(showRun) {
        if (showRun) {
            calm = true
            // The group's bar fills up and its wave flattens out first.
            kotlinx.coroutines.delay(550)
            morph.animateTo(1f, spec)
        } else {
            morph.animateTo(0f, spec)
            calm = false
        }
    }
    val doneColor = WavyProgressIndicatorDefaults.indicatorColor
    val todoColor = WavyProgressIndicatorDefaults.trackColor
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
    if (morph.value == 0f || runPlaces.size < 2) {
        LinearWavyProgressIndicator(
            progress = groupProgress,
            modifier = modifier.padding(vertical = 12.dp),
            // Flat while calm: the indicator eases its wave in and out by itself.
            // Material's own wave, which calms down near the start and the end on purpose (the
            // bar eases into a straight line as it finishes); flat while the run is shown.
            amplitude = { p -> if (calm) 0f else WavyProgressIndicatorDefaults.indicatorAmplitude(p) },
        )
        return
    }
    Canvas(modifier = modifier.height(34.dp)) {
        val t = morph.value
        // The other groups' pieces never fade: this group's bar moves over them. Finishing a
        // group, the full bar shrinks into its piece and uncovers the run, done ones filled.
        // Starting the next one, its piece grows over the others until it fills the width, so
        // the finished groups stay in view until they're covered.
        val shrink = FastOutSlowInEasing.transform(t)
        val others = 1f
        val gap = 4.dp.toPx()
        // As thick as the wavy bar's line, so the pieces don't look heavier than it.
        val thickness = 4.dp.toPx()
        val y = (size.height - thickness) / 2
        val radius = CornerRadius(thickness / 2)
        val weights = runPlaces.map { (sizes[it.id] ?: it.unread).coerceAtLeast(1).toFloat() }
        val available = size.width - gap * (runPlaces.size - 1)
        val sum = weights.sum()
        val focus = runPlaces.indexOfFirst { it.id == focusId }
        // Where each piece sits in the run.
        val starts = FloatArray(runPlaces.size)
        val widths = FloatArray(runPlaces.size)
        var x = 0f
        runPlaces.indices.forEach { k ->
            starts[k] = x
            widths[k] = available * weights[k] / sum
            x += widths[k] + gap
        }
        // The other groups first, fading in (or out) at their places...
        runPlaces.forEachIndexed { k, place ->
            if (k == focus) return@forEachIndexed
            // Filled as far as you got: all of a finished group, part of one you jumped away from
            // (its cards not reached stay unread), none of one still to come.
            val size = (sizes[place.id] ?: place.unread).coerceAtLeast(1)
            val got =
                if (place.id !in visited) 0f
                else (1f - (notReached[place.id] ?: 0).toFloat() / size).coerceIn(0f, 1f)
            drawRoundRect(todoColor.copy(alpha = todoColor.alpha * others), Offset(starts[k], y), Size(widths[k], thickness), radius)
            if (got > 0f) {
                drawRoundRect(doneColor.copy(alpha = doneColor.alpha * others), Offset(starts[k], y), Size(widths[k] * got, thickness), radius)
            }
        }
        // ...then this group's own bar on top: from the full width to its piece, filled as far as
        // it got. Drawn last, so the others never show through it while it moves.
        if (focus >= 0) {
            val left = lerp(0f, starts[focus], shrink)
            val width = lerp(size.width, widths[focus], shrink)
            drawRoundRect(todoColor, Offset(left, y), Size(width, thickness), radius)
            val fill = groupProgress().coerceIn(0f, 1f)
            if (fill > 0f) drawRoundRect(doneColor, Offset(left, y), Size(width * fill, thickness), radius)
        }
        // No piece for this group (it isn't in the run's list): the run just fades in.
        if (focus < 0) {
            drawRoundRect(todoColor.copy(alpha = todoColor.alpha * (1f - others)), Offset(0f, y), Size(size.width, thickness), radius)
        }
    }
}

/**
 * Shares a Triage card's picture (a PNG in the app's cache, through its file provider), with the
 * article's link as text for apps that take both.
 */
private fun shareCardPicture(context: android.content.Context, picture: androidx.compose.ui.graphics.ImageBitmap, link: String?) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
        val file = java.io.File(dir, "triage-card.png")
        file.outputStream().use { picture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send =
            android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .apply { link?.takeIf { it.isNotBlank() }?.let { putExtra(android.content.Intent.EXTRA_TEXT, it) } }
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, uri)
        context.startActivity(
            android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
