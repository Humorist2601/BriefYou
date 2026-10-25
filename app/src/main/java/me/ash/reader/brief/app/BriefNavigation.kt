package me.ash.reader.brief.app

import me.ash.reader.brief.reading.screenSwitch
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import me.ash.reader.brief.ui.BackPreviewController
import me.ash.reader.brief.ui.BackPreviewPage
import me.ash.reader.brief.ui.FilterBarHost
import me.ash.reader.brief.ui.LocalBackPreviewLeavingPicture
import me.ash.reader.brief.ui.LocalBackPreviewPicture
import me.ash.reader.brief.ui.LocalBackStack
import me.ash.reader.brief.ui.LocalFilterBarHost
import me.ash.reader.brief.ui.LocalPageKey
import me.ash.reader.brief.ui.SharedFilterBar
import me.ash.reader.brief.ui.backPreviewPopTransform
import me.ash.reader.ui.page.nav3.key.Route

/*
 * Brief You's additions to Read You's navigation (AppEntry), kept here so Read You's file only
 * needs a few calls: the back preview (Android's own predictive back animation between screens)
 * and the filter bar drawn once above every screen.
 */

/**
 * Same as Navigation 3's NavEntry, with the screen wrapped for the back preview and told its own
 * key (for the shared filter bar). Read You's AppEntry imports this one instead, so its screen
 * list stays exactly as it is.
 */
fun NavEntry(
    key: NavKey,
    content: @Composable (NavKey) -> Unit,
): androidx.navigation3.runtime.NavEntry<NavKey> =
    androidx.navigation3.runtime.NavEntry(key) { entryKey ->
        BackPreviewPage(entryKey) {
            CompositionLocalProvider(LocalPageKey provides entryKey) { content(entryKey) }
        }
    }

/**
 * The transition between screens, put in front of Read You's with `?:` (so Read You's stays as the
 * fallback in its file). While the back preview runs: its transition, which draws nothing (the
 * back preview draws). Otherwise Brief You's overlapping switch ([screenSwitch]) instead of Read
 * You's shared axis, which faded the old screen out completely before the new one came in, through
 * the empty background (a dark flash). [forward] is true for opening a screen.
 */
fun backPreviewTransition(running: () -> Boolean, forward: Boolean = false): ContentTransform? =
    if (running()) backPreviewPopTransform() else screenSwitch(forward)

/**
 * Around the app's navigation: the back preview's controller and picture, and the filter bar of
 * Feeds and the article list, drawn once above every screen, so screen animations (including the
 * back gesture) never move it.
 */
@Composable
fun BriefNavigationHost(backStack: NavBackStack<NavKey>, content: @Composable () -> Unit) {
    val backPreviewPicture = rememberGraphicsLayer()
    val backPreviewLeavingPicture = rememberGraphicsLayer()
    val filterBarHost = remember { FilterBarHost() }
    BackPreviewController(backStack)
    BriefShortcuts.Handler(backStack)
    CompositionLocalProvider(
        LocalBackStack provides backStack,
        LocalBackPreviewPicture provides backPreviewPicture,
        LocalBackPreviewLeavingPicture provides backPreviewLeavingPicture,
        LocalFilterBarHost provides filterBarHost,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            SharedFilterBar(
                host = filterBarHost,
                top = backStack.lastOrNull(),
                hasBar = { it == Route.Feeds || it is Route.Reading },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
