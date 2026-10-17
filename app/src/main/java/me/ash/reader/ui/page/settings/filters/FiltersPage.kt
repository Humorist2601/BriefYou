package me.ash.reader.ui.page.settings.filters

import android.content.Intent
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import me.ash.reader.domain.service.FilterLogEntry
import me.ash.reader.domain.service.FilterReason
import me.ash.reader.infrastructure.android.MainActivity
import me.ash.reader.ui.page.common.ExtraName
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.BriefFilters
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.component.base.Tips
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@HiltViewModel
class FiltersViewModel
@Inject
constructor(val filters: BriefFilters, private val accountService: AccountService) : ViewModel() {
    private val _applied = MutableStateFlow<Int?>(null)
    val applied: StateFlow<Int?> = _applied.asStateFlow()

    init {
        // Drop entries for articles that were deleted since the app started.
        viewModelScope.launch { runCatching { filters.prune() } }
    }

    fun undo(entry: FilterLogEntry) {
        viewModelScope.launch { runCatching { filters.undo(entry) } }
    }

    fun resetUndone() {
        viewModelScope.launch { runCatching { filters.resetExemptions() } }
    }

    fun clearLog() {
        viewModelScope.launch { filters.clearLog() }
    }

    fun applyNow() {
        _applied.value = null
        viewModelScope.launch {
            _applied.value = runCatching { filters.apply(accountService.getCurrentAccountId()) }.getOrDefault(0)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersPage(onBack: () -> Unit, viewModel: FiltersViewModel = hiltViewModel()) {
    val keywords = viewModel.filters.mutedKeywords.collectAsStateValue()
    val dupes = viewModel.filters.hideDuplicates.collectAsStateValue()
    val applied = viewModel.applied.collectAsStateValue()
    val log = viewModel.filters.log.collectAsStateValue()
    val undoneCount = viewModel.filters.exemptCount.collectAsStateValue()
    val context = LocalContext.current

    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    val mutedCount = log.count { it.reason == FilterReason.MutedKeyword }
    val duplicateCount = log.count { it.reason == FilterReason.Duplicate }
    // The filtered articles, newest first, under Today / Yesterday / a date.
    val days = remember(log) { log.sortedByDescending { it.at }.groupBy { dayOf(it.at) } }

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
                    DisplayText(text = stringResource(R.string.filters_settings), desc = "")
                    Spacer(Modifier.height(8.dp))
                    // At a glance: what the filters have caught recently.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.FilterAlt,
                            count = mutedCount,
                            label = pluralStringResource(R.plurals.filters_stat_muted, mutedCount),
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.ContentCopy,
                            count = duplicateCount,
                            label =
                                pluralStringResource(R.plurals.filters_stat_duplicates, duplicateCount),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.filters_duplicates),
                    )
                    SettingItem(
                        title = stringResource(R.string.filters_dupes),
                        desc = stringResource(R.string.filters_dupes_desc),
                        onClick = { viewModel.filters.setHideDuplicates(!dupes) },
                    ) {
                        RYSwitch(activated = dupes) { viewModel.filters.setHideDuplicates(!dupes) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.filters_keywords),
                    )
                    Spacer(Modifier.height(8.dp))
                    // The muted keywords as chips in one rounded box, with an Add chip at the end.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            keywords.forEach { word ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.filters.removeKeyword(word) },
                                    label = { Text(word) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription =
                                                stringResource(R.string.filters_remove, word),
                                            modifier = Modifier.size(InputChipDefaults.IconSize),
                                        )
                                    },
                                )
                            }
                            AssistChip(
                                onClick = { addDialogVisible = true },
                                label = { Text(stringResource(R.string.filters_add)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                    if (keywords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.filters_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Run the filters now, with what they caught next to the button.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = viewModel::applyNow,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.filters_apply_now))
                        }
                        applied?.let {
                            Text(
                                text = pluralStringResource(R.plurals.filters_caught, it, it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(if (undoneCount > 0) 12.dp else 24.dp))
                    // Articles brought back with Undo are skipped from then on; this lets the
                    // filters catch them again.
                    if (undoneCount > 0) {
                        SettingItem(
                            title = stringResource(R.string.filters_reset_undone),
                            desc =
                                pluralStringResource(
                                    R.plurals.filters_reset_undone_desc,
                                    undoneCount,
                                    undoneCount,
                                ),
                            onClick = viewModel::resetUndone,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Subtitle(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.filters_log),
                        )
                        if (log.isNotEmpty()) {
                            TextButton(onClick = viewModel::clearLog) {
                                Text(stringResource(R.string.filters_log_clear))
                            }
                        }
                    }
                    if (log.isEmpty()) {
                        Text(
                            text = stringResource(R.string.filters_log_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                days.forEach { (day, entries) ->
                    item(key = "day-$day") {
                        Text(
                            text = dayLabel(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(entries, key = { _, it -> it.articleId }) { index, entry ->
                        FilterLogCard(
                            entry = entry,
                            shape = groupShape(index, entries.size),
                            onOpen = {
                                context.startActivity(
                                    Intent(context, MainActivity::class.java)
                                        .putExtra(ExtraName.ARTICLE_ID, entry.articleId)
                                )
                            },
                            onUndo = { viewModel.undo(entry) },
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Tips(text = stringResource(R.string.filters_intro))
                    Spacer(Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    // Only composed while open, so it starts empty every time. The dialog's Add button follows
    // [draft], so what you type has to come back into it.
    if (addDialogVisible) {
        var draft by remember { mutableStateOf("") }
        TextFieldDialog(
            visible = true,
            title = stringResource(R.string.filters_add_keyword),
            icon = Icons.Outlined.FilterAlt,
            value = draft,
            placeholder = stringResource(R.string.filters_add_hint),
            confirmText = stringResource(R.string.filters_add),
            onValueChange = { draft = it },
            onDismissRequest = { addDialogVisible = false },
            onConfirm = { word ->
                if (word.isNotBlank()) viewModel.filters.addKeyword(word.trim())
                addDialogVisible = false
            },
        )
    }
}

/** A big tonal tile with a number, like the tiles in the sort sheet. */
@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, count: Int, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One filtered article: its title, the feed and why it was filtered, and Undo. */
@Composable
private fun FilterLogCard(
    entry: FilterLogEntry,
    shape: RoundedCornerShape,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Why it was filtered, as a small chip.
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text =
                                when (entry.reason) {
                                    FilterReason.MutedKeyword ->
                                        stringResource(R.string.filters_reason_muted, entry.detail)
                                    FilterReason.Duplicate ->
                                        stringResource(
                                            R.string.filters_reason_duplicate,
                                            entry.detailFeed ?: entry.detail,
                                        )
                                },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = entry.feedName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                    )
                }
            }
            IconButton(onClick = onUndo) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.filters_log_undo),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Big corners on the outside of a group, small ones between its cards. */
private fun groupShape(position: Int, size: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    val top = if (position == 0) outer else inner
    val bottom = if (position == size - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The day a time falls on, as days since the epoch in the phone's time zone. */
private fun dayOf(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()

@Composable
private fun dayLabel(day: Long): String {
    val today = java.time.LocalDate.now().toEpochDay()
    return when (day) {
        today -> stringResource(R.string.filters_today)
        today - 1 -> stringResource(R.string.filters_yesterday)
        else ->
            java.time.LocalDate.ofEpochDay(day)
                .format(
                    java.time.format.DateTimeFormatter.ofLocalizedDate(
                        java.time.format.FormatStyle.MEDIUM
                    )
                )
    }
}
