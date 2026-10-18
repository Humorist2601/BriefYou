@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.brief.ai

import me.ash.reader.brief.ui.GoogleSansWideFontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import me.ash.reader.brief.ui.InWindowAlertDialog
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
import me.ash.reader.brief.ai.GemmaSummarizer
import me.ash.reader.brief.ai.ModelState
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.collectAsStateValue
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

    // Read You's settings-page scaffold (deprecated by Read You, but all of Read You's settings
    // pages still use it), so this page looks like the others.
    @Suppress("DEPRECATION")
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
                    Spacer(modifier = Modifier.height(8.dp))
                    // The model at a glance, with what to do next right inside the card.
                    ModelHero(
                        model = model,
                        onDownload = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, GemmaSummarizer.MODEL_PAGE.toUri())
                            )
                        },
                        onImport = { picker.launch(arrayOf("*/*")) },
                        onRemove = viewModel::removeModel,
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.ai_settings_instructions),
                    )
                    // The two instructions as connected cards, each with a preview of its text.
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        PromptCard(
                            icon = Icons.AutoMirrored.Outlined.Article,
                            title = stringResource(R.string.ai_settings_article_prompt),
                            text = articlePrompt,
                            edited = articlePrompt.trim() != GemmaSummarizer.DEFAULT_ARTICLE_PROMPT.trim(),
                            shape = RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp),
                            onClick = { editing = PromptKind.Article },
                        )
                        PromptCard(
                            icon = Icons.Outlined.Newspaper,
                            title = stringResource(R.string.ai_settings_gist_prompt),
                            text = gistPrompt,
                            edited = gistPrompt.trim() != GemmaSummarizer.DEFAULT_GIST_PROMPT.trim(),
                            shape = RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp),
                            onClick = { editing = PromptKind.Gist },
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
                item {
                    // Privacy, as a soft card with a lock on an Expressive shape.
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(40.dp)
                                        .clip(MaterialShapes.Cookie4Sided.toShape())
                                        .background(
                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.ai_settings_privacy),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
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
    InWindowAlertDialog(
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
 * The model at a glance, Material 3 Expressive: its name, where it stands (ready with its size,
 * importing with a wavy ring, missing, or failed) on an Expressive shape, and the next step as
 * buttons inside the card.
 */
@Composable
private fun ModelHero(
    model: ModelState,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val failed = model is ModelState.ImportFailed
    val container =
        if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content =
        if (failed) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
    val accent = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val onAccent = if (failed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                    if (model is ModelState.Importing) {
                        val p = model.progress
                        if (p != null) {
                            CircularWavyProgressIndicator(
                                progress = { p },
                                modifier = Modifier.size(84.dp),
                                color = accent,
                                trackColor = accent.copy(alpha = 0.18f),
                            )
                        } else {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(84.dp),
                                color = accent,
                                trackColor = accent.copy(alpha = 0.18f),
                            )
                        }
                    }
                    Box(
                        modifier =
                            Modifier.size(if (model is ModelState.Importing) 56.dp else 72.dp)
                                .clip(
                                    when (model) {
                                        is ModelState.Ready -> MaterialShapes.Cookie9Sided
                                        is ModelState.Importing -> MaterialShapes.Circle
                                        else -> MaterialShapes.Cookie4Sided
                                    }.toShape()
                                )
                                .background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (model is ModelState.Importing && model.progress != null) {
                            Text(
                                text = "${(model.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onAccent,
                            )
                        } else {
                            Icon(
                                imageVector =
                                    when (model) {
                                        is ModelState.Ready -> Icons.Outlined.AutoAwesome
                                        is ModelState.Importing -> Icons.Outlined.Downloading
                                        is ModelState.ImportFailed -> Icons.Outlined.ErrorOutline
                                        ModelState.Missing -> Icons.Outlined.CloudOff
                                    },
                                contentDescription = null,
                                tint = onAccent,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_model_name),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = GoogleSansWideFontFamily,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text =
                            when (model) {
                                is ModelState.Ready ->
                                    stringResource(
                                        R.string.ai_model_ready,
                                        Formatter.formatShortFileSize(context, model.sizeBytes),
                                    )
                                is ModelState.Importing -> stringResource(R.string.ai_model_tile_importing_label)
                                is ModelState.ImportFailed -> model.message
                                ModelState.Missing -> stringResource(R.string.ai_model_tile_missing_label)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            when (model) {
                ModelState.Missing,
                is ModelState.ImportFailed -> {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_model_download))
                        }
                        FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ai_model_import))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.ai_settings_download_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is ModelState.Ready -> {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onImport) {
                            Text(stringResource(R.string.ai_model_replace))
                        }
                        TextButton(onClick = onRemove) {
                            Text(stringResource(R.string.ai_settings_remove), color = content)
                        }
                    }
                }
                is ModelState.Importing -> Unit
            }
        }
    }
}

/** One of the AI's instructions: its name, whether it's yours or the default, and a preview. */
@Composable
private fun PromptCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    edited: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color =
                            if (edited) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor =
                            if (edited) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = stringResource(if (edited) R.string.ai_prompt_edited else R.string.ai_prompt_default),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
