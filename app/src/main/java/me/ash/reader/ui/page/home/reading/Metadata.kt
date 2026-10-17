package me.ash.reader.ui.page.home.reading

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingTitleAlign
import me.ash.reader.infrastructure.preference.LocalReadingTitleBold
import me.ash.reader.infrastructure.preference.LocalReadingTitleUpperCase
import me.ash.reader.ui.ext.formatAsString
import me.ash.reader.ui.ext.requiresBidi
import me.ash.reader.ui.theme.applyTextDirection
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Metadata(
    feedName: String,
    title: String,
    publishedDate: Date,
    modifier: Modifier = Modifier,
    author: String? = null,
    readingMinutes: Int? = null,
) {
    val context = LocalContext.current
    val titleBold = LocalReadingTitleBold.current
    val titleUpperCase = LocalReadingTitleUpperCase.current
    val titleAlign = LocalReadingTitleAlign.current.toTextAlign()
    val dateString =
        remember(publishedDate) { publishedDate.formatAsString(context, atHourMinute = true) }
    val readingLabel = readingMinutes?.let { stringResource(R.string.reading_time_short, it) }
    val fontFamily = LocalReadingFonts.current.asFontFamily(context)

    val titleUpperCaseString by remember { derivedStateOf { title.uppercase() } }

    val labelColor = MaterialTheme.colorScheme.outline.copy(alpha = .7f)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(12.dp)
    ) {
        // Expressive header: the feed, the date and the reading time as chips.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    6.dp,
                    when (titleAlign) {
                        TextAlign.Center -> Alignment.CenterHorizontally
                        TextAlign.End, TextAlign.Right -> Alignment.End
                        else -> Alignment.Start
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HeaderChip(
                text = feedName,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            HeaderChip(text = dateString)
            readingLabel?.let { HeaderChip(text = it) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = if (titleUpperCase.value) titleUpperCaseString else title,
            color = MaterialTheme.colorScheme.onSurface,
            style =
                MaterialTheme.typography.headlineLarge
                    .merge(
                        fontFamily = fontFamily,
                        fontWeight = if (titleBold.value) FontWeight.Bold else FontWeight.Medium,
                    )
                    .applyTextDirection(requiresBidi = title.requiresBidi()),
            textAlign = titleAlign,
        )
        Spacer(modifier = Modifier.height(4.dp))
        author?.let {
            if (it.isNotEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = it,
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium.merge(fontFamily = fontFamily),
                    textAlign = titleAlign,
                )
            }
        }
    }
}

/** A small rounded label in the header (feed, date, reading time). */
@Composable
private fun HeaderChip(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
