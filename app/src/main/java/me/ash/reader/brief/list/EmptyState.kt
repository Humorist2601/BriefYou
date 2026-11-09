package me.ash.reader.brief.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.delay
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.general.Filter

/**
 * The app's one empty state: a big cookie with the page's icon, a short line and a hint. Shown on
 * an article list with nothing in it (Starred with nothing starred, a caught-up Unread list, a
 * feed with no articles yet), not while searching.
 */
@Composable
fun EmptyListState(pagingItems: LazyPagingItems<ArticleFlowItem>?, filter: Filter, searching: Boolean) {
    val empty =
        pagingItems != null && !searching && pagingItems.itemCount == 0 &&
            pagingItems.loadState.refresh is LoadState.NotLoading
    // A moment's wait, so it doesn't flash while a list is still loading.
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(empty) {
        show = false
        if (empty) {
            delay(500)
            show = true
        }
    }
    val (icon, title, text) =
        when {
            filter.isStarred() -> Triple(Icons.Rounded.StarOutline, R.string.empty_starred_title, R.string.empty_starred_text)
            filter.isUnread() -> Triple(Icons.Rounded.Check, R.string.empty_unread_title, R.string.empty_unread_text)
            else -> Triple(Icons.AutoMirrored.Rounded.Subject, R.string.empty_all_title, R.string.empty_all_text)
        }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon, stringResource(title), stringResource(text))
        }
    }
}

/** A big cookie with [icon], a [title] and a short [text] under it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier.size(96.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
