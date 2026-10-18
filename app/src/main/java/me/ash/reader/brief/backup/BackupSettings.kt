package me.ash.reader.brief.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.ext.DateFormat
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.ext.showToastLong
import me.ash.reader.ui.ext.toString
import me.ash.reader.ui.page.settings.SettingItem

/** Creates and restores Brief You's full backup file (used by [BackupSettingItems]). */
@HiltViewModel
class BackupViewModel
@Inject
constructor(
    private val backupService: BackupService,
    @param:IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    /** Builds the full backup file; [callback] gets its bytes on the main thread. */
    fun createBackup(accountId: Int, callback: (ByteArray) -> Unit) {
        viewModelScope.launch(defaultDispatcher) {
            val bytes = runCatching { backupService.create(accountId) }.getOrNull()
            withContext(mainDispatcher) { callback(bytes ?: ByteArray(0)) }
        }
    }

    /** Restores a backup file; [callback] gets the result, or the error message, on the main thread. */
    fun restoreBackup(bytes: ByteArray, callback: (Result<BackupService.RestoreResult>) -> Unit) {
        // Not tied to the screen, so leaving the page can't stop a restore half way.
        applicationScope.launch(ioDispatcher) {
            val result = runCatching { backupService.restore(bytes) }
            withContext(mainDispatcher) { callback(result) }
        }
    }
}

/**
 * "Back up everything" and "Restore from backup" on an account's page, with the restore
 * confirmation. Read You's AccountDetailsPage shows these with a single line.
 */
@Composable
fun BackupSettingItems(accountId: Int, viewModel: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<ByteArray?>(null) }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MimeType.ANY)) {
            result ->
            result?.let { uri ->
                viewModel.createBackup(accountId) { bytes ->
                    scope.launch {
                        val ok =
                            bytes.isNotEmpty() &&
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                            context.contentResolver.openOutputStream(uri)?.use {
                                                it.write(bytes)
                                            } != null
                                        }
                                        .getOrDefault(false)
                                }
                        context.showToast(
                            context.getString(
                                if (ok) R.string.backup_saved else R.string.backup_failed
                            )
                        )
                    }
                }
            }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                scope.launch {
                    pendingRestore =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                    context.contentResolver.openInputStream(it)?.use { s ->
                                        BackupService.readBackupFile(s)
                                    }
                                }
                                .getOrNull()
                        }
                }
            }
        }

    SettingItem(
        title = stringResource(R.string.backup_everything),
        desc = stringResource(R.string.backup_everything_desc),
        onClick = {
            backupLauncher.launch(
                "BriefYou-backup-" +
                    "${Date().toString(DateFormat.YYYY_MM_DD_DASH_HH_MM_SS_DASH)}.zip"
            )
        },
    ) {}
    SettingItem(
        title = stringResource(R.string.restore_from_backup),
        desc = stringResource(R.string.restore_from_backup_desc),
        onClick = { restoreLauncher.launch(arrayOf(MimeType.ANY)) },
    ) {}

    RYDialog(
        visible = pendingRestore != null,
        title = {
            Text(
                text = stringResource(R.string.restore_from_backup),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = { Text(text = stringResource(R.string.restore_confirm_text)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val bytes = pendingRestore
                    pendingRestore = null
                    if (bytes != null) {
                        viewModel.restoreBackup(bytes) { result ->
                            result
                                .onSuccess { r -> context.showToastLong(restoreMessage(context, r)) }
                                .onFailure {
                                    context.showToastLong(
                                        context.getString(R.string.restore_failed, it.message ?: "")
                                    )
                                }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.restore))
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
        },
        onDismissRequest = { pendingRestore = null },
    )
}

/** "Restored: subscriptions, filters…", plus what couldn't be restored, if anything. */
private fun restoreMessage(
    context: android.content.Context,
    r: BackupService.RestoreResult,
): String {
    fun names(parts: List<BackupService.Part>) =
        parts.joinToString(", ") {
            context.getString(
                when (it) {
                    BackupService.Part.Subscriptions -> R.string.backup_part_subscriptions
                    BackupService.Part.Filters -> R.string.backup_part_filters
                    BackupService.Part.Starred -> R.string.backup_part_starred
                    BackupService.Part.Account -> R.string.backup_part_account
                    BackupService.Part.Settings -> R.string.backup_part_settings
                }
            )
        }
    return buildList {
            if (r.restored.isNotEmpty()) add(context.getString(R.string.restore_done, names(r.restored)))
            if (r.failed.isNotEmpty()) {
                add(context.getString(R.string.restore_partly_failed, names(r.failed)))
            }
        }
        .joinToString("\n")
}
