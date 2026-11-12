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
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.onDark

@Composable
fun StickyHeader(
    dateString: String,
    isShowFeedIcon: Boolean,
    articleListTonalElevation: Int,
    dayCount: me.ash.reader.brief.list.DayCount? = null, // Brief You: "3 articles" on the right
    line: Boolean = false, // Brief You: articles are passing under the pinned day
) {
    // Brief You: a faint hairline under the pinned day only while articles pass under it.
    val lineAlpha by androidx.compose.animation.core.animateFloatAsState(if (line) 1f else 0f, label = "dayLine") // Brief You
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) { // Brief You
    // Brief You: once articles pass under it, the pinned day takes the top bar's colour (the
    // lifted one when the bar has tonal elevation), so the two read as one block.
    val listColor = MaterialTheme.colorScheme.surfaceColorAtElevation(articleListTonalElevation.dp) onDark MaterialTheme.colorScheme.surface // Brief You
    val barColor = with(MaterialTheme.colorScheme) { if (me.ash.reader.infrastructure.preference.LocalFlowTopBarTonalElevation.current.value > 0) surfaceContainer else surface } // Brief You
    val headerColor by androidx.compose.animation.animateColorAsState(if (line) barColor else listColor, label = "dayColor") // Brief You
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerColor), // Brief You
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
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f * lineAlpha), // Brief You: lighter than a divider
        modifier = Modifier.background(headerColor), // Brief You
    ) // Brief You
    } // Brief You
}

