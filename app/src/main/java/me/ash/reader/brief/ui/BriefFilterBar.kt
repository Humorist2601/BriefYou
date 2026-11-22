package me.ash.reader.brief.ui

import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter

/** The height of everything in the bottom bar: the filter toolbar and the AI button. */
val BottomBarHeight = 64.dp

/**
 * The gap under everything that floats at the bottom of the screen: the reading page's pill (above
 * the system gesture bar), and the pull-up hint and the "now playing" pill (above the bottom bar).
 */
val FloatingBarGap = 12.dp

/**
 * The Starred / Unread / All toolbar: a solid tonal pill like Material's floating toolbar. The chosen
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
    // The chosen filter has the colour of the Feeds page's Unread card (and of the AI button).
    val banner = bannerContainer()
    val container by animateColorAsState(if (selected) banner else banner.copy(alpha = 0f), effects, label = "filter")
    val content by animateColorAsState(if (selected) colors.contentColorFor(banner) else colors.onSurfaceVariant, effects, label = "filter icon")
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
 * The bottom bar's surface, like Material's floating toolbar: a solid tonal container
 * (`surfaceContainer`) with a soft shadow, and no outline.
 */
@Composable
internal fun Modifier.glass(shape: Shape): Modifier {
    val container = MaterialTheme.colorScheme.surfaceContainer
    return this
        .barShadow(shape)
        .clip(shape)
        .background(container)
}

/**
 * The shadow under the bottom bar and the AI button: Material's standard one at level 3 (6 dp),
 * the elevation of a floating action button, so its look follows the platform.
 */
internal fun Modifier.barShadow(shape: Shape): Modifier = this.shadow(elevation = 6.dp, shape = shape)
