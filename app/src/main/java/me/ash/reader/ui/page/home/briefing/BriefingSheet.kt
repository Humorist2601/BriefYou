package me.ash.reader.ui.page.home.briefing

import android.content.Context
import android.text.format.DateUtils
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.BriefingPlaybackService
import me.ash.reader.infrastructure.ai.GemmaSummarizer
import me.ash.reader.infrastructure.ai.MorningBriefing
import me.ash.reader.ui.ext.collectAsStateValue

@HiltViewModel
class BriefingViewModel
@Inject
constructor(val briefing: MorningBriefing, private val summarizer: GemmaSummarizer) : ViewModel() {
    val hasModel: Boolean
        get() = summarizer.hasModel()

    fun generateNow() {
        viewModelScope.launch { runCatching { briefing.generate(notify = false) } }
    }

    fun listen(context: Context) {
        ContextCompat.startForegroundService(context, BriefingPlaybackService.playIntent(context))
    }
}

/** Shows the latest morning briefing, with Listen and Generate now. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BriefingSheet(onDismissRequest: () -> Unit, viewModel: BriefingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val latest = viewModel.briefing.latest.collectAsStateValue()
    val generating = viewModel.briefing.generating.collectAsStateValue()
    val settings = viewModel.briefing.settings.collectAsStateValue()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    latest?.title ?: stringResource(R.string.briefing_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (latest != null) {
                Text(
                    DateUtils.getRelativeTimeSpanString(
                            latest.createdAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                        .toString() +
                        " · " +
                        pluralStringResource(
                            R.plurals.ai_gist_based_on,
                            latest.articleCount,
                            latest.articleCount,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            when {
                generating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.briefing_generating),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                latest != null -> Text(latest.text, style = MaterialTheme.typography.bodyLarge)
                else ->
                    Text(
                        stringResource(
                            if (viewModel.hasModel) R.string.briefing_empty
                            else R.string.briefing_needs_model
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
            }

            if (!settings.enabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.briefing_not_scheduled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = viewModel::generateNow,
                    enabled = viewModel.hasModel && !generating,
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.briefing_generate_now))
                }
                Button(
                    onClick = { viewModel.listen(context) },
                    enabled = latest != null && !generating,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.briefing_listen))
                }
            }
        }
    }
}
