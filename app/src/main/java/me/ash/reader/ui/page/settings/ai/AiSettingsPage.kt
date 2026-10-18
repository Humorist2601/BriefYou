@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.settings.ai

import androidx.compose.ui.window.DialogProperties
import me.ash.reader.ui.component.base.keyboardSafe
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.ModelState
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.component.base.Tips
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
                // Laid out like the other settings pages: sections with a title, plain setting rows,
                // and the explanation at the bottom behind an info icon (as on the account page).
                item {
                    DisplayText(text = stringResource(R.string.ai_settings), desc = "")
                    Spacer(modifier = Modifier.height(8.dp))
                    // At a glance, like the tiles at the top of Filters: one tile with where the
                    // model stands (missing, importing, imported with its size, or failed).
                    ModelTile(model = model)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.ai_settings_model),
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_settings_download),
                        desc = stringResource(R.string.ai_settings_download_desc),
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, GemmaSummarizer.MODEL_PAGE.toUri())
                            )
                        },
                    ) {}
                    SettingItem(
                        enabled = model !is ModelState.Importing,
                        title = stringResource(R.string.ai_settings_import),
                        desc = stringResource(R.string.ai_settings_import_desc),
                        onClick = { picker.launch(arrayOf("*/*")) },
                    ) {}
                    SettingItem(
                        enabled = model is ModelState.Ready,
                        title = stringResource(R.string.ai_settings_remove),
                        desc = stringResource(R.string.ai_settings_remove_desc),
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
                        onClick = { editing = PromptKind.Article },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_settings_gist_prompt),
                        onClick = { editing = PromptKind.Gist },
                    ) {}
                }
                item {
                    Tips(text = stringResource(R.string.ai_settings_privacy))
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
        modifier = Modifier.imePadding(),
        properties = DialogProperties().keyboardSafe(),
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

/**
 * The model at a glance: one big tonal tile like the ones at the top of Filters. Imported: its
 * size. Importing: how far, with a wavy bar. Missing or failed: what to do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelTile(model: ModelState) {
    val context = LocalContext.current
    val failed = model is ModelState.ImportFailed
    Surface(
        color =
            if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor =
            if (failed) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Icon(
                imageVector =
                    when (model) {
                        is ModelState.Ready -> Icons.Outlined.AutoAwesome
                        is ModelState.Importing -> Icons.Outlined.Downloading
                        is ModelState.ImportFailed -> Icons.Outlined.ErrorOutline
                        ModelState.Missing -> Icons.Outlined.CloudOff
                    },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text =
                    when (model) {
                        is ModelState.Ready -> Formatter.formatShortFileSize(context, model.sizeBytes)
                        is ModelState.Importing ->
                            model.progress?.let { "${(it * 100).toInt()}%" }
                                ?: stringResource(R.string.ai_model_tile_importing)
                        is ModelState.ImportFailed -> stringResource(R.string.ai_model_tile_failed)
                        ModelState.Missing -> stringResource(R.string.ai_model_tile_missing)
                    },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    when (model) {
                        is ModelState.Ready -> stringResource(R.string.ai_model_tile_ready_label)
                        is ModelState.Importing -> stringResource(R.string.ai_model_tile_importing_label)
                        is ModelState.ImportFailed -> model.message
                        ModelState.Missing -> stringResource(R.string.ai_model_tile_missing_label)
                    },
                style = MaterialTheme.typography.labelLarge,
            )
            if (model is ModelState.Importing) {
                Spacer(Modifier.height(12.dp))
                val p = model.progress
                if (p != null) {
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
