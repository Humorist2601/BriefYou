package me.ash.reader.brief.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.brief.ui.NumberSheet
import me.ash.reader.domain.service.AccountService
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.settings.SettingItem

/**
 * Automatic backups: the same full backup file as "Back up everything", saved into a folder the
 * reader picks, daily or weekly (off by default), while the phone is charging. The newest [keep]
 * files are kept; older ones are deleted. The folder is written with the document picker's
 * permission, so no storage permission is needed and the files outlive the app.
 */
object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val WORK = "auto_backup"
    private const val NAME_PREFIX = "BriefYou-backup-"

    enum class Frequency { Off, Daily, Weekly }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun frequency(context: Context): Frequency =
        prefs(context).getInt("frequency", 0).let { Frequency.values().getOrElse(it) { Frequency.Off } }

    fun folder(context: Context): Uri? = prefs(context).getString("folder", null)?.let(Uri::parse)

    fun keep(context: Context): Int = prefs(context).getInt("keep", 5)

    fun setKeep(context: Context, value: Int) = prefs(context).edit { putInt("keep", value) }

    fun setFrequency(context: Context, value: Frequency) {
        prefs(context).edit { putInt("frequency", value.ordinal) }
        schedule(context)
    }

    fun setFolder(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs(context).edit { putString("folder", uri.toString()) }
        schedule(context)
    }

    /** The last backup made here: when and how big, or null. */
    fun last(context: Context): Pair<Long, Long>? =
        prefs(context).let { p ->
            if (p.contains("last_time")) p.getLong("last_time", 0) to p.getLong("last_size", 0) else null
        }

    /** The folder's name for display ("Documents/BriefYou"). */
    fun folderLabel(uri: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':') }.getOrNull()
            ?.ifBlank { null } ?: uri.lastPathSegment.orEmpty()

    /** Runs on its own while charging, once a day or a week; nothing when off or no folder. */
    fun schedule(context: Context) {
        val frequency = frequency(context)
        val manager = WorkManager.getInstance(context)
        if (frequency == Frequency.Off || folder(context) == null) {
            manager.cancelUniqueWork(WORK)
            return
        }
        val days = if (frequency == Frequency.Daily) 1L else 7L
        val request =
            PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build()
                )
                .build()
        manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Makes one backup into the folder, then deletes the oldest beyond [keep]. False when it failed. */
    suspend fun run(context: Context, service: BackupService, accountId: Int): Boolean =
        withContext(Dispatchers.IO) {
            val tree = folder(context) ?: return@withContext false
            runCatching {
                    val bytes = service.create(accountId)
                    check(bytes.isNotEmpty())
                    val resolver = context.contentResolver
                    val parent =
                        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    val name =
                        NAME_PREFIX + SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date()) + ".zip"
                    val doc = DocumentsContract.createDocument(resolver, parent, "application/zip", name)
                    checkNotNull(doc)
                    checkNotNull(resolver.openOutputStream(doc)).use { it.write(bytes) }
                    prefs(context).edit {
                        putLong("last_time", System.currentTimeMillis())
                        putLong("last_size", bytes.size.toLong())
                    }
                    prune(context, tree)
                    true
                }
                .getOrDefault(false)
        }

    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>() // document id, name
        resolver
            .query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )
            ?.use { c -> while (c.moveToNext()) found += c.getString(0) to c.getString(1) }
        // The date is in the name, so the names sort by age.
        found
            .filter { it.second.startsWith(NAME_PREFIX) && it.second.endsWith(".zip") }
            .sortedByDescending { it.second }
            .drop(keep(context).coerceAtLeast(1))
            .forEach {
                runCatching {
                    DocumentsContract.deleteDocument(
                        resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, it.first),
                    )
                }
            }
    }
}

@HiltWorker
class AutoBackupWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: BackupService,
    private val accountService: AccountService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = AutoBackup.run(applicationContext, service, accountService.getCurrentAccountId())
        return if (ok || runAttemptCount >= 2) Result.success() else Result.retry()
    }
}

private fun AutoBackup.Frequency.label(): Int =
    when (this) {
        AutoBackup.Frequency.Off -> R.string.auto_backup_off
        AutoBackup.Frequency.Daily -> R.string.auto_backup_daily
        AutoBackup.Frequency.Weekly -> R.string.auto_backup_weekly
    }

/**
 * The automatic backup rows, under "Back up everything" and "Restore from backup": how often, the
 * folder, how many to keep, and "Back up now" with the last one's time and size.
 */
@Composable
fun AutoBackupSettingItems(accountId: Int, viewModel: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var frequency by remember { mutableStateOf(AutoBackup.frequency(context)) }
    var folder by remember { mutableStateOf(AutoBackup.folder(context)) }
    var keep by remember { mutableStateOf(AutoBackup.keep(context)) }
    var last by remember { mutableStateOf(AutoBackup.last(context)) }
    var running by remember { mutableStateOf(false) }
    var frequencyDialog by remember { mutableStateOf(false) }
    var keepSheet by remember { mutableStateOf(false) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                AutoBackup.setFolder(context, uri)
                folder = uri
                if (frequency == AutoBackup.Frequency.Off) {
                    frequency = AutoBackup.Frequency.Weekly
                    AutoBackup.setFrequency(context, frequency)
                }
            }
        }

    SettingItem(
        title = stringResource(R.string.auto_backup_title),
        desc = stringResource(frequency.label()),
        onClick = { frequencyDialog = true },
    ) {}
    if (frequency != AutoBackup.Frequency.Off) {
        SettingItem(
            title = stringResource(R.string.auto_backup_folder),
            desc = folder?.let { AutoBackup.folderLabel(it) } ?: stringResource(R.string.auto_backup_no_folder),
            onClick = { picker.launch(folder) },
        ) {}
        SettingItem(
            title = stringResource(R.string.auto_backup_keep),
            desc = pluralStringResource(R.plurals.auto_backup_keep_n, keep, keep),
            onClick = { keepSheet = true },
        ) {}
        SettingItem(
            title = stringResource(R.string.auto_backup_now),
            desc =
                last?.let { (time, size) ->
                    DateUtils.formatDateTime(
                        context,
                        time,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                    ) + " · " + Formatter.formatShortFileSize(context, size)
                } ?: stringResource(R.string.auto_backup_none),
            onClick = {
                if (folder == null) picker.launch(null)
                else if (!running) {
                    running = true
                    viewModel.backupNow(accountId) { ok ->
                        running = false
                        last = AutoBackup.last(context)
                        context.showToast(
                            context.getString(if (ok) R.string.backup_saved else R.string.auto_backup_failed)
                        )
                    }
                }
            },
        ) {}
    }

    RadioDialog(
        visible = frequencyDialog,
        title = stringResource(R.string.auto_backup_title),
        options =
            AutoBackup.Frequency.values().map {
                RadioDialogOption(text = stringResource(it.label()), selected = it == frequency) {
                    frequency = it
                    AutoBackup.setFrequency(context, it)
                    // On with no folder yet: pick one now.
                    if (it != AutoBackup.Frequency.Off && AutoBackup.folder(context) == null) {
                        picker.launch(null)
                    }
                }
            },
    ) {
        frequencyDialog = false
    }
    if (keepSheet) {
        NumberSheet(
            title = stringResource(R.string.auto_backup_keep),
            value = keep.toFloat(),
            range = 1f..30f,
            step = 1f,
            default = 5f,
            onSave = {
                keep = it.toInt()
                AutoBackup.setKeep(context, keep)
            },
            onDismiss = { keepSheet = false },
        )
    }
}
