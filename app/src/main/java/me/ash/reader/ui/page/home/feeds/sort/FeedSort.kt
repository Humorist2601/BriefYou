package me.ash.reader.ui.page.home.feeds.sort

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
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

    /** All groups and feeds, whatever the filter, in their default order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val groups: StateFlow<List<GroupWithFeed>> =
        accountService.currentAccountIdFlow
            .filterNotNull()
            .flatMapLatest { groupDao.queryAllGroupWithFeedAsFlow(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The same groups and feeds in the order the settings give them. */
    val ordered: StateFlow<List<GroupWithFeed>> =
        combine(groups, settings) { groups, settings -> feedOrdering.sort(groups, settings) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setGroupMode(mode: SortMode) =
        feedOrdering.update { s ->
            if (mode == SortMode.Manual && s.groupOrder.isEmpty()) {
                // Start from the order you see right now.
                s.copy(
                    groupMode = mode,
                    groupOrder = feedOrdering.sort(groups.value, s).map { it.group.id },
                )
            } else s.copy(groupMode = mode)
        }

    fun setFeedMode(mode: SortMode) =
        feedOrdering.update { s ->
            if (mode == SortMode.Manual) {
                val current = feedOrdering.sort(groups.value, s)
                val seeded =
                    current
                        .filter { it.group.id !in s.feedOrder }
                        .associate { it.group.id to it.feeds.map { feed -> feed.id } }
                s.copy(feedMode = mode, feedOrder = s.feedOrder + seeded)
            } else s.copy(feedMode = mode)
        }

    /** Both groups and feeds by hand, starting from the order you see now. */
    fun startManual() {
        setGroupMode(SortMode.Manual)
        setFeedMode(SortMode.Manual)
    }

    fun moveGroups(ids: List<String>) = feedOrdering.update { it.copy(groupOrder = ids) }

    fun moveFeeds(groupId: String, ids: List<String>) =
        feedOrdering.update { it.copy(feedOrder = it.feedOrder + (groupId to ids)) }

    fun reset() = feedOrdering.update { OrderSettings() }
}

/**
 * The sort sheet: how groups and feeds are ordered, and a button that turns the Feeds list into
 * the drag-to-reorder editor. Changes apply to the list behind the sheet right away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        Column(modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.sort_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            SortSection(stringResource(R.string.sort_groups), settings.groupMode, onGroupMode)
            Spacer(Modifier.height(16.dp))
            SortSection(stringResource(R.string.sort_feeds), settings.feedMode, onFeedMode)

            if (settings.groupMode == SortMode.MostUnread || settings.feedMode == SortMode.MostUnread) {
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = stringResource(R.string.sort_most_unread_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onReorder) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(R.string.sort_reorder),
                    )
                }
                if (!settings.isDefault) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReset) { Text(stringResource(R.string.sort_reset)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortSection(title: String, selected: SortMode, onSelect: (SortMode) -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(modeLabel(mode)) },
            )
        }
    }
}

@Composable
private fun modeLabel(mode: SortMode): String =
    stringResource(
        when (mode) {
            SortMode.Default -> R.string.sort_default
            SortMode.NameAsc -> R.string.sort_name_asc
            SortMode.NameDesc -> R.string.sort_name_desc
            SortMode.MostUnread -> R.string.sort_most_unread
            SortMode.Manual -> R.string.sort_manual
        }
    )

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
