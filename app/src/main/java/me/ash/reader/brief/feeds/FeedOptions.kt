package me.ash.reader.brief.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.brief.ui.SheetActions
import me.ash.reader.brief.ui.SheetSection
import me.ash.reader.brief.ui.SheetTile
import me.ash.reader.domain.model.group.Group
import me.ash.reader.ui.component.base.RYSelectionChip

/**
 * A feed's settings, in the app's sheet design: how it opens and notifies as three tiles in one
 * row (lit when on), its group as one sideways row of chips, then the two actions side by side
 * ([clear], [remove]). Used by the feed's options sheet and by Add a feed (which has no actions).
 */
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
    clear: Pair<String, () -> Unit>? = null,
    remove: Pair<String, () -> Unit>? = null,
) {
    Column {
        SettingTiles(
            fullContent = fullContent,
            browser = browser,
            notification = notification,
            onFullContent = onFullContent,
            onBrowser = onBrowser,
            onNotification = onNotification,
        )
        if (showGroup) {
            Spacer(Modifier.height(20.dp))
            SheetSection(stringResource(if (isMoveToGroup) R.string.move_to_group else R.string.add_to_group))
            GroupChips(groups, selectedGroupId, onGroupClick, onAddNewGroup)
        }
        if (clear != null) {
            Spacer(Modifier.height(20.dp))
            SheetActions(clear, remove)
        }
    }
}

/**
 * Full content, In browser and Notify as tiles. With a value they switch; with null (a group's
 * sheet) they only act, and the group asks before applying to all its feeds.
 */
@Composable
fun SettingTiles(
    fullContent: Boolean?,
    browser: Boolean?,
    notification: Boolean?,
    onFullContent: () -> Unit,
    onBrowser: () -> Unit,
    onNotification: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SheetTile(Icons.AutoMirrored.Outlined.Article, stringResource(R.string.sheet_full_content), fullContent, onFullContent, Modifier.weight(1f))
        SheetTile(Icons.Outlined.OpenInBrowser, stringResource(R.string.sheet_browser), browser, onBrowser, Modifier.weight(1f))
        SheetTile(Icons.Outlined.Notifications, stringResource(R.string.sheet_notify), notification, onNotification, Modifier.weight(1f))
    }
}

/**
 * The groups as one sideways row of chips (the chosen one ticked and scrolled into view), and a
 * round + for a new group at the end.
 */
@Composable
fun GroupChips(
    groups: List<Group>,
    selectedGroupId: String,
    onGroupClick: (String) -> Unit,
    onAddNewGroup: (() -> Unit)? = null,
) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = groups.indexOfFirst { it.id == selectedGroupId }
        if (i > 0) state.scrollToItem(i)
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(groups, key = { it.id }) {
            RYSelectionChip(content = it.name, selected = it.id == selectedGroupId) { onGroupClick(it.id) }
        }
        if (onAddNewGroup != null) {
            item(key = "new") {
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
}
