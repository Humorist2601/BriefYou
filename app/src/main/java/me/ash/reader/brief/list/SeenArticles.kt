package me.ash.reader.brief.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import me.ash.reader.ui.page.home.flow.CONTENT_TYPE_ARTICLE

/**
 * The articles that have been on screen in this list, for "mark as read on scroll". Read You
 * marked everything above the first visible article, including articles a sync had just added
 * at the top, which were never seen: "random" articles were marked read (Read You #1274).
 */
@Composable
fun rememberSeenArticles(listState: LazyListState, key: Any): Set<String> {
    val seen = remember(key) { HashSet<String>() }
    LaunchedEffect(listState, key) {
        snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .filter { it.contentType == CONTENT_TYPE_ARTICLE }
                    .mapNotNull { it.key as? String }
            }
            .collect { seen.addAll(it) }
    }
    return seen
}
