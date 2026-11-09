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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip

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
    private val progressState: State<Float>,
) {
    val corner: Dp get() = cornerState.value
    val turn: Float get() = turnState.value

    /** 0 at rest, 1 while held (springing a little past on the way back): for anything else that moves with the press. */
    val progress: Float get() = progressState.value

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
    val progress = animateFloatAsState(if (pressed) 1f else 0f, bounce, label = "press progress")
    return remember(interaction) { PressFeel(interaction, corner, turn, scale, progress) }
}

/** The press of the card around this (a banner), for its cookie and arrow. */
val LocalPressFeel = androidx.compose.runtime.compositionLocalOf<PressFeel?> { null }

/**
 * A banner's icon on the Filters tiles' cookie (9 bumps, a soft tint of the card's text): it
 * turns by one bump while the banner is held, the icon staying upright.
 */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
fun BannerCookie(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    val press = LocalPressFeel.current
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier.size(40.dp)
                .graphicsLayer { rotationZ = press?.turn ?: 0f }
                .clip(androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape())
                .background(tint.copy(alpha = 0.12f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -(press?.turn ?: 0f) },
        )
    }
}

/** A banner's arrow, nudged 4 dp toward where it goes while the banner is held. */
fun Modifier.pressNudge(): Modifier = composed {
    val press = LocalPressFeel.current
    graphicsLayer { translationX = (press?.progress ?: 0f) * 4.dp.toPx() }
}

/**
 * The colour of banners (the Starred / Unread / All openers, Settings' banners): Read You's light
 * lavender in light mode; in dark mode the tinted container colour of the app's other cards, so
 * the banner isn't the brightest thing on a dark page.
 */
@androidx.compose.runtime.Composable
fun bannerContainer(): androidx.compose.ui.graphics.Color =
    if (me.ash.reader.infrastructure.preference.LocalDarkTheme.current.isDarkTheme()) {
        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.primaryFixed
    }
