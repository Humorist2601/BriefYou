package me.ash.reader.brief.ui

// Brief You's copy of Read You's filter bar (ui/component/FilterBar.kt), which it rewrote: a
// taller, floating bar with rounded corners and a fade behind it. Read You's original stays
// untouched; see .github/fork/copies.txt.

import android.os.Build
import android.view.SoundEffectConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.infrastructure.preference.LocalThemeIndex
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.onDark

/** A tall bar, like a music player's. Taller than the stock navigation bar. */
fun filterBarContainerHeight(filterBarStyle: Int): Dp =
    when (filterBarStyle) {
        FlowFilterBarStylePreference.Icon.value -> 76.dp
        else -> 88.dp
    }

/**
 * The gap under everything that floats at the bottom of the screen: the reading page's pill (above
 * the system gesture bar), and the article list's AI gist / Triage button and pull-up hint (above
 * the bottom bar).
 */
val FloatingBarGap = 12.dp

@Composable
fun BriefFilterBar(
    modifier: Modifier = Modifier,
    filter: Filter,
    filterBarStyle: Int,
    filterBarFilled: Boolean,
    filterBarPadding: Dp,
    filterBarTonalElevation: Dp,
    filterOnClick: (Filter) -> Unit = {},
) {
    val view = LocalView.current
    val themeIndex = LocalThemeIndex.current
    val indicatorColor = if (themeIndex == 5 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    } onDark MaterialTheme.colorScheme.secondaryContainer

    val containerHeight = filterBarContainerHeight(filterBarStyle)

    // Brief You: a floating bar with rounded corners (not a full-width strip, and not a pill). It
    // sits right on top of the system gesture area, with side margins and a little room above.
    // Frosted glass: the list scrolls on underneath it and shows through, strongly blurred, under
    // a light tint of the bar's colour (Android 12 and newer; older phones get the tint, nearly
    // solid).
    val barColor = MaterialTheme.colorScheme.surfaceColorAtElevation(filterBarTonalElevation)
    val backdrop = LocalBarBackdrop.current
    val glass =
        remember(barColor) {
            dev.chrisbanes.haze.blur.HazeBlurStyle {
                blurRadius(28.dp)
                noiseFactor(0f)
                backgroundColor(barColor)
                colorEffects(listOf(dev.chrisbanes.haze.blur.HazeColorEffect.tint(barColor.copy(alpha = 0.55f))))
                fallbackColorEffect(dev.chrisbanes.haze.blur.HazeColorEffect.tint(barColor.copy(alpha = 0.94f)))
            }
        }
    Box(
        modifier =
            modifier
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(start = 20.dp, end = 20.dp, top = FILTER_BAR_TOP_PADDING)
    ) {
    Surface(
        color = if (backdrop != null) Color.Transparent else barColor,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (backdrop != null)
                        Modifier.hazeBlur(dev.chrisbanes.haze.HazeInput.Sources(backdrop), glass)
                    else Modifier
                )
                .defaultMinSize(minHeight = containerHeight)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Spacer(modifier = Modifier.width(filterBarPadding))
            Filter.values.forEach { item ->
                NavigationBarItem(
                    modifier = Modifier.height(containerHeight),
                    alwaysShowLabel = when (filterBarStyle) {
                        FlowFilterBarStylePreference.Icon.value -> false
                        FlowFilterBarStylePreference.IconLabel.value -> true
                        FlowFilterBarStylePreference.IconLabelOnlySelected.value -> false
                        else -> false
                    },
                    icon = {
                        Icon(
                            imageVector = if (filter == item && filterBarFilled) {
                                item.iconFilled
                            } else {
                                item.iconOutline
                            },
                            contentDescription = item.toName()
                        )
                    },
                    label = if (filterBarStyle == FlowFilterBarStylePreference.Icon.value) {
                        null
                    } else {
                        {
                            Text(
                                text = item.toName(),
//                            style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    selected = filter == item,
                    onClick = {
//                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        filterOnClick(item)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = indicatorColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedIconColor = MaterialTheme.colorScheme.contentColorFor(indicatorColor),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            Spacer(modifier = Modifier.width(filterBarPadding))
        }
    }
    } // Brief You
}
