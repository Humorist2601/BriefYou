@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.filters

import me.ash.reader.brief.ui.GoogleSansWideFontFamily
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.clickable
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import me.ash.reader.brief.filters.FilterLogEntry
import me.ash.reader.brief.filters.FilterReason
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
import androidx.compose.material3.IconButtonDefaults
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
import me.ash.reader.brief.filters.BriefFilters
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.component.base.TextFieldDialog
import androidx.compose.foundation.text.input.rememberTextFieldState
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
    // Duplicates of one story are grouped under it.
    val days =
        remember(log) {
            log.sortedByDescending { it.at }.groupBy { dayOf(it.at) }.mapValues { logRows(it.value) }
        }

    // Read You's settings-page scaffold (deprecated by Read You, but all of Read You's settings
    // pages still use it), so this page looks like the others.
    @Suppress("DEPRECATION")
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
                    Spacer(Modifier.height(8.dp))
                    // The switch in its own card, with its icon on an Expressive shape.
                    Surface(
                        onClick = { viewModel.filters.setHideDuplicates(!dupes) },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShapeIcon(Icons.Outlined.ContentCopy, MaterialShapes.Cookie4Sided)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.filters_dupes),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.filters_dupes_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            RYSwitch(activated = dupes) { viewModel.filters.setHideDuplicates(!dupes) }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.filters_keywords),
                    )
                    Spacer(Modifier.height(8.dp))
                    // The muted keywords in one card: a header with how many, the words as
                    // removable chips with an Add chip at the end, and Apply now at the bottom.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                      Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Outlined.FilterAlt, MaterialShapes.Cookie7Sided)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.filters_keywords),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = keywords.size.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            keywords.forEach { word ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.filters.removeKeyword(word) },
                                    label = { Text(word) },
                                    shape = CircleShape,
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
                                shape = CircleShape,
                                colors =
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                border = null,
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                        if (keywords.isEmpty()) {
                            Text(
                                text = stringResource(R.string.filters_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        // Run the filters now, with what they caught next to the button.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = viewModel::applyNow,
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Text(stringResource(R.string.filters_apply_now))
                            }
                            applied?.let {
                                Spacer(Modifier.width(12.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = pluralStringResource(R.plurals.filters_caught, it, it),
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
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
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 8.dp),
                        )
                    }
                    val open = { articleId: String ->
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra(ExtraName.ARTICLE_ID, articleId)
                        )
                    }
                    itemsIndexed(entries, key = { _, it -> it.key }) { index, row ->
                        when (row) {
                            is LogRow.Single ->
                                FilterLogCard(
                                    entry = row.entry,
                                    shape = groupShape(index, entries.size),
                                    onOpen = { open(row.entry.articleId) },
                                    onUndo = { viewModel.undo(row.entry) },
                                )
                            is LogRow.Duplicates ->
                                DuplicateGroupCard(
                                    group = row,
                                    shape = groupShape(index, entries.size),
                                    onOpen = open,
                                    onUndo = { viewModel.undo(it) },
                                )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Outlined.Info, MaterialShapes.Cookie4Sided, tonal = true)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.filters_intro),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    // Only composed while open, so it starts empty every time.
    if (addDialogVisible) {
        val draft = rememberTextFieldState()
        TextFieldDialog(
            textFieldState = draft,
            visible = true,
            title = stringResource(R.string.filters_add_keyword),
            icon = Icons.Outlined.FilterAlt,
            placeholder = stringResource(R.string.filters_add_hint),
            confirmText = stringResource(R.string.filters_add),
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
        shape = RoundedCornerShape(28.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = count.toString(),
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = GoogleSansWideFontFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
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
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Why it was filtered, at a glance: a muted word or a duplicate story.
            val muted = entry.reason == FilterReason.MutedKeyword
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip((if (muted) MaterialShapes.Cookie7Sided else MaterialShapes.Cookie4Sided).toShape())
                        .background(
                            if (muted) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (muted) Icons.Outlined.FilterAlt else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint =
                        if (muted) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
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
                        color =
                            if (muted) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor =
                            if (muted) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    // The feed this copy came from (the chip names the feed that had it first).
                    Text(
                        text = stringResource(R.string.filters_from_feed, entry.feedName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                    )
                }
                // For a duplicate: the original story it repeats.
                if (entry.reason == FilterReason.Duplicate && entry.detailFeed != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.filters_original_story, entry.detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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

/** A row of the filtered list: one muted article, or the hidden copies of one story. */
private sealed interface LogRow {
    val key: String

    data class Single(val entry: FilterLogEntry) : LogRow {
        override val key: String
            get() = entry.articleId
    }

    data class Duplicates(
        val originalTitle: String,
        val originalFeed: String?,
        val originalId: String?,
        val copies: List<FilterLogEntry>,
    ) : LogRow {
        override val key: String
            get() = "dupes-" + copies.first().articleId
    }
}

/** A day's filtered articles as rows: duplicates of the same story together, in list order. */
private fun logRows(entries: List<FilterLogEntry>): List<LogRow> {
    val groups = LinkedHashMap<Any, MutableList<FilterLogEntry>>()
    for (entry in entries) {
        val key: Any =
            if (entry.reason == FilterReason.Duplicate) Triple(entry.detailId, entry.detailFeed, entry.detail)
            else entry.articleId
        groups.getOrPut(key) { mutableListOf() } += entry
    }
    return groups.values.map { list ->
        val first = list.first()
        if (first.reason == FilterReason.Duplicate) {
            LogRow.Duplicates(
                originalTitle = first.detail,
                originalFeed = first.detailFeed,
                originalId = first.detailId,
                copies = list,
            )
        } else {
            LogRow.Single(first)
        }
    }
}

/**
 * The story that was published first (its feed and title; tap to open it, when known), with the
 * copies that were marked as read under it: each with its feed, and Undo.
 */
@Composable
private fun DuplicateGroupCard(
    group: LogRow.Duplicates,
    shape: RoundedCornerShape,
    onOpen: (String) -> Unit,
    onUndo: (FilterLogEntry) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 1.5.dp),
    ) {
        Column {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (group.originalId != null) Modifier.clickable { onOpen(group.originalId) }
                            else Modifier
                        )
                        .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(36.dp)
                            .clip(MaterialShapes.Cookie4Sided.toShape())
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            group.originalFeed?.let { stringResource(R.string.filters_first_in, it) }
                                ?: stringResource(R.string.filters_first_story),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = group.originalTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            group.copies.forEach { copy ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onOpen(copy.articleId) }
                            .padding(start = 62.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            text = copy.feedName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.filters_copy_marked_read),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Undo on a soft round background.
                    IconButton(
                        onClick = { onUndo(copy) },
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.filters_log_undo),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** An icon on a small Expressive shape, for the cards on this page. */
@Composable
private fun ShapeIcon(
    icon: ImageVector,
    shape: androidx.graphics.shapes.RoundedPolygon,
    tonal: Boolean = false,
) {
    Box(
        modifier =
            Modifier.size(40.dp)
                .clip(shape.toShape())
                .background(
                    if (tonal) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tonal) LocalContentColor.current else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
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
