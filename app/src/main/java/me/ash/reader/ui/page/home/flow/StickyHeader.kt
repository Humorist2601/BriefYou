package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue // Brief You
import androidx.compose.runtime.setValue // Brief You
import androidx.compose.ui.layout.onGloballyPositioned // Brief You
import androidx.compose.ui.layout.positionInParent // Brief You
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.onDark

@Composable
fun StickyHeader(
    dateString: String,
    isShowFeedIcon: Boolean,
    articleListTonalElevation: Int,
    dayCount: me.ash.reader.brief.list.DayCount? = null, // Brief You: "3 articles" on the right
    sticky: Boolean = false, // Brief You: pinned to the top while its day scrolls under it
) {
    // Brief You: a hairline under the day while it's pinned, so the articles passing under it
    // don't run into it; none while it scrolls with the list.
    var pinned by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) } // Brief You
    val lineAlpha by androidx.compose.animation.core.animateFloatAsState(if (sticky && pinned) 1f else 0f, label = "dayLine") // Brief You
    androidx.compose.foundation.layout.Column( // Brief You
        modifier = Modifier.fillMaxWidth().then( // Brief You
            if (sticky) Modifier.pinnedAtTop { pinned = it } else Modifier // Brief You
        ) // Brief You
    ) { // Brief You
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceColorAtElevation(articleListTonalElevation.dp)
                        onDark MaterialTheme.colorScheme.surface
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .padding(start = 24.dp) // Brief You
                .padding(top = 8.dp, bottom = 4.dp),
            text = dateString,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        if (dayCount != null && dayCount.count > 0) { // Brief You
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) // Brief You
            Text( // Brief You
                modifier = Modifier.padding(end = 24.dp).padding(top = 8.dp, bottom = 4.dp), // Brief You
                text = androidx.compose.ui.res.pluralStringResource(me.ash.reader.R.plurals.day_article_count, dayCount.count, dayCount.count.toString() + if (dayCount.more) "+" else ""), // Brief You
                color = MaterialTheme.colorScheme.onSurfaceVariant, // Brief You
                style = MaterialTheme.typography.labelMedium, // Brief You
            ) // Brief You
        } // Brief You
    }
    androidx.compose.material3.HorizontalDivider( // Brief You: on the header's colour, so nothing shows through
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = lineAlpha), // Brief You
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(articleListTonalElevation.dp) onDark MaterialTheme.colorScheme.surface), // Brief You
    ) // Brief You
    } // Brief You
}

/** Brief You: reports whether this sits at the very top of its list (where a sticky header pins). */
private fun Modifier.pinnedAtTop(onPinned: (Boolean) -> Unit): Modifier = // Brief You
    this.onGloballyPositioned { onPinned(it.positionInParent().y <= 0.5f) } // Brief You
