package me.ash.reader.ui.page.adaptive

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneMotion
import androidx.compose.material3.adaptive.layout.PaneScaffoldMotionDataProvider
import androidx.compose.material3.adaptive.layout.PaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculateDefaultEnterTransition
import androidx.compose.material3.adaptive.layout.calculateDefaultExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import me.ash.reader.ui.motion.DEPTH_DURATION
import me.ash.reader.ui.motion.depthEnterBack
import me.ash.reader.ui.motion.depthEnterForward
import me.ash.reader.ui.motion.depthExitBack
import me.ash.reader.ui.motion.depthExitForward

// Opening and closing an article uses the app's "depth slide" (see DepthTransitions.kt): the article
// comes in from halfway across and grows into place; closing it slides it away as a shrinking card.

@ExperimentalMaterial3AdaptiveApi
internal fun <Role : PaneScaffoldRole> PaneScaffoldMotionDataProvider<Role>
    .calculateEnterTransition(role: Role) =
    when (this[role].motion) {
        PaneMotion.EnterFromRight -> depthEnterForward()
        // The scaffold draws the list above the article while closing it, so the list fades in
        // over the article as it leaves, instead of covering it at once.
        PaneMotion.EnterFromLeft ->
            depthEnterBack() + fadeIn(tween(DEPTH_DURATION, easing = LinearEasing))
        else -> calculateDefaultEnterTransition(role)
    }

@ExperimentalMaterial3AdaptiveApi
internal fun <Role : PaneScaffoldRole> PaneScaffoldMotionDataProvider<Role>.calculateExitTransition(
    role: Role
) =
    when (this[role].motion) {
        PaneMotion.ExitToLeft -> depthExitForward()
        PaneMotion.ExitToRight -> depthExitBack()
        else -> calculateDefaultExitTransition(role)
    }
