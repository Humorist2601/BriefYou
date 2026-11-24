package com.ay.briefyou.brief.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ay.briefyou.R
import com.ay.briefyou.brief.ui.LocalSheetLabelInset
import com.ay.briefyou.brief.ui.SheetSection
import com.ay.briefyou.brief.ui.SheetShape
import com.ay.briefyou.brief.ui.SheetTitle
import com.ay.briefyou.brief.ui.StatTile
import com.ay.briefyou.brief.ui.rememberFullSheetState
import com.ay.briefyou.brief.ui.sheetScrollFade
import com.ay.briefyou.ui.ext.collectAsStateValue

/**
 * The top bar's filter button: a brief of the filters, only tiles and articles (what the muted
 * words, duplicates and watched words caught, and the latest filtered articles), with the buttons
 * pinned at the bottom: Apply now, and the full Filters page for the words, the switch and the log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenAll: () -> Unit,
    viewModel: FiltersViewModel = hiltViewModel(),
) {
    if (!visible) return
    val log = viewModel.filters.log.collectAsStateValue()
    val applied = viewModel.applied.collectAsStateValue()
    val mutedCount = log.count { it.reason == FilterReason.MutedKeyword }
    val duplicateCount = log.count { it.reason == FilterReason.Duplicate }
    val watchedCount = WatchedWords.log.size
    // Today's filtered articles, newest first; earlier days are on the Filters page.
    val today = remember(log) {
        val start = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        log.filter { it.at >= start }.sortedByDescending { it.at }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        shape = SheetShape,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets.navigationBars },
    ) {
        CompositionLocalProvider(LocalSheetLabelInset provides 14.dp) {
            // At most 70% of the screen, like the AI sheet; the list scrolls inside it.
            val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.7f).dp
            Column(modifier = Modifier.heightIn(max = maxHeight).padding(horizontal = 16.dp)) {
                SheetTitle(
                    stringResource(R.string.filters_settings),
                    applied?.let { pluralStringResource(R.plurals.filters_caught, it, it) }
                        ?: stringResource(R.string.filters_settings_desc),
                    icon = Icons.Outlined.FilterAlt,
                )
                Spacer(Modifier.height(4.dp))
                val scroll = rememberScrollState()
                Column(
                    modifier =
                        Modifier.weight(1f, fill = false).sheetScrollFade(scroll).verticalScroll(scroll)
                ) {
                    // The Filters page's own tiles; a tap opens the page.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val openAll = {
                            onDismiss()
                            onOpenAll()
                        }
                        StatTile(
                            icon = Icons.Outlined.NotificationsOff,
                            value = mutedCount.toString(),
                            label = pluralStringResource(R.plurals.filters_stat_muted, mutedCount),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = openAll,
                        )
                        StatTile(
                            icon = Icons.Outlined.ContentCopy,
                            value = duplicateCount.toString(),
                            label = pluralStringResource(R.plurals.filters_stat_duplicates, duplicateCount),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = openAll,
                        )
                        StatTile(
                            icon = Icons.Outlined.Visibility,
                            value = watchedCount.toString(),
                            label = pluralStringResource(R.plurals.filters_stat_watched, watchedCount),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = openAll,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    SheetSection(stringResource(R.string.filters_today))
                    if (today.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(if (log.isEmpty()) R.string.filters_log_empty else R.string.filters_nothing_today),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        // One card per article, as on the Filters page.
                        val openArticle: (String) -> Unit = { id ->
                            context.startActivity(
                                android.content.Intent(context, com.ay.briefyou.infrastructure.android.MainActivity::class.java)
                                    .putExtra(com.ay.briefyou.ui.page.common.ExtraName.ARTICLE_ID, id)
                            )
                        }
                        today.forEachIndexed { i, entry ->
                            FilterLogCard(
                                entry = entry,
                                shape = com.ay.briefyou.brief.ui.groupShape(i, today.size),
                                sidePadding = 0.dp,
                                onOpen = { openArticle(entry.articleId) },
                                onUndo = { viewModel.undo(entry) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.applyNow() },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Bolt, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.filters_apply_now))
                    }
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenAll()
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.filters_all))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
