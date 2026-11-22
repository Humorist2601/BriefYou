package me.ash.reader.brief.ui

import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import me.ash.reader.domain.model.general.Filter

/** The height of everything in the bottom bar: the filter toolbar and the AI button. */
val BottomBarHeight = 64.dp

/**
 * The gap under everything that floats at the bottom of the screen: the reading page's pill (above
 * the system gesture bar), and the pull-up hint and the "now playing" pill (above the bottom bar).
 */
val FloatingBarGap = 12.dp

/**
 * The Starred / Unread / All toolbar: a frosted glass pill (the list shows through it, strongly
 * blurred, on Android 12 and newer; older phones get the same colour, nearly solid). The chosen
 * filter is a filled accent pill with its name; the others are just icons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BriefFilterBar(
    modifier: Modifier = Modifier,
    filter: Filter,
    filterOnClick: (Filter) -> Unit = {},
) {
    val view = LocalView.current
    Row(
        modifier = modifier.glass(CircleShape).height(BottomBarHeight).padding(horizontal = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Filter.values.forEach { item ->
            FilterButton(item = item, selected = filter == item) {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                filterOnClick(item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterButton(item: Filter, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(if (selected) colors.primary else colors.primary.copy(alpha = 0f), effects, label = "filter")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, effects, label = "filter icon")
    val name = item.toName()
    Row(
        modifier =
            Modifier.height(48.dp)
                .clip(CircleShape)
                .background(container)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) item.iconFilled else item.iconOutline,
            contentDescription = if (selected) null else name,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Text(
                text = name,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            )
        }
    }
}

/**
 * Frosted glass for the bottom bar: the screens behind it, strongly blurred under a light tint,
 * a thin bright edge and a faint lift (no dark shadow, which looks dirty through glass).
 */
@Composable
internal fun Modifier.glass(shape: Shape): Modifier {
    val tintColor = MaterialTheme.colorScheme.surfaceContainer
    val dark = tintColor.luminance() < 0.5f
    val backdrop = LocalBarBackdrop.current
    val style =
        remember(tintColor) {
            HazeBlurStyle {
                blurRadius(28.dp)
                noiseFactor(0f)
                backgroundColor(tintColor)
                colorEffects(listOf(HazeColorEffect.tint(tintColor.copy(alpha = 0.6f))))
                fallbackColorEffect(HazeColorEffect.tint(tintColor.copy(alpha = 0.95f)))
            }
        }
    return this
        .dropShadow(shape, Shadow(radius = 16.dp, color = Color.Black, offset = DpOffset(0.dp, 4.dp), alpha = if (dark) 0.22f else 0.10f))
        .clip(shape)
        .then(
            if (backdrop != null) Modifier.hazeBlur(HazeInput.Sources(backdrop), style)
            else Modifier.background(tintColor)
        )
        .border(1.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.6f), shape)
}
