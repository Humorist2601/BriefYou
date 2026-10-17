@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.feeds.sort

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.ash.reader.R
import me.ash.reader.domain.service.FeedOrdering
import me.ash.reader.domain.service.OrderSettings
import me.ash.reader.domain.service.SortMode

/** How groups and feeds are sorted, for the sort sheet on the Feeds page. */
@HiltViewModel
class FeedSortViewModel @Inject constructor(private val feedOrdering: FeedOrdering) : ViewModel() {

    val settings: StateFlow<OrderSettings> =
        feedOrdering.version
            .map { feedOrdering.settings() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, feedOrdering.settings())

    fun setGroupMode(mode: SortMode) = feedOrdering.update { it.copy(groupMode = mode) }

    fun setFeedMode(mode: SortMode) = feedOrdering.update { it.copy(feedMode = mode) }
}

/** The three ways to sort in the sheet; Name and Unread each have two directions. */
private enum class SortKind(val icon: ImageVector, val label: Int) {
    Default(Icons.Rounded.History, R.string.sort_default),
    Name(Icons.Rounded.Title, R.string.sort_name),
    Count(Icons.Rounded.BarChart, R.string.sort_count);

    companion object {
        fun of(mode: SortMode): SortKind =
            when (mode.normalized) {
                SortMode.NameAsc,
                SortMode.NameDesc -> Name
                SortMode.MostUnread,
                SortMode.FewestUnread -> Count
                else -> Default
            }
    }
}

/**
 * The sort sheet, in the style of Material 3 Expressive pickers: for groups and for the feeds inside
 * them, three big tonal tiles (Default, Name, Unread), the chosen one filled with the accent
 * color and its corners morphing rounder, and under Name or Unread a connected button for the
 * direction. Changes apply to the list behind the sheet right away. Default is the order the groups
 * and feeds were added in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSortSheet(
    visible: Boolean,
    settings: OrderSettings,
    onDismiss: () -> Unit,
    onGroupMode: (SortMode) -> Unit,
    onFeedMode: (SortMode) -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier.padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.sort_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))

            SortSection(stringResource(R.string.sort_groups), settings.groupMode, onGroupMode)
            Spacer(Modifier.height(18.dp))
            SortSection(stringResource(R.string.sort_feeds), settings.feedMode, onFeedMode)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSection(title: String, selected: SortMode, onSelect: (SortMode) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val kind = SortKind.of(selected)
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SortKind.entries.forEach { tile ->
            SortTile(
                kind = tile,
                selected = tile == kind,
                onClick = {
                    if (tile != kind) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(
                            when (tile) {
                                SortKind.Default -> SortMode.Default
                                SortKind.Name -> SortMode.NameAsc
                                SortKind.Count -> SortMode.MostUnread
                            }
                        )
                    }
                },
            )
        }
    }
    // The direction, for Name and Unread: a connected two-part button that slides in under them.
    AnimatedVisibility(
        visible = kind != SortKind.Default,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val options =
            if (kind == SortKind.Count) {
                listOf(
                    Triple(SortMode.MostUnread, Icons.Rounded.KeyboardArrowDown, R.string.sort_most_first),
                    Triple(SortMode.FewestUnread, Icons.Rounded.KeyboardArrowUp, R.string.sort_fewest_first),
                )
            } else {
                listOf(
                    Triple(SortMode.NameAsc, Icons.Rounded.KeyboardArrowUp, R.string.sort_name_asc),
                    Triple(SortMode.NameDesc, Icons.Rounded.KeyboardArrowDown, R.string.sort_name_desc),
                )
            }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            options.forEachIndexed { index, (mode, icon, label) ->
                SegmentedButton(
                    selected = selected.normalized == mode,
                    onClick = {
                        if (selected.normalized != mode) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(mode)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.height(40.dp),
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** A big rounded-square tile with an icon, its label under it. The chosen one is filled and becomes a circle. */
@Composable
private fun SortTile(kind: SortKind, selected: Boolean, onClick: () -> Unit) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    // 30 dp is half the tile's size: the chosen tile morphs into a circle.
    val corner by animateDpAsState(if (selected) 30.dp else 16.dp, spatial, label = "tileCorner")
    val container by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            effects,
            label = "tileColor",
        )
    val content by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            effects,
            label = "tileContent",
        )
    val labelColor by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            effects,
            label = "tileLabel",
        )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.size(60.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(container)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                kind.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(kind.label),
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
        )
    }
}
