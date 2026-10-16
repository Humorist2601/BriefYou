@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.flow

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Precision
import coil.size.Scale
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.ai.SavedKind
import me.ash.reader.infrastructure.ai.SummaryStoreEntryPoint
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000
import me.ash.reader.ui.component.rememberFeedAccent
import me.ash.reader.ui.motion.MotionConstants
import me.ash.reader.ui.theme.GoogleSansWideFontFamily

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
    onBackProgress: (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var exit by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    // True while the back gesture (or the back key) is closing Triage: it then moves exactly like
    // the app's other pages do on back (the Material shared axis transition). Closing with a button
    // keeps the shrink-and-fade towards the center.
    var sharedAxis by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }

    // Tells the list underneath how far back we are, so it can come in the way a page does.
    LaunchedEffect(Unit) {
        snapshotFlow { if (sharedAxis) backProgress else 0f }.collect { onBackProgress(it) }
    }
    DisposableEffect(Unit) { onDispose { onBackProgress(0f) } }

    // Finishes the exit and only then removes Triage, so it never vanishes in a single frame.
    suspend fun playExit(axis: Boolean) {
        if (closing) return
        closing = true
        sharedAxis = axis
        if (axis) {
            // The rest of the page transition: the same 300 ms the app uses, from where you are.
            val remaining =
                (MotionConstants.DefaultMotionDuration * (1f - backProgress)).toInt().coerceAtLeast(60)
            animate(backProgress, 1f, animationSpec = tween(remaining, easing = LinearEasing)) { v, _ ->
                backProgress = v
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

    // Predictive back, in the app's own style: while you swipe, Triage slides to the right and fades
    // out while the list slides in from the left (see the shared axis motion). Let go to finish it;
    // drag back to cancel.
    PredictiveBackHandler(enabled = true) { progress ->
        try {
            progress.collect { event ->
                sharedAxis = true
                backProgress = event.progress
            }
            playExit(axis = true)
        } catch (e: CancellationException) {
            if (!closing) {
                scope.launch {
                    val back = (MotionConstants.DefaultMotionDuration * backProgress).toInt()
                    animate(backProgress, 0f, animationSpec = tween(back.coerceAtLeast(60), easing = LinearEasing)) {
                        value, _ ->
                        backProgress = value
                    }
                    sharedAxis = false
                }
            }
        }
    }

    Surface(
        modifier =
            Modifier.fillMaxSize().graphicsLayer {
                val e = enter.value
                if (sharedAxis) {
                    // The app's page "pop" transition: slides 10% of the width to the right with the
                    // standard easing, and is faded out within the first 35% of the way.
                    translationX = FastOutSlowInEasing.transform(backProgress) * 0.10f * size.width
                    alpha =
                        e * (1f - FastOutLinearInEasing.transform((backProgress / 0.35f).coerceIn(0f, 1f)))
                } else {
                    val scale =
                        (0.94f + 0.06f * e) * (1f - 0.12f * backProgress) * (1f - 0.06f * exit) *
                            (1f - 0.1f * exit)
                    scaleX = scale
                    scaleY = scale
                    alpha = e * (1f - 0.3f * backProgress) * (1f - exit)
                    shape = RoundedCornerShape(backProgress * 40.dp.toPx())
                    clip = backProgress > 0f
                }
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        TriageContent(
            session,
            onRead,
            onUnread,
            onStar,
            onOpen,
            onClose = { scope.launch { playExit(axis = false) } },
            findNext,
            loadNext,
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

    fun undo() {
        val step = history.removeLastOrNull() ?: return
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
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
            FilledTonalIconButton(onClick = { undo() }, enabled = history.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.triage_undo),
                )
            }
        }

        LinearWavyProgressIndicator(
            progress = { if (total == 0) 1f else index.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        )

        if (current == null) {
            TriageDone(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                            val cards = runCatching { loadNext(target) }.getOrDefault(emptyList())
                            session.startNext(target, cards)
                            loadingNext = false
                        }
                    }
                },
                onClose = onClose,
            )
        } else {
            TriageStack(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                current = current,
                next = articles.getOrNull(index + 1),
                onDecide = { decide(it) },
            )
        }
    }
}

@Composable
private fun TriageStack(
    modifier: Modifier,
    current: ArticleWithFeed,
    next: ArticleWithFeed?,
    onDecide: (TriageAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // A fresh drag state for every card.
    val dragX = remember(current.article.id) { Animatable(0f) }
    val dragY = remember(current.article.id) { Animatable(0f) }
    var busy by remember(current.article.id) { mutableStateOf(false) }

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
                            .pointerInput(current.article.id) {
                                detectDragGestures(
                                    onDragStart = {},
                                    onDragEnd = {
                                        if (!busy) {
                                            busy = true
                                            scope.launch {
                                                settle(actionFor(dragX.value, dragY.value))
                                                busy = false
                                            }
                                        }
                                    },
                                    onDragCancel = { scope.launch { settle(null) } },
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
    val summary = rememberSavedSummary(item.id)
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
                        data = item.img,
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
                if (summary != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(top = 2.dp).size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = summary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                            .background(color.copy(alpha = 0.88f * overlayAlpha)),
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
        Box(
            modifier =
                Modifier.size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.triage_summary, read, saved, skipped),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        if (next != null) {
            Button(onClick = onNext, enabled = !loadingNext) {
                Text(stringResource(R.string.triage_next_group, next.name, next.unread))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.triage_back_to_list)) }
        } else {
            Button(onClick = onClose) { Text(stringResource(R.string.triage_back_to_list)) }
        }
    }
}

/** The article's saved AI summary, if it has one; null otherwise. */
@Composable
private fun rememberSavedSummary(articleId: String): String? {
    val context = LocalContext.current
    val store = remember {
        EntryPointAccessors.fromApplication(
                context.applicationContext,
                SummaryStoreEntryPoint::class.java,
            )
            .summaryStore()
    }
    val summary by
        produceState<String?>(initialValue = null, articleId) {
            value = store.get(articleId, SavedKind.Summary)
        }
    return summary
}
