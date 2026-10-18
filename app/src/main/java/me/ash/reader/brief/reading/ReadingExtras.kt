@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.ash.reader.brief.reading

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Precision
import coil.size.Scale
import kotlin.math.roundToInt
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingTextAlign
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import me.ash.reader.infrastructure.preference.ReadingTextAlignPreference
import me.ash.reader.infrastructure.preference.ReadingTextFontSizePreference
import me.ash.reader.infrastructure.preference.ReadingTextHorizontalPaddingPreference
import me.ash.reader.infrastructure.preference.ReadingTextLineHeightPreference
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.component.base.SIZE_1000

/*
 * Reading page additions (an experiment, on its own branch): the quick text settings sheet and the
 * next article card at the end. The text sheet writes the same settings as the reading style
 * pages, so nothing new is stored.
 */

/**
 * At the end of an article: the next one. It loads its own preview and fades in once it has it
 * (and the article has had a moment to lay out), so it never flashes while the page settles.
 */
@Composable
fun NextArticleCard(
    nextId: String?,
    load: suspend (String) -> ArticleWithFeed?,
    onNext: () -> Unit,
) {
    // Loading = no answer yet; then either the next article or none (all caught up).
    val preview by
        produceState<Result<ArticleWithFeed?>?>(initialValue = null, nextId) {
            delay(SETTLE_MS)
            value = Result.success(nextId?.let { load(it) })
        }
    AnimatedVisibility(visible = preview != null, enter = fadeIn(tween(250))) {
        NextArticleContent(next = preview?.getOrNull(), onNext = onNext)
    }
}

private const val SETTLE_MS = 500L

@Composable
private fun NextArticleContent(next: ArticleWithFeed?, onNext: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        if (next != null) NextArticle(next, onNext) else AllCaughtUp()
    }
}

/** The next article: what it is and its picture; tap the card to go to it. */
@Composable
private fun NextArticle(next: ArticleWithFeed, onNext: () -> Unit) {
    Surface(
        onClick = onNext,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = EndCardHeight),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        stringResource(R.string.reading_up_next).uppercase() +
                            " · " +
                            next.feed.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = next.article.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            next.article.img?.let { img ->
                RYAsyncImage(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    data = FeedContent.listImage(img, next.article.link),
                    scale = Scale.FILL,
                    precision = Precision.INEXACT,
                    size = SIZE_1000,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** No next article: the same size card, softer, with a check on an Expressive cookie shape. */
@Composable
private fun AllCaughtUp() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = EndCardHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.reading_all_caught_up),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Both end-of-article cards are at least this tall, so they are the same size. */
private val EndCardHeight = 92.dp

/**
 * The quick text settings, right over the article (the Style button). It writes the same settings
 * as the reading style pages, so the article updates live and nothing new is stored.
 */
@Composable
fun TextSettingsSheet(onDismiss: () -> Unit, onAllSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontSize = LocalReadingTextFontSize.current
    val lineHeight = LocalReadingTextLineHeight.current
    val padding = LocalReadingTextHorizontalPadding.current
    val font = LocalReadingFonts.current
    val align = LocalReadingTextAlign.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.text_settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The same header as the other sheets (Sort, AI summary): title, 12 dp, a divider,
            // 14 dp (the column already puts 6 dp between items).
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(2.dp))

            SettingSlider(
                label = stringResource(R.string.text_settings_size),
                value = fontSize.toFloat(),
                valueLabel = "$fontSize",
                range = 12f..28f,
                steps = 15,
            ) {
                ReadingTextFontSizePreference.put(context, scope, it.roundToInt())
            }
            SettingSlider(
                label = stringResource(R.string.text_settings_line_height),
                value = lineHeight,
                valueLabel = "%.1f×".format(lineHeight),
                range = 0.8f..2f,
                steps = 11,
            ) {
                ReadingTextLineHeightPreference.put(context, scope, (it * 10).roundToInt() / 10f)
            }
            SettingSlider(
                label = stringResource(R.string.text_settings_padding),
                value = padding.toFloat(),
                valueLabel = "$padding dp",
                range = 0f..48f,
                steps = 11,
            ) {
                ReadingTextHorizontalPaddingPreference.put(context, scope, it.roundToInt())
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.text_settings_font), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingFontsPreference.values
                    .filter { it != ReadingFontsPreference.External || font == ReadingFontsPreference.External }
                    .forEach { option ->
                        ToggleButton(
                            checked = font == option,
                            onCheckedChange = { option.put(context, scope) },
                        ) {
                            Text(option.toDesc(context))
                        }
                    }
            }

            SwitchRow(
                label = stringResource(R.string.text_settings_justify),
                checked = align == ReadingTextAlignPreference.Justify,
            ) {
                (if (it) ReadingTextAlignPreference.Justify else ReadingTextAlignPreference.Start)
                    .put(context, scope)
            }

            TextButton(onClick = onAllSettings, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.text_settings_all))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    // Follows the finger right away; the setting is written as it moves, so the article updates.
    var current by remember(value) { mutableStateOf(value) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = current.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                current = it
                onChange(it)
            },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, desc: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            desc?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
