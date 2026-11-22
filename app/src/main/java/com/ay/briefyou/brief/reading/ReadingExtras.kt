@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.ay.briefyou.brief.reading

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.unit.sp
import coil3.size.Precision
import coil3.size.Scale
import kotlin.math.roundToInt
import com.ay.briefyou.R
import com.ay.briefyou.domain.model.article.ArticleWithFeed
import com.ay.briefyou.infrastructure.preference.LocalReadingFonts
import com.ay.briefyou.infrastructure.preference.LocalReadingTextAlign
import com.ay.briefyou.infrastructure.preference.LocalReadingTextFontSize
import com.ay.briefyou.infrastructure.preference.LocalReadingTextHorizontalPadding
import com.ay.briefyou.infrastructure.preference.LocalReadingTextLineHeight
import com.ay.briefyou.infrastructure.preference.ReadingFontsPreference
import com.ay.briefyou.infrastructure.preference.ReadingTextAlignPreference
import com.ay.briefyou.infrastructure.preference.ReadingTextFontSizePreference
import com.ay.briefyou.infrastructure.preference.ReadingTextHorizontalPaddingPreference
import com.ay.briefyou.infrastructure.preference.ReadingTextLineHeightPreference
import com.ay.briefyou.ui.component.base.RYAsyncImage
import com.ay.briefyou.ui.component.base.SIZE_1000

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
            next.article.img?.takeIf { !com.ay.briefyou.brief.media.MediaFiles.isMedia(it) }?.let { img ->
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

    val fonts =
        ReadingFontsPreference.values.filter { it != ReadingFontsPreference.External || font == ReadingFontsPreference.External }
    val families = remember(fonts) { fonts.map { runCatching { it.asFontFamily(context) }.getOrNull() } }
    val family = families.getOrNull(fonts.indexOf(font))

    val sheetState = com.ay.briefyou.brief.ui.rememberFullSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    // Always the full height, so the sheet doesn't change size with its content (and
                    // doesn't jump while a slider or the font changes the preview above it).
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
        ) {
            com.ay.briefyou.brief.ui.SheetTitle(stringResource(R.string.text_settings))

            // A live preview that follows the article's own style sheet (WebViewStyle): the font,
            // size, boldness, letter spacing, line spacing (the article spaces lines at 1.5 times
            // the chosen value, and headings at most 1.2), the side margins (scaled down to the
            // preview's width) and the alignment, without hyphenation, as the article has none.
            val letterSpacing = com.ay.briefyou.infrastructure.preference.LocalReadingTextLetterSpacing.current
            val bold = com.ay.briefyou.infrastructure.preference.LocalReadingTextBold.current.value
            val articleLine = lineHeight * 1.5f
            val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val sidePadding = (8f + padding * (screenWidth - 48f) / screenWidth).dp
            Surface(
                shape = RoundedCornerShape(com.ay.briefyou.brief.ui.Corner.Card),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = sidePadding, vertical = 16.dp)) {
                    Text(
                        stringResource(R.string.text_settings_preview_title),
                        fontFamily = family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (fontSize + 2).sp,
                        letterSpacing = letterSpacing.sp,
                        lineHeight = ((fontSize + 2) * minOf(1.2f, articleLine)).sp,
                    )
                    Text(
                        stringResource(R.string.text_settings_preview_text),
                        fontFamily = family,
                        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = fontSize.sp,
                        letterSpacing = letterSpacing.sp,
                        lineHeight = (fontSize * articleLine).sp,
                        textAlign =
                            if (align == ReadingTextAlignPreference.Justify) androidx.compose.ui.text.style.TextAlign.Justify
                            else androidx.compose.ui.text.style.TextAlign.Start,
                        // Lines broken one after another like the article's (Compose's default evens
                        // out the lines, which made Left look justified and Justify look left).
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                lineBreak = androidx.compose.ui.text.style.LineBreak.Simple,
                                hyphens = androidx.compose.ui.text.style.Hyphens.None,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(com.ay.briefyou.brief.ui.Group.Gap)) {
                SettingSlider(
                    icon = Icons.Rounded.FormatSize,
                    label = stringResource(R.string.text_settings_size),
                    value = fontSize.toFloat(),
                    valueLabel = "$fontSize",
                    range = 12f..28f,
                    steps = 15,
                    shape = com.ay.briefyou.brief.ui.groupShape(0, 3),
                ) {
                    ReadingTextFontSizePreference.put(context, scope, it.roundToInt())
                }
                SettingSlider(
                    icon = Icons.Rounded.FormatLineSpacing,
                    label = stringResource(R.string.text_settings_line_height),
                    value = lineHeight,
                    valueLabel = "%.1f×".format(lineHeight),
                    range = 0.8f..2f,
                    steps = 11,
                    shape = com.ay.briefyou.brief.ui.groupShape(1, 3),
                ) {
                    ReadingTextLineHeightPreference.put(context, scope, (it * 10).roundToInt() / 10f)
                }
                SettingSlider(
                    icon = Icons.Rounded.CropFree,
                    label = stringResource(R.string.text_settings_padding),
                    value = padding.toFloat(),
                    valueLabel = "$padding dp",
                    range = 0f..48f,
                    steps = 11,
                    shape = com.ay.briefyou.brief.ui.groupShape(2, 3),
                ) {
                    ReadingTextHorizontalPaddingPreference.put(context, scope, it.roundToInt())
                }
            }

            Spacer(Modifier.height(16.dp))
            com.ay.briefyou.brief.ui.SheetSection(stringResource(R.string.text_settings_font))
            com.ay.briefyou.brief.ui.JoinedChoices(
                count = fonts.size,
                selected = fonts.indexOf(font),
                onPick = { fonts[it].put(context, scope) },
            ) { i, on ->
                // Each name in its own font, so you can see what you'd get.
                com.ay.briefyou.brief.ui.JoinedLabel(
                    // "Google Sans Flex" was too long for a third of the width and ended with "…".
                    if (fonts[i] == ReadingFontsPreference.GoogleSans) stringResource(R.string.text_settings_font_google)
                    else fonts[i].toDesc(context),
                    on,
                    androidx.compose.ui.text.TextStyle(fontFamily = families[i]),
                )
            }

            Spacer(Modifier.height(16.dp))
            com.ay.briefyou.brief.ui.SheetSection(stringResource(R.string.text_settings_alignment))
            val aligns = listOf(ReadingTextAlignPreference.Start, ReadingTextAlignPreference.Justify)
            val alignLabels =
                listOf(stringResource(R.string.text_settings_align_left), stringResource(R.string.text_settings_align_justify))
            com.ay.briefyou.brief.ui.JoinedChoices(
                count = aligns.size,
                selected = aligns.indexOf(align),
                onPick = { aligns[it].put(context, scope) },
            ) { i, on ->
                Icon(
                    if (i == 0) Icons.AutoMirrored.Rounded.FormatAlignLeft else Icons.Rounded.FormatAlignJustify,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                com.ay.briefyou.brief.ui.JoinedLabel(alignLabels[i], on)
            }

            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                // The sheet slides away first, then the page opens: both at once (the sheet's many
                // sliders and the style page's list being built) made the opening stutter.
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onAllSettings() } },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.text_settings_all), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** One setting of the Text sheet's joined group: icon, name, the value in a pill, then its slider. */
@Composable
private fun SettingSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    shape: androidx.compose.ui.graphics.Shape,
    onChange: (Float) -> Unit,
) {
    // Follows the finger right away; the setting is written as it moves, so the article updates.
    var current by remember(value) { mutableStateOf(value) }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier.size(34.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        valueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            com.ay.briefyou.brief.ui.BriefSlider(
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
}
