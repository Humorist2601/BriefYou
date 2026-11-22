@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.ui


import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import me.ash.reader.brief.ui.LocalBackStack
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter

/*
 * The bottom bar of Feeds and the article list, drawn once for the whole app: the Starred /
 * Unread / All toolbar and, on the Unread list, the AI button (AI Gist and Triage) beside it.
 *
 * It sits above every screen, so no screen animation can move it: going between Feeds and the
 * article list (tap, back button or back gesture) the screens move under it while it stays
 * exactly where it is. The two screens keep an empty space where it is ([FilterBarSpace]) and
 * tell it which filter is chosen and what a tap does ([ProvideFilterBar]). On every other screen
 * (settings, an open article) it slides away, and slides back when you come back.
 */

/** What a screen wants the bar to show and do. */
data class FilterBarSpec(
    val filter: Filter,
    /** The AI button beside the toolbar (AI Gist and Triage), or null for none. */
    val ai: AiActions? = null,
    /** False while the screen doesn't show the bar (for example while an article is open). */
    val enabled: Boolean = true,
    val onClick: (Filter) -> Unit,
)

/** The bar's state: what each screen asked for, by its navigation key. */
class FilterBarHost {
    internal val specs = mutableStateMapOf<Any, FilterBarSpec>()
}

val LocalFilterBarHost = staticCompositionLocalOf<FilterBarHost?> { null }

/** The navigation key of the screen being composed (provided by the app's navigation). */
val LocalPageKey = staticCompositionLocalOf<Any?> { null }

/** Called by a screen that has the bar, with what the bar should show. */
@Composable
fun ProvideFilterBar(spec: FilterBarSpec) {
    val host = LocalFilterBarHost.current ?: return
    val key = LocalPageKey.current ?: return
    val backStack = LocalBackStack.current
    val latest = rememberUpdatedState(spec)
    SideEffect { if (host.specs[key] != spec) host.specs[key] = spec }
    DisposableEffect(host, key) {
        onDispose {
            // The screen is still open but no longer shows its content (for example the article
            // list while an article is open): the bar hides. A closed screen is forgotten.
            if (key in backStack) host.specs[key] = latest.value.copy(enabled = false)
            else host.specs.remove(key)
        }
    }
}

/** The height the bar takes at the bottom of a screen, for that screen's own layout. */
@Composable
fun FilterBarSpace() {
    // The "now playing" pill sits above the bar: the screen keeps room for it too, so the list
    // and the pull-up hint move up above it.
    val pill by androidx.compose.animation.core.animateDpAsState(
        if (rememberNowPlayingShown()) NOW_PLAYING_PILL_HEIGHT + FloatingBarGap else 0.dp,
        label = "now playing space",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(pill))
        Spacer(modifier = Modifier.height(BottomBarHeight + FILTER_BAR_TOP_PADDING))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(NavigationBarDefaults.windowInsets))
    }
}

/**
 * The bar itself, put once above the navigation. [top] is the screen on top and [hasBar] says
 * whether that kind of screen has the bar.
 */
@Composable
fun SharedFilterBar(
    host: FilterBarHost,
    top: Any?,
    hasBar: (Any?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = top?.let { host.specs[it] }
    // A screen tells the bar about itself right after it first appears; until then the bar keeps
    // showing what it showed, so going from Feeds to the article list doesn't make it blink. Once
    // it has, its own wish counts: shown, or hidden (for example while an article is open).
    val last = remember { arrayOfNulls<FilterBarSpec>(1) }
    if (spec != null) last[0] = spec
    val shown = spec ?: last[0]
    val visible = hasBar(top) && shown != null && (spec?.enabled ?: true)

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val context = androidx.compose.ui.platform.LocalContext.current

    // The AI button's menu. It closes when the button goes (another screen or filter), with the
    // back button, or with a tap anywhere else (on the dimmed screen behind it).
    val ai = if (visible) shown?.ai else null
    var menuOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(ai == null) { if (ai == null) menuOpen = false }
    androidx.activity.compose.BackHandler(enabled = menuOpen) { menuOpen = false }
    val scrim by androidx.compose.animation.core.animateFloatAsState(
        if (menuOpen) 1f else 0f,
        effects,
        label = "ai menu scrim",
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (scrim > 0f) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = scrim }
                        .background(Color.Black.copy(alpha = 0.32f))
                        .pointerInput(Unit) { detectTapGestures { menuOpen = false } }
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = slideInVertically(spatial) { it } + fadeIn(effects),
            exit = slideOutVertically(spatial) { it } + fadeOut(effects),
        ) {
            val s = shown ?: return@AnimatedVisibility
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                var boxWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                var barWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                val groupExtra = with(density) { (BottomBarHeight + ToolbarToButtonGap).roundToPx() }
                // Slides up from behind the bar, and back down behind it when closed. The gap
                // under it matches the one above it (to the top of the screen's list space).
                AnimatedVisibility(
                    visible = rememberNowPlayingShown(),
                    enter = slideInVertically(spatial) { it } + fadeIn(effects),
                    exit = slideOutVertically(spatial) { it } + fadeOut(effects),
                ) {
                    // A fixed width: the widest the bar and the AI button are together (the Unread
                    // list, where the chosen filter shows its name), whichever filter is chosen.
                    WidestGroupWidth(modifier = Modifier.padding(bottom = FloatingBarGap - FILTER_BAR_TOP_PADDING)) { width ->
                        me.ash.reader.brief.player.NowPlayingPill(
                            modifier = Modifier.width(width).height(NOW_PLAYING_PILL_HEIGHT)
                        )
                    }
                }
                // Material's floating toolbar with a button: the two are one group, 8 dp apart,
                // and the group is centred on the screen. With the button the toolbar sits a
                // little left of the middle, and glides back when the button goes.
                val aiShown by androidx.compose.animation.core.animateFloatAsState(
                    if (s.ai != null) 1f else 0f,
                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "toolbar place",
                )
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                            .padding(start = 16.dp, end = 16.dp, top = FILTER_BAR_TOP_PADDING)
                            // Always the bar's height: the AI button's open menu reaches up out of
                            // it, so nothing above (the "now playing" pill) moves.
                            .height(BottomBarHeight)
                            .onSizeChanged { boxWidth = it.width }
                ) {
                    BriefFilterBar(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .onSizeChanged { barWidth = it.width }
                                .offset {
                                    val x = ((boxWidth - barWidth - aiShown * groupExtra) / 2f).toInt()
                                    IntOffset(x.coerceAtLeast(0), 0)
                                },
                        filter = s.filter,
                        filterOnClick = {
                            menuOpen = false
                            s.onClick(it)
                        },
                    )
                    // Kept while it shrinks away, so it has something to show.
                    val lastAi = remember { arrayOfNulls<AiActions>(1) }
                    if (s.ai != null) lastAi[0] = s.ai
                    // (Not the Column's own AnimatedVisibility: this one is inside a Box.)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = s.ai != null,
                        // The group's right end (the toolbar is at its left, see above), counted
                        // from the right edge, where the button would otherwise sit.
                        modifier =
                            Modifier.align(Alignment.BottomEnd).offset {
                                val barX = ((boxWidth - barWidth - groupExtra) / 2).coerceAtLeast(0)
                                IntOffset(barX + barWidth + groupExtra - boxWidth, 0)
                            },
                        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        val actions = s.ai ?: lastAi[0] ?: return@AnimatedVisibility
                        BriefAiButton(
                            actions = actions,
                            expanded = menuOpen,
                            onExpandedChange = { menuOpen = it },
                            longPress = AiLongPress.get(context),
                            modifier =
                                Modifier.wrapContentSize(Alignment.BottomEnd, unbounded = true)
                                    .offset(x = AiButtonMenuMargin, y = AiButtonMenuMargin),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gives [content] the width of the widest the bottom bar group gets: the toolbar with the longest
 * of the three filters chosen (its name is shown) plus the AI button's room. Worked out by
 * measuring the toolbar for each filter (not shown), so it follows the language and font size.
 */
@Composable
private fun WidestGroupWidth(modifier: Modifier = Modifier, content: @Composable (androidx.compose.ui.unit.Dp) -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { constraints ->
        val bars =
            Filter.values.map { filter ->
                subcompose(filter) { BriefFilterBar(filter = filter) }
                    .first()
                    .measure(androidx.compose.ui.unit.Constraints())
                    .width
            }
        val width = bars.max() + (BottomBarHeight + ToolbarToButtonGap).roundToPx()
        val placeable =
            subcompose("content") { content(width.toDp()) }
                .first()
                .measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}

/** The pill's height: the same as the bottom bar's. */
internal val NOW_PLAYING_PILL_HEIGHT = BottomBarHeight

/** Whether the pill shows: an episode or video is loaded, or read aloud is reading or paused. */
@Composable
internal fun rememberNowPlayingShown(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Only whether something is loaded: the player's state changes twice a second while it
    // plays, and read aloud's with every word, and the bar shouldn't redraw for those.
    val loaded by
        remember {
                me.ash.reader.brief.player.BriefPlayer.state
                    .map { it.now != null }
                    .distinctUntilChanged()
            }
            .collectAsState(initial = me.ash.reader.brief.player.BriefPlayer.state.value.now != null)
    val tts = remember {
        runCatching {
            dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, me.ash.reader.brief.player.BriefPlayer.Deps::class.java).tts()
        }.getOrNull()
    }
    val reading by
        remember(tts) {
                (tts?.stateFlow ?: kotlinx.coroutines.flow.flowOf(null))
                    .map {
                        it is me.ash.reader.brief.readaloud.TextToSpeechManager.State.Reading ||
                            it is me.ash.reader.brief.readaloud.TextToSpeechManager.State.Paused
                    }
                    .distinctUntilChanged()
            }
            .collectAsState(initial = false)
    return loaded || reading
}

internal val FILTER_BAR_TOP_PADDING = 8.dp

/** The gap between the toolbar and the AI button (Material's floating toolbar uses 8 dp). */
internal val ToolbarToButtonGap = 8.dp

/**
 * Put by a screen that has the bar (Feeds and the article list) where the bar is: keeps the bar's
 * space on the screen and tells the shared bar what to show. [enabled] false hides the bar, for
 * example under Triage; [ai] adds the AI button (the Unread list).
 */
@Composable
fun SharedFilterBarSlot(
    filter: Filter,
    enabled: Boolean = true,
    ai: AiActions? = null,
    filterOnClick: (Filter) -> Unit = {},
) {
    FilterBarSpace()
    ProvideFilterBar(FilterBarSpec(filter = filter, ai = ai, enabled = enabled, onClick = filterOnClick))
}
