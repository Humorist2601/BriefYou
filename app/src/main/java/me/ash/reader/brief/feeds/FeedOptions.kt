package me.ash.reader.brief.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.ui.SheetRow
import me.ash.reader.brief.ui.SheetRows
import me.ash.reader.brief.ui.SheetSection
import me.ash.reader.domain.model.group.Group
import me.ash.reader.ui.component.base.RYSelectionChip

/**
 * A feed's settings, in the app's sheet design (replaces Read You's chips): how it opens and
 * notifies as one joined list with switches, any [actions] (clear, unsubscribe) as a second list,
 * then its group. Used by the feed's options sheet and by Add feed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedOptions(
    fullContent: Boolean,
    browser: Boolean,
    notification: Boolean,
    onFullContent: () -> Unit,
    onBrowser: () -> Unit,
    onNotification: () -> Unit,
    groups: List<Group>,
    selectedGroupId: String,
    onGroupClick: (String) -> Unit,
    onAddNewGroup: () -> Unit,
    showGroup: Boolean = true,
    isMoveToGroup: Boolean = false,
    actions: List<SheetRow> = emptyList(),
) {
    Column {
        SheetRows(
            listOf(
                SheetRow(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.parse_full_content), checked = fullContent, onClick = onFullContent),
                SheetRow(Icons.Outlined.OpenInBrowser, stringResource(R.string.open_in_browser), checked = browser, onClick = onBrowser),
                SheetRow(Icons.Outlined.Notifications, stringResource(R.string.allow_notification), checked = notification, onClick = onNotification),
            )
        )
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SheetRows(actions)
        }
        if (showGroup) {
            Spacer(Modifier.height(20.dp))
            SheetSection(stringResource(if (isMoveToGroup) R.string.move_to_group else R.string.add_to_group))
            GroupChips(groups, selectedGroupId, onGroupClick, onAddNewGroup)
        }
    }
}

/** The groups as chips (the chosen one ticked), and a round + for a new group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupChips(
    groups: List<Group>,
    selectedGroupId: String,
    onGroupClick: (String) -> Unit,
    onAddNewGroup: (() -> Unit)? = null,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEach {
            RYSelectionChip(content = it.name, selected = it.id == selectedGroupId) { onGroupClick(it.id) }
        }
        if (onAddNewGroup != null) {
            Box(
                modifier =
                    Modifier.size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onAddNewGroup() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.create_new_group),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
