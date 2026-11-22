package com.ay.briefyou.brief.app

import com.ay.briefyou.brief.reading.screenSwitch
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
import com.ay.briefyou.brief.ui.BackPreviewController
import com.ay.briefyou.brief.ui.BackPreviewPage
import com.ay.briefyou.brief.ui.FilterBarHost
import com.ay.briefyou.brief.ui.LocalBackPreviewLeavingPicture
import com.ay.briefyou.brief.ui.LocalBackPreviewPicture
import com.ay.briefyou.brief.ui.LocalBackStack
import com.ay.briefyou.brief.ui.LocalFilterBarHost
import com.ay.briefyou.brief.ui.LocalPageKey
import com.ay.briefyou.brief.ui.SharedFilterBar
import com.ay.briefyou.brief.ui.backPreviewPopTransform
import com.ay.briefyou.ui.page.nav3.key.Route

/*
 * Brief You's additions to the navigation (AppEntry), kept here so the original file only
 * needs a few calls: the back preview (Android's own predictive back animation between screens)
 * and the filter bar drawn once above every screen.
 */

/**
 * Same as Navigation 3's NavEntry, with the screen wrapped for the back preview and told its own
 * key (for the shared filter bar). The original AppEntry imports this one instead, so its screen
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
 * The transition between screens, put in front of the original with `?:` (so the original stays as the
 * fallback in its file). While the back preview runs: its transition, which draws nothing (the
 * back preview draws). Otherwise Brief You's overlapping switch ([screenSwitch]) instead of Read
 * You's shared axis, which faded the old screen out completely before the new one came in, through
 * the empty background (a dark flash). [forward] is true for opening a screen.
 */
fun backPreviewTransition(running: () -> Boolean, forward: Boolean = false, readingPage: Boolean = false): ContentTransform? =
    if (running()) backPreviewPopTransform() else screenSwitch(forward, readingPage)

/**
 * Around the app's navigation: the back preview's controller and picture, the filter bar of
 * Feeds and the article list, drawn once above every screen, so screen animations (including the
 * back gesture) never move it, and the newspaper maker shared by the article screen and lists.
 */
@Composable
fun BriefNavigationHost(backStack: NavBackStack<NavKey>, content: @Composable () -> Unit) {
    val backPreviewPicture = rememberGraphicsLayer()
    val backPreviewLeavingPicture = rememberGraphicsLayer()
    val filterBarHost = remember { FilterBarHost() }
    val newspapers = com.ay.briefyou.brief.share.rememberNewspaperMaker()
    BackPreviewController(backStack)
    BriefShortcuts.Handler(backStack)
    CompositionLocalProvider(
        LocalBackStack provides backStack,
        LocalBackPreviewPicture provides backPreviewPicture,
        LocalBackPreviewLeavingPicture provides backPreviewLeavingPicture,
        LocalFilterBarHost provides filterBarHost,
        com.ay.briefyou.brief.share.LocalNewspaperMaker provides newspapers,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            SharedFilterBar(
                host = filterBarHost,
                top = backStack.lastOrNull(),
                hasBar = { it == Route.Feeds || it is Route.Reading },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            // Takes a newspaper's picture off screen, at the screen's width.
            com.ay.briefyou.brief.share.NewspaperHost(newspapers)
        }
    }
}
