@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.filters

import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
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

    private val _watchedFound = MutableStateFlow<Int?>(null)
    /** How many new watched articles Apply now found on the Watched tab. */
    val watchedFound: StateFlow<Int?> = _watchedFound.asStateFlow()

    fun watchNow() {
        _watchedFound.value = null
        viewModelScope.launch {
            val before = WatchedWords.log.size
            runCatching { filters.apply(accountService.getCurrentAccountId()) }
            _watchedFound.value = (WatchedWords.log.size - before).coerceAtLeast(0)
        }
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
    var addWatchedVisible by rememberSaveable { mutableStateOf(false) }
    // Watched (0) or Muted (1).
    var tab by rememberSaveable { mutableStateOf(0) }
    val watched = WatchedWords.words
    val watchedLog = WatchedWords.log
    val watchedFound = viewModel.watchedFound.collectAsStateValue()
    // Older days are grouped by reason (duplicate stories, then each keyword), each group closed
    // until tapped; an open group shows a few articles at a time. Today's are always shown.
    var openGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    var shownInGroup by remember { mutableStateOf(mapOf<String, Int>()) }
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
            // One list: the header (title, tiles, tabs), then the chosen tab's part.
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.filters_settings), desc = "")
                    Spacer(Modifier.height(8.dp))
                    // At a glance: what the filters have caught recently.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Visibility,
                            onClick = { tab = 0 },
                            count = watchedLog.size,
                            label = pluralStringResource(R.plurals.filters_stat_watched, watchedLog.size),
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.NotificationsOff,
                            onClick = { tab = 1 },
                            count = mutedCount,
                            label = pluralStringResource(R.plurals.filters_stat_muted, mutedCount),
                        )
                        StatTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = { tab = 1 },
                            count = duplicateCount,
                            label =
                                pluralStringResource(R.plurals.filters_stat_duplicates, duplicateCount),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // Watched on the left, muted on the right: tap or swipe.
                    FilterTabs(page = tab) { tab = it }
                    Spacer(Modifier.height(8.dp))
                }
                if (tab == 0) {
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.filters_watched),
                    )
                    Spacer(Modifier.height(8.dp))
                    // Watched words, the opposite of muted ones, in a card like theirs: the words
                    // as tertiary chips with an eye (tap one to stop watching it) and Add.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ShapeIcon(Icons.Outlined.Visibility, MaterialShapes.Cookie7Sided)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = stringResource(R.string.filters_watched),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        text = watched.size.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                watched.forEach { word ->
                                    InputChip(
                                        selected = false,
                                        onClick = { WatchedWords.remove(context, word) },
                                        label = { Text(word) },
                                        shape = CircleShape,
                                        trailingIcon = {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.filters_remove, word),
                                                modifier = Modifier.size(InputChipDefaults.IconSize),
                                            )
                                        },
                                    )
                                }
                                AssistChip(
                                    onClick = { addWatchedVisible = true },
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
                            if (watched.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.filters_watched_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            // Look through recent articles now, with what it found next to the button.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = viewModel::watchNow,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text(stringResource(R.string.filters_apply_now))
                                }
                                watchedFound?.let {
                                    Spacer(Modifier.width(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        shape = CircleShape,
                                    ) {
                                        Text(
                                            text = pluralStringResource(R.plurals.filters_watched_found, it, it),
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (watchedLog.isEmpty()) 28.dp else 20.dp))
                }
                // Recently watched, laid out like Recently filtered: today's articles in full,
                // earlier days under their date with one closed row per word and its count.
                if (watchedLog.isNotEmpty()) {
                    item(key = "watched-head") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Subtitle(modifier = Modifier.weight(1f), text = stringResource(R.string.filters_watched_log))
                            TextButton(onClick = { WatchedWords.clearLog() }) {
                                Text(stringResource(R.string.filters_log_clear))
                            }
                        }
                    }
                    val openWatched = { entry: WatchedEntry ->
                        context.startActivity(
                            Intent(context, MainActivity::class.java).putExtra(ExtraName.ARTICLE_ID, entry.articleId)
                        )
                    }
                    val watchedDays = watchedLog.groupBy { dayOf(it.at) }
                    val todayDay = java.time.LocalDate.now().toEpochDay()
                    watchedDays[todayDay]?.let { rows ->
                        item(key = "watched-day-$todayDay") {
                            Text(
                                text = dayLabel(todayDay),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.animateItem().padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
                            )
                        }
                        itemsIndexed(rows, key = { _, it -> "watched-today-${it.articleId}" }) { index, entry ->
                            Box(Modifier.animateItem()) {
                                WatchedLogCard(entry, groupShape(index, rows.size)) { openWatched(entry) }
                            }
                        }
                    }
                    watchedDays.filterKeys { it != todayDay }.forEach { (day, dayRows) ->
                        item(key = "watched-day-$day") {
                            Text(
                                text = dayLabel(day),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.animateItem().padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
                            )
                        }
                        dayRows.groupBy { it.word }.forEach { (word, rows) ->
                            val key = "watch-$day-$word"
                            val isOpen = key in openGroups
                            item(key = "group-$key") {
                                ReasonGroupRow(
                                    title = stringResource(R.string.filters_group_keyword, word),
                                    count = rows.size,
                                    open = isOpen,
                                    modifier = Modifier.animateItem(),
                                    onClick = {
                                        openGroups = if (isOpen) openGroups - key else openGroups + key
                                        if (isOpen) shownInGroup = shownInGroup - key
                                    },
                                )
                            }
                            if (isOpen) {
                                val shown = rows.take(shownInGroup[key] ?: GROUP_PAGE)
                                itemsIndexed(shown, key = { _, it -> "$key-${it.articleId}" }) { index, entry ->
                                    Box(Modifier.animateItem()) {
                                        WatchedLogCard(entry, groupShape(index, shown.size)) { openWatched(entry) }
                                    }
                                }
                                val more = rows.size - shown.size
                                if (more > 0) {
                                    item(key = "more-$key") {
                                        TextButton(
                                            onClick = { shownInGroup = shownInGroup + (key to shown.size + GROUP_PAGE * 4) },
                                            modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp),
                                        ) {
                                            Text(stringResource(R.string.filters_show_more, more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "watched-end") { Spacer(Modifier.height(28.dp)) }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Outlined.Info, MaterialShapes.Cookie4Sided, tonal = true)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.filters_watched_desc),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
                } else {
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                      Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShapeIcon(Icons.Outlined.NotificationsOff, MaterialShapes.Cookie7Sided)
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                val open = { articleId: String ->
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .putExtra(ExtraName.ARTICLE_ID, articleId)
                    )
                }
                // One day's filtered articles: duplicates grouped under the story that was kept.
                fun dayRows(day: Long, entries: List<LogRow>, header: Boolean) {
                    if (header) {
                        item(key = "day-$day") {
                            Text(
                                text = dayLabel(day),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
                            )
                        }
                    }
                    itemsIndexed(entries, key = { _, it -> it.key }) { index, row ->
                        Box(Modifier.animateItem()) {
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
                }
                // An earlier day: one closed row per reason, with its count. Opening one shows its
                // first few articles and "Show more"; rows slide and fade in and out.
                fun reasonGroups(day: Long, entries: List<LogRow>) {
                    item(key = "day-$day") {
                        Text(
                            text = dayLabel(day),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.animateItem().padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
                        )
                    }
                    val groups = buildList {
                        val dupes = entries.filterIsInstance<LogRow.Duplicates>()
                        if (dupes.isNotEmpty()) add(Triple("$day-dupes", null as String?, dupes as List<LogRow>))
                        entries
                            .filterIsInstance<LogRow.Single>()
                            .groupBy { it.entry.detail }
                            .forEach { (word, rows) -> add(Triple("$day-kw-$word", word, rows as List<LogRow>)) }
                    }
                    groups.forEach { (key, word, rows) ->
                        val isOpen = key in openGroups
                        val count = rows.sumOf { if (it is LogRow.Duplicates) it.copies.size else 1 }
                        item(key = "group-$key") {
                            ReasonGroupRow(
                                title =
                                    if (word == null) stringResource(R.string.filters_group_duplicates)
                                    else stringResource(R.string.filters_group_keyword, word),
                                count = count,
                                open = isOpen,
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    openGroups = if (isOpen) openGroups - key else openGroups + key
                                    if (isOpen) shownInGroup = shownInGroup - key
                                },
                            )
                        }
                        if (isOpen) {
                            val shown = rows.take(shownInGroup[key] ?: GROUP_PAGE)
                            itemsIndexed(shown, key = { _, it -> "$key-" + it.key }) { index, row ->
                                Box(Modifier.animateItem()) {
                                    val shape = groupShape(index, shown.size)
                                    when (row) {
                                        is LogRow.Single ->
                                            FilterLogCard(
                                                entry = row.entry,
                                                shape = shape,
                                                onOpen = { open(row.entry.articleId) },
                                                onUndo = { viewModel.undo(row.entry) },
                                            )
                                        is LogRow.Duplicates ->
                                            DuplicateGroupCard(
                                                group = row,
                                                shape = shape,
                                                onOpen = open,
                                                onUndo = { viewModel.undo(it) },
                                            )
                                    }
                                }
                            }
                            val more = rows.size - shown.size
                            if (more > 0) {
                                item(key = "more-$key") {
                                    TextButton(
                                        onClick = { shownInGroup = shownInGroup + (key to shown.size + GROUP_PAGE * 4) },
                                        modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp),
                                    ) {
                                        Text(stringResource(R.string.filters_show_more, more))
                                    }
                                }
                            }
                        }
                    }
                }
                val today = java.time.LocalDate.now().toEpochDay()
                val earlier = days.filterKeys { it != today }
                val todayRows = days[today]
                if (todayRows != null) {
                    dayRows(today, todayRows, header = true)
                } else if (earlier.isNotEmpty()) {
                    item(key = "nothing-today") {
                        Text(
                            text = stringResource(R.string.filters_nothing_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                }
                earlier.forEach { (day, entries) -> reasonGroups(day, entries) }
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
            }
        },
    )

    if (addWatchedVisible) {
        val draft = rememberTextFieldState()
        TextFieldDialog(
            textFieldState = draft,
            visible = true,
            title = stringResource(R.string.filters_add_watched),
            icon = Icons.Outlined.Visibility,
            placeholder = stringResource(R.string.filters_add_hint),
            confirmText = stringResource(R.string.filters_add),
            onDismissRequest = { addWatchedVisible = false },
            onConfirm = { word ->
                if (word.isNotBlank()) WatchedWords.add(context, word.trim())
                addWatchedVisible = false
            },
        )
    }
    // Only composed while open, so it starts empty every time.
    if (addDialogVisible) {
        val draft = rememberTextFieldState()
        TextFieldDialog(
            textFieldState = draft,
            visible = true,
            title = stringResource(R.string.filters_add_keyword),
            icon = Icons.Outlined.NotificationsOff,
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

/**
 * The two tabs, watched on the left and muted on the right, as one joined pill (the chosen half
 * filled, its icon with it), like the app's other joined buttons.
 */
@Composable
private fun FilterTabs(page: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
                Triple(Icons.Outlined.Visibility, R.string.filters_tab_watched, 0),
                Triple(Icons.Outlined.NotificationsOff, R.string.filters_tab_muted, 1),
            )
            .forEach { (icon, label, index) ->
                val selected = page == index
                val outer = 24.dp
                val inner = 8.dp
                val color by
                    androidx.compose.animation.animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "tab",
                    )
                Surface(
                    onClick = { onSelect(index) },
                    color = color,
                    contentColor =
                        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape =
                        if (index == 0) RoundedCornerShape(outer, inner, inner, outer)
                        else RoundedCornerShape(inner, outer, outer, inner),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
    }
}

/** A big tonal tile with a number, like the tiles in the sort sheet. */
@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, count: Int, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
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

/** One watched article: its title and feed, on the eye's shape; tap to open it. */
@Composable
private fun WatchedLogCard(entry: WatchedEntry, shape: RoundedCornerShape, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
                // Like a filtered article's row: the word as a small chip, then the feed.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = stringResource(R.string.filters_reason_watched, entry.word),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.filters_from_feed, entry.feedName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                    )
                }
            }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.5.dp),
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
                    if (muted) Icons.Outlined.NotificationsOff else Icons.Outlined.ContentCopy,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.5.dp),
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
                            group.originalFeed?.let { stringResource(R.string.filters_kept_unread_from, it) }
                                ?: stringResource(R.string.filters_kept_unread),
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
                    // The hidden copy: which feed it came from, and its own title.
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.filters_hidden_from, copy.feedName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = copy.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
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

/** "Earlier (12)": opens and closes the filtered articles of the days before today. */
/** A reason's filtered articles on an earlier day, closed: its name, how many, and an arrow. */
@Composable
private fun ReasonGroupRow(title: String, count: Int, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "group arrow")
    Surface(
        onClick = onClick,
        color = if (open) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (open) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = turn },
            )
        }
    }
}

/** How many articles an opened group shows at first ("Show more" adds more). */
private const val GROUP_PAGE = 5

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
