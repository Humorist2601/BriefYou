package com.ay.briefyou.ui.page.home.flow

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
import androidx.compose.ui.unit.em
import androidx.compose.runtime.getValue // Brief You

@Composable
fun StickyHeader(
    dateString: String,
    isShowFeedIcon: Boolean,
    dayCount: com.ay.briefyou.brief.list.DayCount? = null, // Brief You: "3 articles" on the right
    line: Boolean = false, // Brief You: articles are passing under the pinned day
) {
    // Brief You: a faint hairline under the pinned day only while articles pass under it.
    val lineAlpha by androidx.compose.animation.core.animateFloatAsState(if (line) 1f else 0f, label = "dayLine") // Brief You
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) { // Brief You
    // Brief You: once articles pass under it, the pinned day takes the top bar's colour (the
    // lifted one when the bar has tonal elevation), so the two read as one block.
    val listColor = MaterialTheme.colorScheme.surface // Brief You
    val barColor = MaterialTheme.colorScheme.surface // Brief You
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
            text = dateString.uppercase(), // Brief You: tracked capitals, like Home's section labels
            color = MaterialTheme.colorScheme.primary,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
        )
        if (dayCount != null && dayCount.count > 0) { // Brief You
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) // Brief You
            Text( // Brief You
                modifier = Modifier.padding(end = 24.dp).padding(top = 8.dp, bottom = 4.dp), // Brief You
                text = androidx.compose.ui.res.pluralStringResource(com.ay.briefyou.R.plurals.day_article_count, dayCount.count, dayCount.count.toString() + if (dayCount.more) "+" else ""), // Brief You
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

