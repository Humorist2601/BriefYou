package me.ash.reader.ui.theme.palette

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import android.app.WallpaperManager
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import me.ash.reader.infrastructure.preference.LocalCustomPrimaryColor
import me.ash.reader.ui.theme.palette.dynamic.PresetColor

/*
 * Brief You: the app's colors, the way Material You makes them today.
 *
 * - With your wallpaper's colors on Android 12 and newer, the scheme is the system's own: every
 *   color role comes straight from the phone, so the app matches the rest of the system exactly
 *   (on Android 14 and newer that includes the newer surface roles).
 * - With a preset color, your own color, or a wallpaper color on older Android, the scheme is
 *   generated from that color with Google's Material Color Utilities (the Tonal Spot style that
 *   Android itself uses, on the 2025 color spec).
 *
 * The choices are in the same order as the color picker in Settings (see
 * extractTonalPalettesFromUserWallpaper), so the same theme index picks the same option.
 */

/** Where a theme's colors come from. */
sealed interface ThemeColorSource {
    /** The phone's own Material You colors (Android 12 and newer). */
    data object System : ThemeColorSource

    /** A scheme generated from one color. */
    data class Seed(val color: Color) : ThemeColorSource
}

/** The color picker's options, in its order: 4 presets, your color, then the wallpaper's. */
@Composable
fun themeColorSources(): List<ThemeColorSource> {
    val context = LocalContext.current
    val custom = LocalCustomPrimaryColor.current
    val inEditMode = LocalView.current.isInEditMode
    return remember(custom, inEditMode) {
        buildList {
            add(ThemeColorSource.Seed(PresetColor.blue))
            add(ThemeColorSource.Seed(PresetColor.pink))
            add(ThemeColorSource.Seed(PresetColor.purple))
            add(ThemeColorSource.Seed(PresetColor.yellow))
            add(ThemeColorSource.Seed(custom.safeHexToColor()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !inEditMode) {
                val colors =
                    WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                val primary = colors?.primaryColor?.toArgb()
                if (primary != null) {
                    add(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ThemeColorSource.System
                        else ThemeColorSource.Seed(Color(primary))
                    )
                }
                colors?.secondaryColor?.toArgb()?.let { add(ThemeColorSource.Seed(Color(it))) }
                colors?.tertiaryColor?.toArgb()?.let { add(ThemeColorSource.Seed(Color(it))) }
            }
        }
    }
}

/** The color scheme for [source], light or dark, with AMOLED black surfaces if asked. */
@Composable
fun materialYouColorScheme(
    source: ThemeColorSource,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme {
    val context = LocalContext.current
    return when (source) {
        ThemeColorSource.System ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) {
                    androidx.compose.material3.dynamicDarkColorScheme(context).let {
                        if (isAmoled) it.amoled() else it
                    }
                } else {
                    androidx.compose.material3.dynamicLightColorScheme(context)
                }
            } else {
                seededScheme(PresetColor.blue, isDark, isAmoled)
            }
        is ThemeColorSource.Seed -> seededScheme(source.color, isDark, isAmoled)
    }
}

private fun seededScheme(seed: Color, isDark: Boolean, isAmoled: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        isAmoled = isAmoled,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

/**
 * A scheme from an article's picture, for the reading page: Material's Content style, which keeps
 * the picture's own colour (like music apps do with album art), with the same contrast rules.
 */
fun pictureColorScheme(seed: Color, isDark: Boolean, isAmoled: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        isAmoled = isAmoled,
        style = PaletteStyle.Content,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

/** Black surfaces for AMOLED screens, keeping the containers just above black so cards show. */
private fun ColorScheme.amoled(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF0E0E0E),
        surfaceContainerHigh = Color(0xFF141414),
        surfaceContainerHighest = Color(0xFF1A1A1A),
    )

/**
 * The web view's colours on the reading page: the picture's scheme straight away (not the fading
 * one), so the article is drawn once in its final colours instead of again on every frame.
 */
val LocalWebViewColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** [target], with every colour fading to its new value over [durationMillis]. */
@Composable
fun animateColorScheme(target: ColorScheme, durationMillis: Int = 400): ColorScheme {
    val spec = tween<Color>(durationMillis)
    return target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(target.primaryContainer, spec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, spec, label = "onPrimaryContainer").value,
        inversePrimary = animateColorAsState(target.inversePrimary, spec, label = "inversePrimary").value,
        secondary = animateColorAsState(target.secondary, spec, label = "secondary").value,
        onSecondary = animateColorAsState(target.onSecondary, spec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(target.secondaryContainer, spec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, spec, label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(target.tertiary, spec, label = "tertiary").value,
        onTertiary = animateColorAsState(target.onTertiary, spec, label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(target.tertiaryContainer, spec, label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(target.onTertiaryContainer, spec, label = "onTertiaryContainer").value,
        background = animateColorAsState(target.background, spec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        surfaceTint = animateColorAsState(target.surfaceTint, spec, label = "surfaceTint").value,
        inverseSurface = animateColorAsState(target.inverseSurface, spec, label = "inverseSurface").value,
        inverseOnSurface = animateColorAsState(target.inverseOnSurface, spec, label = "inverseOnSurface").value,
        error = animateColorAsState(target.error, spec, label = "error").value,
        onError = animateColorAsState(target.onError, spec, label = "onError").value,
        errorContainer = animateColorAsState(target.errorContainer, spec, label = "errorContainer").value,
        onErrorContainer = animateColorAsState(target.onErrorContainer, spec, label = "onErrorContainer").value,
        outline = animateColorAsState(target.outline, spec, label = "outline").value,
        outlineVariant = animateColorAsState(target.outlineVariant, spec, label = "outlineVariant").value,
        scrim = animateColorAsState(target.scrim, spec, label = "scrim").value,
        surfaceBright = animateColorAsState(target.surfaceBright, spec, label = "surfaceBright").value,
        surfaceContainer = animateColorAsState(target.surfaceContainer, spec, label = "surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(target.surfaceContainerHigh, spec, label = "surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(target.surfaceContainerHighest, spec, label = "surfaceContainerHighest").value,
        surfaceContainerLow = animateColorAsState(target.surfaceContainerLow, spec, label = "surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(target.surfaceContainerLowest, spec, label = "surfaceContainerLowest").value,
        surfaceDim = animateColorAsState(target.surfaceDim, spec, label = "surfaceDim").value,
    )
}

