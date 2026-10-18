package me.ash.reader.brief.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import coil.size.Precision
import coil.size.Scale
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000

/*
 * Brief You's additions to the top of Read You's article (Content.kt): a large picture above the
 * title, the actual reading time, and the title lined up with the article text.
 */

/** An article split for display: its leading picture (shown large), the rest, and its length. */
@Immutable
data class BriefArticle(val hero: String?, val body: String, val minutes: Int?)

/** [content] split into a large leading picture and the rest, with the actual reading time. */
@Composable
fun rememberBriefArticle(content: String): BriefArticle =
    remember(content) {
        val (hero, body) = FeedContent.extractHero(content)
        BriefArticle(hero = hero, body = body, minutes = readingMinutes(content))
    }

/**
 * Lines the title block up with the article text: the reading text padding setting, less the
 * 12 dp the title block has inside it.
 */
@Composable
fun Modifier.briefHeadlinePadding(): Modifier {
    val textPadding = LocalReadingTextHorizontalPadding.current
    return padding(horizontal = (textPadding - 12).coerceAtLeast(0).dp)
}

/** The article's leading picture, large above the title; tap to open it. */
@Composable
fun BriefArticleHero(
    url: String,
    title: String,
    onImageClick: ((imgUrl: String, altText: String) -> Unit)?,
) {
    val corners = LocalReadingImageRoundedCorners.current
    RYAsyncImage(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 8.dp)
                .heightIn(max = 280.dp)
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(corners.dp.coerceAtLeast(16.dp)))
                .clickable { onImageClick?.invoke(url, title) },
        data = url,
        scale = Scale.FILL,
        precision = Precision.INEXACT,
        size = SIZE_1000,
        contentScale = ContentScale.Crop,
        contentDescription = title,
    )
}

/**
 * The article's feed, date and reading time as small rounded chips above the title (in place of
 * Read You's plain date and feed lines), lined up like the title.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BriefMetadataChips(
    feedName: String,
    dateString: String,
    readingMinutes: Int?,
    titleAlign: androidx.compose.ui.text.style.TextAlign,
) {
    val readingLabel =
        readingMinutes?.let {
            androidx.compose.ui.res.stringResource(me.ash.reader.R.string.reading_time_short, it)
        }
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement.spacedBy(
                6.dp,
                when (titleAlign) {
                    androidx.compose.ui.text.style.TextAlign.Center ->
                        androidx.compose.ui.Alignment.CenterHorizontally
                    androidx.compose.ui.text.style.TextAlign.End,
                    androidx.compose.ui.text.style.TextAlign.Right -> androidx.compose.ui.Alignment.End
                    else -> androidx.compose.ui.Alignment.Start
                },
            ),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        HeaderChip(
            text = feedName,
            container = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
            content = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
        )
        HeaderChip(text = dateString)
        readingLabel?.let { HeaderChip(text = it) }
    }
    androidx.compose.foundation.layout.Spacer(
        modifier = Modifier.padding(top = 10.dp)
    )
}

/** A small rounded label in the header (feed, date, reading time). */
@Composable
private fun HeaderChip(
    text: String,
    container: androidx.compose.ui.graphics.Color =
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
    content: androidx.compose.ui.graphics.Color =
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
) {
    androidx.compose.material3.Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
