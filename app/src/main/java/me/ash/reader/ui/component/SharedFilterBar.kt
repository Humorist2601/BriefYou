@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter

/*
 * The Unread / Starred / All bar of Feeds and the article list, drawn once for the whole app.
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
    val style: Int,
    val padding: Dp,
    val tonalElevation: Dp,
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
    SideEffect { if (host.specs[key] != spec) host.specs[key] = spec }
    DisposableEffect(host, key) { onDispose { host.specs.remove(key) } }
}

/** The height the bar takes at the bottom of a screen, for that screen's own layout. */
@Composable
fun FilterBarSpace(style: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(filterBarContainerHeight(style) + FILTER_BAR_TOP_PADDING))
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
    // showing what it showed, so going from Feeds to the article list doesn't make it blink.
    val last = remember { arrayOfNulls<FilterBarSpec>(1) }
    if (spec != null) last[0] = spec
    val shown = spec ?: last[0]
    val visible = hasBar(top) && shown != null && (spec?.enabled ?: true)

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(spatial) { it } + fadeIn(effects),
        exit = slideOutVertically(spatial) { it } + fadeOut(effects),
    ) {
        val s = shown ?: return@AnimatedVisibility
        FilterBar(
            filter = s.filter,
            filterBarStyle = s.style,
            filterBarFilled = true,
            filterBarPadding = s.padding,
            filterBarTonalElevation = s.tonalElevation,
            filterOnClick = { s.onClick(it) },
        )
    }
}

internal val FILTER_BAR_TOP_PADDING = 8.dp
