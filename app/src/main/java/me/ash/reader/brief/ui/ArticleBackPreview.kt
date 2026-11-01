package me.ash.reader.brief.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * The back gesture from an article to its list on a phone, the same as every other screen's (see
 * BackPreview): the article shrinks into a card that follows your finger, the list waits behind
 * it, and when you let go the card fades out as the list grows back.
 *
 * The article and its list are two panes of one screen (Read You's list-and-article scaffold),
 * not two screens of the navigation host. The scaffold follows the gesture itself (it shows both
 * panes while you swipe and goes back when you let go); its own pane animations are made to draw
 * nothing during the gesture (see PaneTransitions), and the two panes are drawn by the back
 * preview, exactly like two screens.
 */

/** The article pane and the list pane, as the back preview knows them. */
internal data object ArticleDetailPane

internal data object ArticleListPane

/**
 * The app's own back dispatcher (the one the back preview follows), for the article's panes deep
 * inside a screen. Provided by the app's navigation.
 */
val LocalBackDispatcher = compositionLocalOf<NavigationEventDispatcher?> { null }

/**
 * True while the back gesture of an article (or its settling) runs: the scaffold's pane
 * animations then draw nothing. [active] says whether a gesture now would be the article's (a
 * phone, with an article open).
 */
@Composable
fun rememberArticleBackRunning(active: () -> Boolean): () -> Boolean {
    val dispatcher = LocalBackDispatcher.current
    val isActive by rememberUpdatedState(active)
    return remember(dispatcher) {
        {
            val s = BackPreviewState
            (s.phase != BackPhase.Idle && s.owner == BackOwner.Article) ||
                (isActive() &&
                    dispatcher?.transitionState?.value is NavigationEventTransitionState.InProgress)
        }
    }
}

/**
 * Follows the back gesture from an article to its list. [active] says whether the scaffold is
 * following a back gesture right now (a phone, an article open, and the gesture really going to
 * the scaffold: not to the AI sheet, the picture viewer, a menu or anything else open on top);
 * [articleShown] whether the scaffold shows an article.
 *
 * Edge cases, each handled on purpose:
 * - From either edge: the card moves like the other screens' (from the right it stays centred).
 * - Cancelled: everything runs back; the list the scaffold still shows for a moment stays hidden.
 * - Another article opened while the card fades out: the animation stops at once and the new
 *   article is shown normally (the old one never stays hidden over it).
 * - A second back while the first still settles: the first finishes at once (see collectLatest),
 *   and the second is the navigation's, as the list is then showing.
 * - The screen goes away mid-gesture (rotating, unfolding, leaving the app): the gesture is reset,
 *   so the next one starts clean.
 */
@Composable
fun ArticleBackController(active: () -> Boolean, articleShown: () -> Boolean) {
    val dispatcher = LocalBackDispatcher.current ?: return
    val isActive by rememberUpdatedState(active)
    val shown by rememberUpdatedState(articleShown)
    // A pane hidden after a gesture comes back as soon as it is shown again.
    LaunchedEffect(Unit) {
        snapshotFlow { shown() }
            .collectLatest { article ->
                BackPreviewState.collapsedKeys.remove(if (article) ArticleDetailPane else ArticleListPane)
            }
    }
    // Leaving mid-gesture never leaves the back preview stuck.
    DisposableEffect(Unit) {
        onDispose {
            val s = BackPreviewState
            if (s.owner == BackOwner.Article) s.reset()
            s.collapsedKeys.remove(ArticleDetailPane)
            s.collapsedKeys.remove(ArticleListPane)
        }
    }
    LaunchedEffect(dispatcher) {
        val s = BackPreviewState
        dispatcher.transitionState.collectLatest { gesture ->
            if (gesture is NavigationEventTransitionState.InProgress) {
                val event = gesture.latestEvent
                if (s.phase == BackPhase.Idle) {
                    // Checked on every move until the scaffold follows the gesture (it may start
                    // a frame after the gesture does), and never when something else takes it.
                    if (!isActive()) return@collectLatest
                    s.collapsedKeys.remove(ArticleDetailPane)
                    s.collapsedKeys.remove(ArticleListPane)
                    s.leavingKey = ArticleDetailPane
                    s.behindKey = ArticleListPane
                    s.fromRight = event.swipeEdge == NavigationEvent.EDGE_RIGHT
                    s.startY = event.touchY
                    s.settle = 0f
                    s.progress = event.progress
                    s.rawDy = 0f
                    s.owner = BackOwner.Article
                    s.phase = BackPhase.Dragging
                }
                if (s.owner != BackOwner.Article) return@collectLatest
                s.progress = event.progress
                s.rawDy = event.touchY - s.startY
            } else if (s.phase == BackPhase.Dragging && s.owner == BackOwner.Article) {
                s.releasedAt = s.progress
                // As if it goes back straight away (this looks exactly like the last frame of the
                // swipe), so the list is drawn even if the scaffold drops the article at once.
                s.settle = 0f
                s.phase = BackPhase.Committing
                var leftOver: Any? = null
                try {
                    // Going back hides the article; give the scaffold a few frames.
                    var committed = !shown()
                    var frames = 0
                    while (!committed && frames < 6) {
                        withFrameNanos {}
                        frames++
                        committed = !shown()
                    }
                    if (committed) {
                        leftOver = ArticleDetailPane
                        // Stops at once if another article is opened meanwhile.
                        coroutineScope {
                            val fade = launch {
                                animate(0f, 1f, animationSpec = tween(COMMIT_DURATION, easing = LinearEasing)) {
                                    value,
                                    _ ->
                                    s.settle = value
                                }
                            }
                            val reopened = launch {
                                snapshotFlow { shown() }.first { it }
                                leftOver = null
                                fade.cancel()
                            }
                            fade.join()
                            reopened.cancel()
                        }
                    } else {
                        leftOver = ArticleListPane
                        s.phase = BackPhase.Cancelling
                        animate(
                            s.progress,
                            0f,
                            animationSpec = tween(CANCEL_DURATION, easing = FastOutSlowInEasing),
                        ) { value, _ ->
                            s.progress = value
                        }
                    }
                } finally {
                    // The pane the scaffold still shows for a moment (the article you left, or
                    // the list after a cancel) stays hidden until it is gone or shown again; never
                    // an article that is showing again.
                    val hide = leftOver
                    if (hide != null && !(hide == ArticleDetailPane && shown())) {
                        s.collapsedKeys.add(hide)
                    }
                    s.reset()
                }
            }
        }
    }
}

/** One of the article's panes ([key]: [ArticleDetailPane] or [ArticleListPane]), drawn by the back preview. */
@Composable
fun ArticleBackPane(key: Any, content: @Composable () -> Unit) {
    DisposableEffect(key) { onDispose { BackPreviewState.collapsedKeys.remove(key) } }
    BackPreviewLayer(
        key = key,
        collapsedWhenIdle = key in BackPreviewState.collapsedKeys,
        content = content,
    )
}
