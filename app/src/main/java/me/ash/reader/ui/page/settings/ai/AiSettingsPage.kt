package me.ash.reader.ui.page.settings.ai

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.ModelState
import me.ash.reader.ui.component.base.Banner
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

private enum class PromptKind {
    Article,
    Gist,
}

@Composable
fun AiSettingsPage(onBack: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val model = viewModel.modelState.collectAsStateValue()
    val articlePrompt = viewModel.articlePrompt.collectAsStateValue()
    val gistPrompt = viewModel.gistPrompt.collectAsStateValue()
    var editing by rememberSaveable { mutableStateOf<PromptKind?>(null) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importModel)
        }

    val statusText =
        when (model) {
            ModelState.Missing -> stringResource(R.string.ai_settings_status_missing)
            is ModelState.Ready ->
                stringResource(
                    R.string.ai_settings_status_ready,
                    Formatter.formatShortFileSize(context, model.sizeBytes),
                )
            is ModelState.Importing ->
                stringResource(
                    R.string.ai_settings_status_importing,
                    model.progress?.let { "${(it * 100).toInt()}%" } ?: "",
                )
            is ModelState.ImportFailed ->
                stringResource(R.string.ai_settings_status_failed, model.message)
        }

    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.ai_settings), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                    Banner(
                        title = statusText,
                        desc = stringResource(R.string.ai_settings_privacy),
                        icon = Icons.Outlined.Memory,
                    )
                    if (model is ModelState.Importing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val p = model.progress
                        if (p != null) {
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.ai_settings_model),
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_settings_download),
                        desc = stringResource(R.string.ai_settings_download_desc),
                        icon = Icons.Outlined.CloudDownload,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, GemmaSummarizer.MODEL_PAGE.toUri())
                            )
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_settings_small_model),
                        desc =
                            stringResource(
                                if (viewModel.lowRam) R.string.ai_settings_small_model_desc_low
                                else R.string.ai_settings_small_model_desc,
                                Formatter.formatShortFileSize(context, viewModel.ramBytes),
                            ),
                        icon = Icons.Outlined.Speed,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, GemmaSummarizer.SMALL_MODEL_PAGE.toUri())
                            )
                        },
                    ) {}
                    SettingItem(
                        enabled = model !is ModelState.Importing,
                        title = stringResource(R.string.ai_settings_import),
                        desc = stringResource(R.string.ai_settings_import_desc),
                        icon = Icons.Outlined.FileOpen,
                        onClick = { picker.launch(arrayOf("*/*")) },
                    ) {}
                    SettingItem(
                        enabled = model is ModelState.Ready,
                        title = stringResource(R.string.ai_settings_remove),
                        desc = stringResource(R.string.ai_settings_remove_desc),
                        icon = Icons.Outlined.DeleteOutline,
                        onClick = viewModel::removeModel,
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.ai_settings_instructions),
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_settings_article_prompt),
                        desc = articlePrompt.preview(),
                        icon = Icons.Outlined.Summarize,
                        onClick = { editing = PromptKind.Article },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_settings_gist_prompt),
                        desc = gistPrompt.preview(),
                        icon = Icons.Outlined.EditNote,
                        onClick = { editing = PromptKind.Gist },
                    ) {}
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    VoiceSection(viewModel)
                }
                item { BriefingSection(viewModel) }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    when (editing) {
        PromptKind.Article ->
            PromptDialog(
                title = stringResource(R.string.ai_settings_article_prompt),
                initial = articlePrompt,
                default = GemmaSummarizer.DEFAULT_ARTICLE_PROMPT,
                onSave = viewModel::setArticlePrompt,
                onDismiss = { editing = null },
            )
        PromptKind.Gist ->
            PromptDialog(
                title = stringResource(R.string.ai_settings_gist_prompt),
                initial = gistPrompt,
                default = GemmaSummarizer.DEFAULT_GIST_PROMPT,
                onSave = viewModel::setGistPrompt,
                onDismiss = { editing = null },
            )
        null -> Unit
    }
}

@Composable
private fun PromptDialog(
    title: String,
    initial: String,
    default: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(if (text.trim() == default) null else text.trim())
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ai_settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { text = default }) {
                Text(stringResource(R.string.ai_settings_reset))
            }
        },
    )
}

private fun String.preview(): String = if (length <= 110) this else take(110).trimEnd() + "…"
