package me.ash.reader.ui.page.settings.sort

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import me.ash.reader.ui.component.base.Banner
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.theme.palette.onLight

@HiltViewModel
class SortFeedsViewModel
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

    fun moveGroups(ids: List<String>) = feedOrdering.update { it.copy(groupOrder = ids) }

    fun moveFeeds(groupId: String, ids: List<String>) =
        feedOrdering.update { it.copy(feedOrder = it.feedOrder + (groupId to ids)) }

    fun reset() = feedOrdering.update { OrderSettings() }
}

private val RowHeight = 64.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortFeedsPage(onBack: () -> Unit, viewModel: SortFeedsViewModel = hiltViewModel()) {
    val settings = viewModel.settings.collectAsStateValue()
    val ordered = viewModel.ordered.collectAsStateValue()

    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.sort_title), desc = "")
                    Spacer(Modifier.height(16.dp))
                    Banner(
                        title = stringResource(R.string.sort_intro),
                        icon = Icons.AutoMirrored.Outlined.Sort,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // --- Groups ---
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.sort_groups),
                    )
                    Spacer(Modifier.height(8.dp))
                    ModeChips(selected = settings.groupMode, onSelect = viewModel::setGroupMode)
                    if (settings.groupMode == SortMode.MostUnread) {
                        ModeNote(stringResource(R.string.sort_most_unread_note))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (settings.groupMode == SortMode.Manual) {
                    item {
                        ModeNote(stringResource(R.string.sort_manual_groups_hint))
                        Spacer(Modifier.height(4.dp))
                        ReorderableColumn(
                            items = ordered,
                            itemKey = { it.group.id },
                            onReorder = viewModel::moveGroups,
                        ) { item, handle, dragging ->
                            SortRow(
                                title = item.group.name,
                                subtitle =
                                    pluralStringResource(
                                        R.plurals.sort_feed_count,
                                        item.feeds.size,
                                        item.feeds.size,
                                    ),
                                leading = {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                handle = handle,
                                dragging = dragging,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // --- Feeds ---
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.sort_feeds),
                    )
                    Spacer(Modifier.height(8.dp))
                    ModeChips(selected = settings.feedMode, onSelect = viewModel::setFeedMode)
                    if (settings.feedMode == SortMode.MostUnread) {
                        ModeNote(stringResource(R.string.sort_most_unread_note))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (settings.feedMode == SortMode.Manual) {
                    item { ModeNote(stringResource(R.string.sort_manual_feeds_hint)) }
                    items(ordered.size, key = { ordered[it].group.id }) { index ->
                        val group = ordered[index]
                        var expanded by rememberSaveable(group.group.id) { mutableStateOf(false) }
                        Column {
                            Surface(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 3.dp)
                                        .clickable { expanded = !expanded },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Row(
                                    modifier = Modifier.height(52.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                        text = group.group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = group.feeds.size.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        if (expanded) Icons.Rounded.KeyboardArrowUp
                                        else Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                            AnimatedVisibility(visible = expanded) {
                                ReorderableColumn(
                                    items = group.feeds,
                                    itemKey = { it.id },
                                    onReorder = { viewModel.moveFeeds(group.group.id, it) },
                                ) { feed, handle, dragging ->
                                    SortRow(
                                        title = feed.name,
                                        subtitle = null,
                                        leading = {
                                            FeedIcon(
                                                feedName = feed.name,
                                                iconUrl = feed.icon,
                                                size = 28.dp,
                                            )
                                        },
                                        handle = handle,
                                        dragging = dragging,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    if (!settings.isDefault) {
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            onClick = viewModel::reset,
                        ) {
                            Text(stringResource(R.string.sort_reset))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(selected: SortMode, onSelect: (SortMode) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

@Composable
private fun ModeNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SortRow(
    title: String,
    subtitle: String?,
    leading: @Composable () -> Unit,
    handle: Modifier,
    dragging: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(18.dp),
        color =
            if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.height(RowHeight - 6.dp).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The handle: press and drag it up or down.
            Box(modifier = Modifier.size(56.dp).then(handle), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.sort_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A list whose rows can be reordered by dragging their handle. The rows have a fixed height, so
 * moving past half a row swaps it with its neighbour; [onReorder] gets the new order of the keys
 * when the finger lifts.
 */
@Composable
private fun <T> ReorderableColumn(
    items: List<T>,
    itemKey: (T) -> String,
    onReorder: (List<String>) -> Unit,
    rowHeight: Dp = RowHeight,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    var list by remember(items) { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val haptic = LocalHapticFeedback.current

    fun swap(from: Int, to: Int) {
        list = list.toMutableList().apply { add(to, removeAt(from)) }
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    Column {
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
                                val index = list.indexOfFirst { itemKey(it) == id }
                                if (dragOffset > rowPx / 2 && index < list.lastIndex) {
                                    swap(index, index + 1)
                                    dragOffset -= rowPx
                                } else if (dragOffset < -rowPx / 2 && index > 0) {
                                    swap(index, index - 1)
                                    dragOffset += rowPx
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
                            .height(rowHeight)
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                    contentAlignment = Alignment.Center,
                ) {
                    row(item, handle, dragging)
                }
            }
        }
    }
}
