package me.ash.reader.ui.page.home.reading.summary

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.ui.ext.collectAsStateValue

/**
 * Bottom sheet that streams an on-device summary or translation of one article, or a gist of a
 * whole list, with read-aloud and follow-up questions.
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
    val mode = viewModel.mode.collectAsStateValue()
    val chat = viewModel.chat.collectAsStateValue()
    val gistMarkedRead = viewModel.gistMarkedRead.collectAsStateValue()
    val ttsState = viewModel.textToSpeechManager.stateFlow.collectAsStateValue()
    // Skipping the half-expanded stop keeps the sheet from bouncing between heights while text
    // streams in and the content grows past the screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArticle = request is SummaryRequest.Article

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .imePadding()
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
                when {
                    count != null -> pluralStringResource(R.plurals.ai_gist_based_on, count, count)
                    state is SummaryUiState.Streaming && state.fromCache ->
                        stringResource(R.string.ai_saved_on_device)
                    else -> stringResource(R.string.ai_summary_on_device)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 36.dp),
            )

            if (isArticle && state !is SummaryUiState.NeedsModel && state !is SummaryUiState.Importing) {
                Spacer(Modifier.height(16.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SheetMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { viewModel.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(index, SheetMode.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (m) {
                                        SheetMode.Summary -> R.string.ai_summary
                                        SheetMode.Translate -> R.string.ai_translate
                                    }
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            when (state) {
                SummaryUiState.NeedsModel -> {
                    Text(
                        stringResource(R.string.ai_summary_needs_model),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (viewModel.lowRam) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.ai_low_ram_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val page =
                                    if (viewModel.lowRam) GemmaSummarizer.SMALL_MODEL_PAGE
                                    else GemmaSummarizer.MODEL_PAGE
                                context.startActivity(Intent(Intent.ACTION_VIEW, page.toUri()))
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (viewModel.lowRam) R.string.ai_get_small_model
                                    else R.string.ai_summary_get_model
                                )
                            )
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
                        LoadingIndicator(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.ai_summary_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SummaryUiState.Streaming -> {
                    Column {
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
                                OutlinedButton(onClick = viewModel::regenerate) {
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

                            if (!isArticle && state.articleCount != null) {
                                Spacer(Modifier.height(12.dp))
                                val marked = gistMarkedRead
                                Button(
                                    onClick = viewModel::markGistAsRead,
                                    enabled = marked == null,
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
                                                state.articleCount,
                                                state.articleCount,
                                            )
                                        else
                                            pluralStringResource(
                                                R.plurals.ai_gist_marked_read, marked, marked)
                                    )
                                }
                            }

                            chat.forEach { qa -> QaBubble(qa) }
                            Spacer(Modifier.height(16.dp))
                            AskField(
                                enabled = chat.lastOrNull()?.done != false,
                                onAsk = viewModel::ask,
                            )
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
                    Button(onClick = viewModel::retry) {
                        Text(stringResource(R.string.ai_summary_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun QaBubble(qa: QA) {
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
        ) {
            Text(
                qa.question,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
    ) {
        Text(
            when {
                qa.answer.isEmpty() && !qa.done -> "…"
                qa.done -> qa.answer
                else -> qa.answer + " ▍"
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (qa.error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AskField(enabled: Boolean, onAsk: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank() && enabled) {
            onAsk(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.ai_ask_placeholder)) },
        shape = RoundedCornerShape(28.dp),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = send, enabled = enabled && text.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.ai_ask_send),
                )
            }
        },
    )
}
