@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.ash.reader.ui.page.home.reading

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.size.Precision
import coil.size.Scale
import kotlin.math.abs
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
import me.ash.reader.ui.page.home.reading.summary.SummaryRequest
import me.ash.reader.ui.page.home.reading.summary.SummaryUiState
import me.ash.reader.ui.page.home.reading.summary.SummaryViewModel

/*
 * Reading page additions (an experiment, on its own branch): the quick text settings sheet, the
 * AI key points card, the Explain chip for selected text, the next article card at the end, and
 * swiping sideways between articles.
 *
 * Nothing here changes how existing settings are stored: the text sheet writes the same settings
 * as the reading style pages, and the one new switch (key points) lives in its own small file.
 */

/** The reading page's own new switches, kept apart from the app's settings and backups. */
object ReadingExperiments {
    private const val FILE = "reading_experiments"
    private const val KEY_POINTS = "key_points"

    fun keyPoints(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_POINTS, false)

    fun setKeyPoints(context: Context, value: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_POINTS, value) }
}

/**
 * Swiping sideways over the article: left for the next article, right for the previous one. It
 * only watches (never takes the touch from the article), and only counts quick, clearly sideways
 * swipes that don't start at the screen edges (those belong to the back gesture).
 */
fun Modifier.swipeBetweenArticles(onNext: (() -> Unit)?, onPrevious: (() -> Unit)?): Modifier =
    pointerInput(onNext, onPrevious) {
        val edge = 32.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            var last = down.position
            var lastTime = down.uptimeMillis
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                last = change.position
                lastTime = change.uptimeMillis
                if (!change.pressed) break
            }
            val dx = last.x - down.position.x
            val dy = last.y - down.position.y
            val quick = lastTime - down.uptimeMillis < 600
            if (quick && abs(dx) > size.width * 0.3f && abs(dx) > abs(dy) * 2.5f) {
                if (dx < 0) onNext?.invoke() else onPrevious?.invoke()
            }
        }
    }

/** At the end of an article: the next one, and a way back to the list. */
@Composable
fun NextArticleCard(next: ArticleWithFeed?, onNext: () -> Unit, onBackToList: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (next != null) {
            Text(
                text = stringResource(R.string.reading_up_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Surface(
                onClick = onNext,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = next.feed.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)),
                            data = me.ash.reader.infrastructure.rss.FeedContent.listImage(img, next.article.link),
                            scale = Scale.FILL,
                            precision = Precision.INEXACT,
                            size = SIZE_1000,
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.reading_all_caught_up),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        FilledTonalButton(onClick = onBackToList, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.reading_back_to_list))
        }
    }
}

/**
 * AI key points above the article (when switched on in the text sheet). Nothing runs until you tap
 * Show, so opening articles stays as fast as before.
 */
@Composable
fun KeyPointsCard(articleId: String, title: String?, html: String) {
    val viewModel: SummaryViewModel = hiltViewModel(key = "keypoints-$articleId")
    if (!viewModel.hasModel()) return
    var shown by remember(articleId) { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.reading_key_points),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!shown) {
                    TextButton(
                        onClick = {
                            shown = true
                            viewModel.start(SummaryRequest.Article(articleId, title, html))
                        }
                    ) {
                        Text(stringResource(R.string.reading_key_points_show))
                    }
                }
            }
            if (shown) {
                Spacer(Modifier.height(8.dp))
                when (val s = state) {
                    is SummaryUiState.Streaming ->
                        Text(
                            text = s.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    is SummaryUiState.Error ->
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    else -> LoadingIndicator(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

/** Shown above the bottom bar while text is selected: explain it with the on-device model. */
@Composable
fun ExplainChip(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(50),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reading_explain), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * The quick text settings, right over the article (the Style button). It writes the same settings
 * as the reading style pages, so the article updates live and nothing new is stored, except the
 * key points switch.
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
    var keyPoints by remember { mutableStateOf(ReadingExperiments.keyPoints(context)) }

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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

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
            SwitchRow(
                label = stringResource(R.string.text_settings_key_points),
                desc = stringResource(R.string.text_settings_key_points_desc),
                checked = keyPoints,
            ) {
                keyPoints = it
                ReadingExperiments.setKeyPoints(context, it)
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
