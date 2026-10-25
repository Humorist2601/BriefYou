package me.ash.reader.brief.reading

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneMotion
import androidx.compose.material3.adaptive.layout.PaneScaffoldMotionDataProvider
import androidx.compose.material3.adaptive.layout.PaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculateDefaultEnterTransition
import androidx.compose.material3.adaptive.layout.calculateDefaultExitTransition

/*
 * Brief You: going between the article list and an article on a phone (opening an article, and
 * going back, also while the back gesture follows your finger).
 *
 * Read You uses Material's "shared axis" here: the old screen fades out completely before the new
 * one fades in, so for a moment only the empty background shows: a dark flash (a white one in
 * light theme). Here the new screen fades in first while the old one stays, and only then does the
 * old one fade out. Whichever of the two is drawn on top, one of them always covers the screen. Both
 * still slide a little where they slid before.
 *
 * On a phone (one pane) every switch uses this, whatever motion the scaffold picks for it (it
 * doesn't always say "from the right"; then Read You's plain fades also went through the
 * background). With two panes side by side the scaffold's own motions stay.
 */

private const val DURATION = 300
private const val HALF = DURATION / 2
private const val OFFSET_FACTOR = 0.10f
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun <Role : PaneScaffoldRole> PaneScaffoldMotionDataProvider<Role>.briefEnterTransition(
    role: Role,
    singlePane: Boolean,
): EnterTransition {
    val motion = this[role].motion
    return when {
        motion == PaneMotion.EnterFromRight -> enter { (it * OFFSET_FACTOR).toInt() }
        motion == PaneMotion.EnterFromLeft -> enter { -(it * OFFSET_FACTOR).toInt() }
        singlePane && motion != PaneMotion.NoMotion && motion != PaneMotion.AnimateBounds ->
            enter { 0 }
        else -> calculateDefaultEnterTransition(role)
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun <Role : PaneScaffoldRole> PaneScaffoldMotionDataProvider<Role>.briefExitTransition(
    role: Role,
    singlePane: Boolean,
): ExitTransition {
    val motion = this[role].motion
    return when {
        motion == PaneMotion.ExitToLeft -> exit { -(it * OFFSET_FACTOR).toInt() }
        motion == PaneMotion.ExitToRight -> exit { (it * OFFSET_FACTOR).toInt() }
        singlePane && motion != PaneMotion.NoMotion && motion != PaneMotion.AnimateBounds ->
            exit { 0 }
        else -> calculateDefaultExitTransition(role)
    }
}

/**
 * Brief You's switch between whole screens (opening Settings, a list, going back with a button):
 * the same overlap, so it never passes through the empty background either. [forward] slides the
 * new screen in from the right; going back, from the left.
 */
fun screenSwitch(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    return enter { sign * (it * OFFSET_FACTOR).toInt() } togetherWith
        exit { -sign * (it * OFFSET_FACTOR).toInt() }
}

/** Slides in, and is fully there halfway through. */
private fun enter(offset: (Int) -> Int): EnterTransition =
    slideInHorizontally(tween(DURATION, easing = Emphasized), offset) +
        fadeIn(tween(HALF, easing = LinearEasing))

/** Slides out, and fades only in the second half, once the new screen is fully there. */
private fun exit(offset: (Int) -> Int): ExitTransition =
    slideOutHorizontally(tween(DURATION, easing = Emphasized), offset) +
        fadeOut(tween(HALF, delayMillis = HALF, easing = LinearEasing))
