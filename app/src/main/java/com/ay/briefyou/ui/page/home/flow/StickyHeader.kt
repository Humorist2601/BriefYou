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

@Composable
fun StickyHeader(
    dateString: String,
    isShowFeedIcon: Boolean,
) {
    // Brief You: no line under the pinned day; articles pass under it and the page's own fade does the rest.
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.padding(start = 16.dp).padding(top = 8.dp, bottom = 4.dp),
            text = dateString.uppercase(), // Brief You: tracked capitals, like Home's section labels
            color = MaterialTheme.colorScheme.primary,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
        )
    }
}
