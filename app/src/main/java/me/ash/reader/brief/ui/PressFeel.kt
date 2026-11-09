package me.ash.reader.brief.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * The press feel of Triage's Next card and buttons, for any tile: while held it squeezes a little,
 * its corners tighten and its cookie turns by one bump; let go, it springs back a little past its
 * size. Pass [interaction] to the tile's Surface, use [corner] for its shape, [squeeze] on the
 * tile and [turn] on the cookie (turned back the other way inside, so the icon stays upright).
 */
@Stable
class PressFeel internal constructor(
    val interaction: MutableInteractionSource,
    private val cornerState: State<Dp>,
    private val turnState: State<Float>,
    private val scaleState: State<Float>,
) {
    val corner: Dp get() = cornerState.value
    val turn: Float get() = turnState.value

    /** The squeeze, for a graphicsLayer. */
    val scale: Float get() = scaleState.value

    /** Squeezes the tile. */
    fun Modifier.squeeze(): Modifier = graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * [rest] and [held] are the tile's corners; [bumps] is how many its cookie has (0: no cookie).
 * [squeezeTo] is the size while held: 96%, or a gentler 98% for big cards.
 */
@Composable
fun rememberPressFeel(rest: Dp, held: Dp, bumps: Int = 0, squeezeTo: Float = 0.96f): PressFeel {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounce = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    val scale = animateFloatAsState(if (pressed) squeezeTo else 1f, bounce, label = "press size")
    val corner =
        animateDpAsState(
            if (pressed) held else rest,
            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
            label = "press corner",
        )
    val turn = animateFloatAsState(if (pressed && bumps > 0) 360f / bumps else 0f, bounce, label = "press turn")
    return remember(interaction) { PressFeel(interaction, corner, turn, scale) }
}
