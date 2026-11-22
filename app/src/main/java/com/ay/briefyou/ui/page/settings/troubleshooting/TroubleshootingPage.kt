package com.ay.briefyou.ui.page.settings.troubleshooting

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.WorkInfo
import java.util.Date
import kotlinx.coroutines.launch
import com.ay.briefyou.R
import com.ay.briefyou.domain.data.Log
import com.ay.briefyou.domain.service.SyncWorker.Companion.ONETIME_WORK_TAG
import com.ay.briefyou.domain.service.SyncWorker.Companion.PERIODIC_WORK_TAG
import com.ay.briefyou.infrastructure.preference.OpenLinkPreference
import com.ay.briefyou.ui.component.base.Banner
import com.ay.briefyou.ui.component.base.DisplayText
import com.ay.briefyou.ui.component.base.FeedbackIconButton
import com.ay.briefyou.ui.component.base.RYScaffold
import com.ay.briefyou.ui.component.base.Subtitle
import com.ay.briefyou.ui.ext.collectAsStateValue
import com.ay.briefyou.ui.ext.openURL
import com.ay.briefyou.ui.theme.palette.onLight

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TroubleshootingPage(onBack: () -> Unit, viewModel: TroubleshootingViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val syncLogList = remember { mutableStateListOf<Log>() }

    LaunchedEffect(viewModel) { viewModel.getSyncLogs().let { syncLogList.addAll(it) } }

    val onetimeWorkerInfos =
        viewModel.workManager
            .getWorkInfosByTagFlow(ONETIME_WORK_TAG)
            .collectAsStateValue(emptyList())

    val periodicWorkerInfos =
        viewModel.workManager
            .getWorkInfosByTagFlow(PERIODIC_WORK_TAG)
            .collectAsStateValue(emptyList())

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
                    DisplayText(text = stringResource(R.string.troubleshooting), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                    Banner(
                        title = stringResource(R.string.bug_report),
                        icon = Icons.Outlined.Info,
                        action = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.go_to),
                            )
                        },
                    ) {
                        context.openURL(
                            context.getString(R.string.issue_tracer_url),
                            OpenLinkPreference.AutoPreferCustomTabs,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = "Worker infos")
                }
                items(onetimeWorkerInfos) {
                    WorkInfo(
                        tags = it.tags,
                        state = it.state,
                        nextScheduledMillis = it.nextScheduleTimeMillis,
                    )
                }
                items(periodicWorkerInfos) {
                    WorkInfo(
                        tags = it.tags,
                        state = it.state,
                        nextScheduledMillis = it.nextScheduleTimeMillis,
                    )
                }
                if (syncLogList.isNotEmpty()) {
                    item {
                        Subtitle(
                            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 24.dp),
                            text = "Sync errors",
                        )
                    }
                    items(syncLogList) { SyncLogItem(log = it) }
                    item {
                        Button(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                            onClick = {
                                viewModel.clearSyncLogs()
                                syncLogList.clear()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(
                        modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkInfo(
    tags: Set<String>,
    state: WorkInfo.State,
    nextScheduledMillis: Long,
    modifier: Modifier = Modifier,
) {
    val date = remember(nextScheduledMillis) { Date(nextScheduledMillis) }
    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(tags.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(state.toString(), style = MaterialTheme.typography.bodySmall)
        if (tags.contains(PERIODIC_WORK_TAG) && state != WorkInfo.State.FAILED) {
            Text("Next scheduled time: $date", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SyncLogItem(log: Log, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText(log.fileName, log.content))
                        )
                    }
                }
                .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(log.fileName, style = MaterialTheme.typography.titleMedium)
        Text(
            log.content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
