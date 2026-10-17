package me.ash.reader.ui.page.settings.filters

import android.content.Intent
import androidx.compose.foundation.lazy.items
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
    val context = LocalContext.current

    var addDialogVisible by rememberSaveable { mutableStateOf(false) }

    // Laid out like the other settings pages: sections with a title, plain setting rows, and the
    // explanation at the bottom behind an info icon (as on the account page).
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
                    Spacer(Modifier.height(16.dp))
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
                    SettingItem(
                        title = stringResource(R.string.filters_add_keyword),
                        desc = if (keywords.isEmpty()) stringResource(R.string.filters_none) else null,
                        onClick = { addDialogVisible = true },
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(keywords, key = { "keyword-$it" }) { word ->
                    SettingItem(title = word, onClick = {}) {
                        IconButton(onClick = { viewModel.filters.removeKeyword(word) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.filters_remove, word),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SettingItem(
                        title = stringResource(R.string.filters_apply_now),
                        desc =
                            applied?.let { pluralStringResource(R.plurals.filters_applied, it, it) }
                                ?: stringResource(R.string.filters_apply_now_desc),
                        onClick = viewModel::applyNow,
                    ) {}
                    Spacer(Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.filters_log),
                    )
                    if (log.isEmpty()) {
                        SettingItem(
                            title = stringResource(R.string.filters_log_empty),
                            enabled = false,
                            onClick = {},
                        ) {}
                    }
                }
                items(log, key = { it.articleId }) { entry ->
                    FilterLogRow(
                        entry = entry,
                        onOpen = {
                            context.startActivity(
                                Intent(context, MainActivity::class.java)
                                    .putExtra(ExtraName.ARTICLE_ID, entry.articleId)
                            )
                        },
                        onUndo = { viewModel.undo(entry) },
                    )
                }
                item {
                    if (log.isNotEmpty()) {
                        SettingItem(
                            title = stringResource(R.string.filters_log_clear_all),
                            onClick = viewModel::clearLog,
                        ) {}
                    }
                    Tips(text = stringResource(R.string.filters_intro))
                    Spacer(Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    // Only composed while open, so it starts empty every time.
    if (addDialogVisible) TextFieldDialog(
        visible = true,
        title = stringResource(R.string.filters_add_keyword),
        icon = Icons.Outlined.FilterAlt,
        value = "",
        placeholder = stringResource(R.string.filters_add_hint),
        confirmText = stringResource(R.string.filters_add),
        onDismissRequest = { addDialogVisible = false },
        onConfirm = { word ->
            if (word.isNotBlank()) viewModel.filters.addKeyword(word)
            addDialogVisible = false
        },
    )
}

/** One filtered article, shown like a setting row: its title, why it was filtered, and Undo. */
@Composable
private fun FilterLogRow(entry: FilterLogEntry, onOpen: () -> Unit, onUndo: () -> Unit) {
    SettingItem(
        title = entry.title,
        desc =
            when (entry.reason) {
                FilterReason.MutedKeyword ->
                    stringResource(R.string.filters_log_muted, entry.detail, entry.feedName)
                FilterReason.Duplicate ->
                    stringResource(
                        R.string.filters_log_duplicate,
                        entry.feedName,
                        entry.detailFeed ?: "",
                        entry.detail,
                    )
            },
        onClick = onOpen,
    ) {
        TextButton(onClick = onUndo) { Text(stringResource(R.string.filters_log_undo)) }
    }
}
