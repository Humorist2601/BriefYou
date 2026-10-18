package me.ash.reader.ui.page.home.reading.summary

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue

/**
 * Bottom sheet that streams an on-device summary of one article, or a gist of a whole list.
 *
 * It looks like the app's other sheets (the sort sheet): a drag handle, a large title with a line
 * under it, then the content. The key points are a group of rounded tonal cards (Material 3
 * Expressive's connected list) that slide in one by one as they are written, and "Why it matters"
 * stands out in its own accent card. The actions sit at the bottom as a connected pair of buttons.
 *
 * The sheet opens at 70% of the screen height and keeps that size while the text is written; the
 * content scrolls inside it. Drag it down, press Back or tap outside to close it.
 *
 * @param requestKey changes when the thing being summarized changes (article id, filter).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SummarySheet(
    requestKey: Any,
    request: SummaryRequest,
    heading: String,
    onDismissRequest: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel(key = "ai-summary"),
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateValue()
    val gistMarkedRead = viewModel.gistMarkedRead.collectAsStateValue()
    val ttsState = viewModel.textToSpeechManager.stateFlow.collectAsStateValue()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArticle = request is SummaryRequest.Article

    LaunchedEffect(requestKey) { viewModel.start(request) }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importModel)
        }

    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    // The same side padding as the reading page, so the summary lines up with the article text.
    val horizontalPadding = LocalReadingTextHorizontalPadding.current.dp.coerceAtLeast(16.dp)

    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .height(sheetHeight)
                    .padding(horizontal = horizontalPadding)
                    .navigationBarsPadding()
        ) {
            // Header: like the sort sheet, a large title, a line about where it comes from, and
            // a divider.
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            val count =
                when (state) {
                    is SummaryUiState.Streaming -> state.articleCount
                    is SummaryUiState.Thinking -> state.articleCount
                    else -> null
                }
            Text(
                text =
                    if (count != null) pluralStringResource(R.plurals.ai_gist_based_on, count, count)
                    else stringResource(R.string.ai_summary_on_device),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            val scrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        // Scrolling the points never moves the sheet, even at the top or bottom of
                        // the list: only the handle and title above the divider drag it.
                        .nestedScroll(KeepScrollInside)
                        // The same scroll thumb as the reading page, also shown while text streams in.
                        .drawVerticalScrollIndicator(scrollState, showWhenContentChanges = true)
                        .verticalScroll(scrollState)
            ) {
                when (state) {
                    SummaryUiState.NeedsModel -> {
                        Text(
                            stringResource(R.string.ai_summary_needs_model),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            GemmaSummarizer.MODEL_PAGE.toUri(),
                                        )
                                    )
                                },
                            ) {
                                Text(stringResource(R.string.ai_summary_get_model))
                            }
                            Button(
                                shapes = ButtonDefaults.shapes(),
                                onClick = { picker.launch(arrayOf("*/*")) },
                            ) {
                                Text(stringResource(R.string.ai_summary_choose_file))
                            }
                        }
                    }

                    is SummaryUiState.Importing -> {
                        Text(
                            stringResource(R.string.ai_summary_importing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        val progress = state.progress
                        if (progress != null) {
                            LinearWavyProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // Loading: the Expressive shape-morphing indicator, centred, with a line under it.
                    is SummaryUiState.Thinking -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.ai_summary_thinking),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is SummaryUiState.Streaming ->
                        SummaryCards(text = state.text, streaming = !state.done)

                    // An error looks like the loading state: centred, a 56 dp mark, a line under it,
                    // then Try again.
                    is SummaryUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(56.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::retry) {
                                Text(stringResource(R.string.ai_summary_retry))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Actions at the bottom, always in reach. They appear (disabled) as soon as the
            // summary starts, so nothing shifts when it is finished.
            val streaming = state as? SummaryUiState.Streaming
            val done = streaming?.done == true
            val actionCount =
                streaming?.articleCount ?: (state as? SummaryUiState.Thinking)?.articleCount
            // The buttons don't drag the sheet either (only the handle and title above the divider).
            if (streaming != null || state is SummaryUiState.Thinking) Column(
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures { change, _ -> change.consume() }
                    }
            ) {
                // A small gap between the points and the buttons, so the points don't run
                // straight into them as they scroll.
                Spacer(Modifier.height(12.dp))
                if (!isArticle && actionCount != null) {
                    val marked = gistMarkedRead
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = viewModel::markGistAsRead,
                        enabled = done && marked == null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(
                            Icons.Outlined.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            if (marked == null)
                                pluralStringResource(
                                    R.plurals.ai_gist_mark_read,
                                    actionCount,
                                    actionCount,
                                )
                            else pluralStringResource(R.plurals.ai_gist_marked_read, marked, marked)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // A connected pair: one pill split in two, round on the outside.
                val reading =
                    ttsState !is TextToSpeechManager.State.Idle &&
                        ttsState !is TextToSpeechManager.State.Error
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilledTonalButton(
                        onClick = viewModel::regenerate,
                        enabled = done,
                        shape = RoundedCornerShape(50, 8, 8, 50),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.ai_summary_regenerate))
                    }
                    FilledTonalButton(
                        onClick = { streaming?.let { viewModel.readAloud(it.text, heading) } },
                        enabled = done,
                        shape = RoundedCornerShape(8, 50, 50, 8),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        // While reading: Stop on the Expressive loading shape, like the reading
                        // bar's read aloud button.
                        if (reading) {
                            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = LocalContentColor.current.copy(alpha = 0.22f),
                                )
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        }
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(
                                if (reading) R.string.ai_stop_reading else R.string.ai_read_aloud
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Takes whatever scrolling the points have left over (at the top or bottom of the list, or when
 * they all fit), so it never reaches the sheet and moves it.
 */
private val KeepScrollInside =
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = available

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            available
    }

private val BulletLine = Regex("""^([•\-–*]|\d{1,2}[.)])\s+(.*)$""")
private val WhyItMatters = Regex("""^\**\s*why it matters\s*\**\s*:\s*\**\s*(.*)$""", RegexOption.IGNORE_CASE)

/** One piece of a summary: a key point, the "why it matters" line, or a plain paragraph. */
private sealed interface SummaryPart {
    data class Point(val text: String) : SummaryPart

    data class Why(val text: String) : SummaryPart

    data class Paragraph(val text: String) : SummaryPart
}

private fun parseSummary(text: String): List<SummaryPart> =
    text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            WhyItMatters.matchEntire(line)?.let { SummaryPart.Why(it.groupValues[1]) }
                ?: BulletLine.matchEntire(line)?.let { SummaryPart.Point(it.groupValues[2]) }
                ?: SummaryPart.Paragraph(line)
        }

/**
 * The summary as cards. Key points in a row form one group of tonal cards (big outer corners, small
 * gaps between them); "Why it matters" is its own accent card with a light bulb. Each new piece
 * slides in as it is written; a cursor marks the end while text is still coming.
 */
@Composable
private fun SummaryCards(text: String, streaming: Boolean) {
    val parts = remember(text) { parseSummary(text) }
    // Split into runs, so a run of points becomes one connected group.
    val runs =
        remember(parts) {
            buildList<List<Pair<Int, SummaryPart>>> {
                var current = mutableListOf<Pair<Int, SummaryPart>>()
                parts.forEachIndexed { index, part ->
                    val joinsRun =
                        part is SummaryPart.Point && current.lastOrNull()?.second is SummaryPart.Point
                    if (!joinsRun && current.isNotEmpty()) {
                        add(current)
                        current = mutableListOf()
                    }
                    current += index to part
                }
                if (current.isNotEmpty()) add(current)
            }
        }
    val style = MaterialTheme.typography.bodyLarge
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        runs.forEach { run ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                run.forEachIndexed { position, (index, part) ->
                    val cursor = if (streaming && index == parts.lastIndex) " ▍" else ""
                    key(index) {
                        SlideIn {
                            when (part) {
                                is SummaryPart.Point ->
                                    PointCard(
                                        text = part.text + cursor,
                                        shape = groupShape(position, run.size),
                                    )
                                is SummaryPart.Why -> WhyCard(text = part.text + cursor)
                                is SummaryPart.Paragraph ->
                                    Text(
                                        text = part.text + cursor,
                                        style = style,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Fades and slides each card in once, when it first appears. */
@Composable
private fun SlideIn(content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    ) {
        content()
    }
}

/** Big corners on the outside of a group, small ones between its cards. */
private fun groupShape(position: Int, size: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    val top = if (position == 0) outer else inner
    val bottom = if (position == size - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun PointCard(text: String, shape: RoundedCornerShape) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // A small accent dot, level with the first line of the text.
            Box(modifier = Modifier.padding(top = 8.dp).size(8.dp).background(
                MaterialTheme.colorScheme.primary,
                CircleShape,
            ))
            Spacer(Modifier.width(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WhyCard(text: String) {
    Surface(
        // The same colour as the top boxes on the AI model and Filters settings pages.
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ai_why_it_matters),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
