package me.ash.reader.ui.page.home.reading.summary

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import me.ash.reader.ui.ext.collectAsStateValue

/**
 * Bottom sheet that streams an on-device summary of one article, or a gist of a whole list.
 *
 * @param requestKey changes when the thing being summarized changes (article id, filter).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val ttsState = viewModel.textToSpeechManager.stateFlow.collectAsStateValue()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(requestKey) { viewModel.start(request) }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importModel)
        }

    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(heading, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            }
            val count =
                when (state) {
                    is SummaryUiState.Streaming -> state.articleCount
                    is SummaryUiState.Thinking -> state.articleCount
                    else -> null
                }
            Text(
                if (count != null)
                    pluralStringResource(R.plurals.ai_gist_based_on, count, count)
                else stringResource(R.string.ai_summary_on_device),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp),
            )
            Spacer(Modifier.height(20.dp))

            when (state) {
                SummaryUiState.NeedsModel -> {
                    Text(
                        stringResource(R.string.ai_summary_needs_model),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, GemmaSummarizer.MODEL_PAGE.toUri())
                                )
                            }
                        ) {
                            Text(stringResource(R.string.ai_summary_get_model))
                        }
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) {
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
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                is SummaryUiState.Thinking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.ai_summary_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SummaryUiState.Streaming -> {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            state.text + if (state.done) "" else " ▍",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (state.done) {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(12.dp, Alignment.End),
                            ) {
                                OutlinedButton(onClick = viewModel::retry) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.ai_summary_regenerate))
                                }
                                val reading = ttsState !is TextToSpeechManager.State.Idle &&
                                    ttsState !is TextToSpeechManager.State.Error
                                FilledTonalButton(onClick = { viewModel.readAloud(state.text) }) {
                                    Icon(
                                        if (reading) Icons.Outlined.StopCircle
                                        else Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(
                                        stringResource(
                                            if (reading) R.string.ai_stop_reading
                                            else R.string.ai_read_aloud
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                is SummaryUiState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.ai_summary_retry))
                        }
                    }
                }
            }
        }
    }
}
