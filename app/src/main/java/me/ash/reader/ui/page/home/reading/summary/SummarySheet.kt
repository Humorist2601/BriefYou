package me.ash.reader.ui.page.home.reading.summary

import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Close
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.ui.component.ExpressiveBadge
import me.ash.reader.ui.ext.collectAsStateValue

/**
 * Bottom sheet that streams an on-device summary of one article, or a gist of a whole list.
 *
 * The sheet opens at 60% of the screen height and stays that size: it does not grow as the text
 * streams in, and the text area inside it scrolls. Horizontal padding matches the reading page.
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

    // Brief You: no drag gestures on this sheet, and a fixed height. It opens at 60% of the screen
    // and never grows or expands to full screen; the summary text scrolls inside it. It closes with
    // the ✕ button, Back, or a tap outside.
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val horizontalPadding = LocalReadingTextHorizontalPadding.current.dp
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        // No drag handle: the sheet isn't draggable, it closes with ✕, Back or a tap outside.
        dragHandle = null,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .height(sheetHeight)
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 24.dp)
                    .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveBadge(Icons.Outlined.AutoAwesome)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(heading, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    val count =
                        when (state) {
                            is SummaryUiState.Streaming -> state.articleCount
                            is SummaryUiState.Thinking -> state.articleCount
                            else -> null
                        }
                    Text(
                        when {
                            count != null ->
                                pluralStringResource(R.plurals.ai_gist_based_on, count, count)
                            state is SummaryUiState.Streaming && state.fromCache ->
                                stringResource(R.string.ai_saved_on_device)
                            else -> stringResource(R.string.ai_summary_on_device)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            }
            Spacer(Modifier.height(20.dp))

            // Actions sit right under the header, so they stay in view at the half-way stop. They
            // appear (disabled) as soon as text starts, so nothing shifts when generation ends.
            val streaming = state as? SummaryUiState.Streaming
            val done = streaming?.done == true
            val actionCount =
                streaming?.articleCount ?: (state as? SummaryUiState.Thinking)?.articleCount
            if (streaming != null || state is SummaryUiState.Thinking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = viewModel::regenerate,
                        enabled = done,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.ai_summary_regenerate))
                    }
                    val reading =
                        ttsState !is TextToSpeechManager.State.Idle &&
                            ttsState !is TextToSpeechManager.State.Error
                    FilledTonalButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = { streaming?.let { viewModel.readAloud(it.text) } },
                        enabled = done,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (reading) Icons.Outlined.StopCircle
                            else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(
                                if (reading) R.string.ai_stop_reading else R.string.ai_read_aloud
                            )
                        )
                    }
                }
                if (!isArticle && actionCount != null) {
                    Spacer(Modifier.height(8.dp))
                    val marked = gistMarkedRead
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = viewModel::markGistAsRead,
                        enabled = done && marked == null,
                        modifier = Modifier.fillMaxWidth(),
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
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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

                    is SummaryUiState.Thinking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.ai_summary_thinking),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is SummaryUiState.Streaming -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                state.text + if (state.done) "" else " ▍",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }

                    is SummaryUiState.Error -> {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::retry) {
                            Text(stringResource(R.string.ai_summary_retry))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
