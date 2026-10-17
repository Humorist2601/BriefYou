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
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.ash.reader.R
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.FeedOrdering
import me.ash.reader.domain.service.OrderSettings
import me.ash.reader.domain.service.SortMode
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.theme.Shape32

/** The order of groups and feeds, for the sort sheet and the reorder editor on the Feeds page. */
@HiltViewModel
class FeedSortViewModel
@Inject
constructor(
    private val feedOrdering: FeedOrdering,
    groupDao: GroupDao,
    accountService: AccountService,
) : ViewModel() {

    val settings: StateFlow<OrderSettings> =
        feedOrdering.version
            .map { feedOrdering.settings() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, feedOrdering.settings())

    /** All groups and feeds, whatever the filter, in the order they were added. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val groups: StateFlow<List<GroupWithFeed>> =
        accountService.currentAccountIdFlow
            .filterNotNull()
            .flatMapLatest { groupDao.queryAllGroupWithFeedAsFlow(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The same groups and feeds, sorted the way the sheet says (re-sorts when your order changes). */
    val ordered: StateFlow<List<GroupWithFeed>> =
        combine(groups, feedOrdering.version) { groups, _ -> feedOrdering.sort(groups) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setGroupMode(mode: SortMode) = feedOrdering.update { it.copy(groupMode = mode) }

    fun setFeedMode(mode: SortMode) = feedOrdering.update { it.copy(feedMode = mode) }

    /**
     * Dragging always changes your order, so starting it switches both groups and feeds to "Your
     * order" (the list then shows the order you are editing).
     */
    fun startReorder() =
        feedOrdering.update { it.copy(groupMode = SortMode.Default, feedMode = SortMode.Default) }

    fun moveGroups(ids: List<String>) {
        viewModelScope.launch { feedOrdering.setGroupOrder(ids) }
    }

    @Suppress("UNUSED_PARAMETER")
    fun moveFeeds(groupId: String, ids: List<String>) {
        viewModelScope.launch { feedOrdering.setFeedOrder(ids) }
    }

    /** Back to the order things were added in, sorted that way. */
    fun reset() {
        feedOrdering.update { OrderSettings() }
        viewModelScope.launch { feedOrdering.clearOrder() }
    }
}

/** The three ways to sort in the sheet; Name and Unread each have two directions. */
private enum class SortKind(val icon: ImageVector, val label: Int) {
    Yours(Icons.Rounded.Reorder, R.string.sort_default),
    Name(Icons.Rounded.Title, R.string.sort_name),
    Count(Icons.Rounded.BarChart, R.string.sort_count);

    companion object {
        fun of(mode: SortMode): SortKind =
            when (mode.normalized) {
                SortMode.NameAsc,
                SortMode.NameDesc -> Name
                SortMode.MostUnread,
                SortMode.FewestUnread -> Count
                else -> Yours
            }
    }
}

/**
 * The sort sheet, in the style of Material 3 Expressive pickers: for groups and for the feeds inside
 * them, three big tonal tiles (Your order, Name, Unread), the chosen one filled with the accent
 * color and its corners morphing rounder, and under Name or Unread a connected button for the
 * direction. At the bottom, a button turns the Feeds list into the drag-to-reorder editor. Changes
 * apply to the list behind the sheet right away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSortSheet(
    visible: Boolean,
    settings: OrderSettings,
    onDismiss: () -> Unit,
    onGroupMode: (SortMode) -> Unit,
    onFeedMode: (SortMode) -> Unit,
    onReorder: () -> Unit,
    onReset: () -> Unit,
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
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            SortSection(stringResource(R.string.sort_groups), settings.groupMode, onGroupMode)
            Spacer(Modifier.height(24.dp))
            SortSection(stringResource(R.string.sort_feeds), settings.feedMode, onFeedMode)

            Spacer(Modifier.height(16.dp))
            val counting =
                SortKind.of(settings.groupMode) == SortKind.Count ||
                    SortKind.of(settings.feedMode) == SortKind.Count
            Text(
                text =
                    stringResource(
                        if (counting) R.string.sort_most_unread_note else R.string.sort_your_order_note
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onReorder,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = stringResource(R.string.sort_reorder),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.sort_reset))
            }
            Spacer(Modifier.height(12.dp))
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
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
                                SortKind.Yours -> SortMode.Default
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
        visible = kind != SortKind.Yours,
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
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
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
                    modifier = Modifier.height(52.dp),
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** A big square tile with an icon, its label under it. The chosen one is filled and rounder. */
@Composable
private fun SortTile(kind: SortKind, selected: Boolean, onClick: () -> Unit) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val corner by animateDpAsState(if (selected) 32.dp else 22.dp, spatial, label = "tileCorner")
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
                Modifier.size(88.dp)
                    .clip(RoundedCornerShape(corner))
                    .background(container)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                kind.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(kind.label),
            style = MaterialTheme.typography.titleSmall,
            color = labelColor,
        )
    }
}

/**
 * The Feeds list in reorder mode: every group is a card you can drag by its handle, and tapping a
 * group opens its feeds so they can be dragged inside it. [groups] is every group, in order.
 */
@Composable
fun FeedReorderEditor(
    groups: List<GroupWithFeed>,
    onMoveGroups: (List<String>) -> Unit,
    onMoveFeeds: (groupId: String, feedIds: List<String>) -> Unit,
) {
    ReorderableColumn(items = groups, itemKey = { it.group.id }, onReorder = onMoveGroups) {
        item,
        handle,
        dragging ->
        var expanded by rememberSaveable(item.group.id) { mutableStateOf(false) }
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .then(if (dragging) Modifier.shadow(12.dp, Shape32) else Modifier)
                    .clip(Shape32)
                    .background(
                        if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceContainerLow
                    )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth().height(64.dp).clickable { expanded = !expanded }
                        .padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        text = item.group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.sort_feed_count,
                                item.feeds.size,
                                item.feeds.size,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription =
                        stringResource(if (expanded) R.string.expand_less else R.string.expand_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SortHandle(handle)
            }
            AnimatedVisibility(visible = expanded && !dragging) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    ReorderableColumn(
                        items = item.feeds,
                        itemKey = { it.id },
                        onReorder = { onMoveFeeds(item.group.id, it) },
                    ) { feed, feedHandle, feedDragging ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(52.dp)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (feedDragging)
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                    .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FeedIcon(feedName = feed.name, iconUrl = feed.icon, size = 24.dp)
                            Text(
                                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                                text = feed.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            SortHandle(feedHandle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortHandle(handle: Modifier) {
    Box(modifier = Modifier.size(52.dp).then(handle), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.sort_drag_handle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A column whose rows can be reordered by dragging their handle. Rows can have different heights:
 * dragging past half of a neighbour swaps the two, and [onReorder] gets the new order of the keys
 * when the finger lifts.
 */
@Composable
private fun <T> ReorderableColumn(
    items: List<T>,
    itemKey: (T) -> String,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    var list by remember(items) { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableMapOf<String, Float>() }
    val haptic = LocalHapticFeedback.current

    fun swap(from: Int, to: Int) {
        list = list.toMutableList().apply { add(to, removeAt(from)) }
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    Column(modifier = modifier) {
        list.forEach { item ->
            val id = itemKey(item)
            key(id) {
                val dragging = draggingKey == id
                val handle =
                    Modifier.pointerInput(id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingKey = id
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                var index = list.indexOfFirst { itemKey(it) == id }
                                while (true) {
                                    val next = list.getOrNull(index + 1)?.let { itemKey(it) }
                                    val previous = list.getOrNull(index - 1)?.let { itemKey(it) }
                                    val nextHeight = next?.let { heights[it] }
                                    val previousHeight = previous?.let { heights[it] }
                                    if (dragOffset > 0 && nextHeight != null && dragOffset > nextHeight / 2) {
                                        swap(index, index + 1)
                                        dragOffset -= nextHeight
                                        index++
                                    } else if (
                                        dragOffset < 0 &&
                                            previousHeight != null &&
                                            -dragOffset > previousHeight / 2
                                    ) {
                                        swap(index, index - 1)
                                        dragOffset += previousHeight
                                        index--
                                    } else break
                                }
                            },
                            onDragEnd = {
                                draggingKey = null
                                dragOffset = 0f
                                onReorder(list.map(itemKey))
                            },
                            onDragCancel = {
                                draggingKey = null
                                dragOffset = 0f
                                list = items
                            },
                        )
                    }
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .onSizeChanged { heights[id] = it.height.toFloat() }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                ) {
                    row(item, handle, dragging)
                }
            }
        }
    }
}
