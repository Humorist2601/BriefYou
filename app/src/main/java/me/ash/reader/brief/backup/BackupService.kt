package me.ash.reader.brief.backup

import me.ash.reader.brief.stats.ReadingStats
import me.ash.reader.domain.service.AccountService
import me.ash.reader.brief.filters.BriefFilters
import me.ash.reader.brief.feeds.FeedOrdering
import me.ash.reader.brief.filters.FilterLogEntry
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.brief.feeds.OrderBackup
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.domain.service.WidgetUpdateWorker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.core.content.edit
import androidx.work.WorkManager
import java.util.Date
import java.util.UUID
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.brief.ai.GemmaSummarizer
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.KeepArchivedPreference
import me.ash.reader.infrastructure.preference.SyncIntervalPreference
import me.ash.reader.infrastructure.preference.SyncOnStartPreference
import me.ash.reader.infrastructure.preference.SyncOnlyOnWiFiPreference
import me.ash.reader.infrastructure.preference.SyncOnlyWhenChargingPreference
import me.ash.reader.ui.ext.spacerDollar
import me.ash.reader.brief.readaloud.TextToSpeechManager // Brief You
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.fromDataStoreToJSONString
import me.ash.reader.ui.ext.fromJSONStringToDataStore

@Serializable
data class BackupManifest(val format: Int, val app: String, val createdAt: Long)

@Serializable
data class FiltersBackup(
    val mutedKeywords: List<String>,
    val hideDuplicates: Boolean,
    /** The recently filtered list (added later; older backups don't have it). */
    val log: List<FilterLogEntry> = emptyList(),
    /** Articles brought back with Undo, which the filters skip. */
    val undone: List<String> = emptyList(),
)

/** A starred article, in full, so it can be brought back even after it left its feed. */
@Serializable
data class StarredArticleBackup(
    val feedUrl: String,
    val title: String,
    val author: String? = null,
    val link: String,
    val date: Long,
    val rawDescription: String,
    val shortDescription: String,
    val img: String? = null,
    val isUnread: Boolean = false,
)

/** The account's sync settings (kept with the account, not with the app settings). */
@Serializable
data class AccountSettingsBackup(
    val syncInterval: Long,
    val syncOnStart: Boolean,
    val syncOnlyOnWiFi: Boolean,
    val syncOnlyWhenCharging: Boolean,
    val keepArchived: Long,
    val syncBlockList: List<String>,
)

/** Settings kept outside the app settings: AI instructions and Triage. */
@Serializable
data class ExtrasBackup(
    /** Null where the default instructions are used. */
    val articlePrompt: String? = null,
    val gistPrompt: String? = null,
    val triageAutoSeconds: Int? = null,
    /** Read aloud's last speed (added later; older backups don't have it). */
    val readAloudSpeed: Float? = null,
)

/**
 * One-file backup of everything that makes the app yours, as a ZIP:
 * - `subscriptions.opml`: groups and feeds with the additional info (notification, full content,
 *   browser flags and custom feed icons), the same file "Export as OPML" writes.
 * - `filters.json`: muted keywords, the duplicate-stories switch, the recently filtered list and
 *   the articles brought back with Undo.
 * - `starred.json`: starred articles in full (title, link, text, date, feed).
 * - `account.json`: the account's sync settings (interval, on start, Wi-Fi, charging, how long
 *   to keep read articles, block list).
 * - `stats.json`: reading stats (time read, articles opened, top feeds, inbox zero, Triage).
 * - `extras.json`: your AI instructions, the Triage auto-advance delay and read aloud's speed.
 * - `order.json`: how groups and feeds are sorted (by name, by count or by hand), if you set one.
 *   The OPML above is written in that same order, too.
 * - `preferences.json`: appearance, reading and sync settings.
 * - `manifest.json`: format version, so future versions can still read old backups.
 *
 * Other articles and their read state are not included: they come back from the feeds (or the
 * account's server) with the next sync. Server logins are left out on purpose, so a backup file
 * never holds a password. The AI model file is too big and is imported again on its own.
 */
@Singleton
class BackupService
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val opmlService: OpmlService,
    private val filters: BriefFilters,
    private val feedOrdering: FeedOrdering,
    private val articleDao: ArticleDao,
    private val briefArticleDao: me.ash.reader.brief.data.BriefArticleDao,
    private val feedDao: FeedDao,
    private val accountService: AccountService,
    private val summarizer: GemmaSummarizer,
    private val workManager: WorkManager,
    private val tts: dagger.Lazy<TextToSpeechManager>,
    @param:IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Which parts were restored, and which could not be. */
    data class RestoreResult(val restored: List<Part>, val failed: List<Part>)

    enum class Part {
        Subscriptions,
        Filters,
        Starred,
        Account,
        Settings,
    }

    suspend fun create(accountId: Int): ByteArray =
        withContext(ioDispatcher) {
            val manifest =
                BackupManifest(
                    format = FORMAT,
                    app = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        ?: "",
                    createdAt = System.currentTimeMillis(),
                )
            val (log, undone) = filters.exportState()
            val filtersBackup =
                FiltersBackup(
                    mutedKeywords = filters.mutedKeywords.value,
                    hideDuplicates = filters.hideDuplicates.value,
                    log = log,
                    undone = undone,
                )
            val starred =
                briefArticleDao
                    .queryForGist(accountId, unreadOnly = false, starredOnly = true, limit = MAX_STARRED)
                    .map {
                        StarredArticleBackup(
                            feedUrl = it.feed.url,
                            title = it.article.title,
                            author = it.article.author,
                            link = it.article.link,
                            date = it.article.date.time,
                            rawDescription = it.article.rawDescription,
                            shortDescription = it.article.shortDescription,
                            img = it.article.img,
                            isUnread = it.article.isUnread,
                        )
                    }
            val account = accountService.getAccountById(accountId)
            val extras =
                ExtrasBackup(
                    articlePrompt = summarizer.customArticlePrompt(),
                    gistPrompt = summarizer.customGistPrompt(),
                    triageAutoSeconds =
                        triagePrefs().takeIf { it.contains(TRIAGE_SECONDS) }?.getInt(TRIAGE_SECONDS, 5),
                    readAloudSpeed =
                        readAloudPrefs().takeIf { it.contains(READ_ALOUD_SPEED) }
                            ?.getFloat(READ_ALOUD_SPEED, 1f),
                )
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.put(MANIFEST, json.encodeToString(manifest))
                zip.put(SUBSCRIPTIONS, opmlService.saveToString(accountId, attachInfo = true))
                zip.put(FILTERS, json.encodeToString(filtersBackup))
                zip.put(STARRED, json.encodeToString(starred))
                account?.let {
                    zip.put(
                        ACCOUNT,
                        json.encodeToString(
                            AccountSettingsBackup(
                                syncInterval = it.syncInterval.value,
                                syncOnStart = it.syncOnStart.value,
                                syncOnlyOnWiFi = it.syncOnlyOnWiFi.value,
                                syncOnlyWhenCharging = it.syncOnlyWhenCharging.value,
                                keepArchived = it.keepArchived.value,
                                syncBlockList = it.syncBlockList,
                            )
                        ),
                    )
                }
                zip.put(EXTRAS, json.encodeToString(extras))
                zip.put(STATS, ReadingStats.export(context))
                // The order of groups and feeds (only when it isn't the default one).
                feedOrdering.export(accountId)?.let { zip.put(ORDER, json.encodeToString(it)) }
                zip.put(PREFERENCES, portablePreferences())
            }
            out.toByteArray()
        }

    /**
     * Restores a backup into the current account. Subscriptions are added (feeds you already have
     * are kept as they are), muted keywords are merged, and settings are replaced by the backup's.
     * Each part is restored on its own, so one bad part doesn't stop the others.
     */
    suspend fun restore(bytes: ByteArray): RestoreResult =
        withContext(ioDispatcher) {
            val entries = readZip(bytes)
            val manifest =
                entries[MANIFEST]?.let {
                    runCatching { json.decodeFromString<BackupManifest>(String(it)) }.getOrNull()
                } ?: error("This is not a Brief You backup file")
            if (manifest.format > FORMAT) {
                error("This backup was made by a newer version of the app")
            }

            val restored = mutableListOf<Part>()
            val failed = mutableListOf<Part>()

            // Starred articles need their feeds, so they come after the subscriptions. Settings go
            // last: they can change the language or theme of the running app.
            val steps =
                listOf(
                        Part.Filters to FILTERS,
                        Part.Subscriptions to SUBSCRIPTIONS,
                        Part.Starred to STARRED,
                        Part.Account to ACCOUNT,
                        Part.Settings to PREFERENCES,
                    )
                    .mapNotNull { (part, name) -> entries[name]?.let { part to it } }
            for ((p, data) in steps) {
                runCatching {
                        when (p) {
                            Part.Filters -> {
                                val f = json.decodeFromString<FiltersBackup>(String(data))
                                f.mutedKeywords.forEach { filters.addKeyword(it) }
                                filters.setHideDuplicates(f.hideDuplicates)
                                filters.importState(f.log, f.undone)
                            }
                            Part.Subscriptions -> {
                                opmlService.saveToDatabase(ByteArrayInputStream(data))
                                // The order is applied once the groups and feeds exist again.
                                entries[ORDER]?.let {
                                    feedOrdering.import(json.decodeFromString<OrderBackup>(String(it)))
                                }
                            }
                            Part.Starred ->
                                restoreStarred(
                                    json.decodeFromString<List<StarredArticleBackup>>(String(data))
                                )
                            Part.Account ->
                                restoreAccountSettings(
                                    json.decodeFromString<AccountSettingsBackup>(String(data))
                                )
                            Part.Settings -> {
                                String(data).fromJSONStringToDataStore(context)
                                entries[EXTRAS]?.let {
                                    restoreExtras(json.decodeFromString<ExtrasBackup>(String(it)))
                                }
                                // Reading stats (added later; older backups don't have them).
                                entries[STATS]?.let { ReadingStats.restore(context, String(it)) }
                            }
                        }
                    }
                    .onSuccess { restored += p }
                    .onFailure { failed += p }
            }
            RestoreResult(restored, failed)
        }

    /**
     * Stars the backup's articles in the current account: an article the feed still has is
     * starred, and one it no longer has is added back. Articles whose feed isn't subscribed are
     * skipped.
     */
    private suspend fun restoreStarred(items: List<StarredArticleBackup>) {
        if (items.isEmpty()) return
        val accountId = accountService.getCurrentAccountId()
        val feeds = HashMap<String, Feed?>()
        for (item in items) {
            val feed =
                feeds.getOrPut(item.feedUrl) {
                    feedDao.queryByLink(accountId, item.feedUrl).firstOrNull()
                } ?: continue
            val existing =
                articleDao.queryArticlesByLinks(listOf(item.link), feed.id, accountId).firstOrNull()
            if (existing != null) {
                if (!existing.isStarred) articleDao.update(existing.copy(isStarred = true))
            } else {
                articleDao.insert(
                    Article(
                        id = accountId.spacerDollar(UUID.randomUUID().toString()),
                        date = Date(item.date),
                        title = item.title,
                        author = item.author,
                        rawDescription = item.rawDescription,
                        shortDescription = item.shortDescription,
                        img = item.img,
                        link = item.link,
                        feedId = feed.id,
                        accountId = accountId,
                        isUnread = item.isUnread,
                        isStarred = true,
                        updateAt = Date(),
                    )
                )
            }
        }
    }

    /** Puts the sync settings on the current account and schedules syncing to match. */
    private suspend fun restoreAccountSettings(b: AccountSettingsBackup) {
        val accountId = accountService.getCurrentAccountId()
        accountService.update(accountId) {
            copy(
                syncInterval =
                    SyncIntervalPreference.values.firstOrNull { it.value == b.syncInterval }
                        ?: syncInterval,
                syncOnStart =
                    SyncOnStartPreference.values.firstOrNull { it.value == b.syncOnStart }
                        ?: syncOnStart,
                syncOnlyOnWiFi =
                    SyncOnlyOnWiFiPreference.values.firstOrNull { it.value == b.syncOnlyOnWiFi }
                        ?: syncOnlyOnWiFi,
                syncOnlyWhenCharging =
                    SyncOnlyWhenChargingPreference.values.firstOrNull {
                        it.value == b.syncOnlyWhenCharging
                    } ?: syncOnlyWhenCharging,
                keepArchived =
                    KeepArchivedPreference.values.firstOrNull { it.value == b.keepArchived }
                        ?: keepArchived,
                syncBlockList = b.syncBlockList,
            )
        }
        val account = accountService.getAccountById(accountId) ?: return
        if (account.syncInterval.value != SyncIntervalPreference.Manually.value) {
            SyncWorker.enqueuePeriodicWork(account = account, workManager = workManager)
            WidgetUpdateWorker.enqueuePeriodicWork(
                workManager = workManager,
                syncInterval = account.syncInterval,
                syncOnlyWhenCharging = account.syncOnlyWhenCharging,
                syncOnlyOnWiFi = account.syncOnlyOnWiFi,
            )
        } else {
            SyncWorker.cancelPeriodicWork(workManager)
            WidgetUpdateWorker.cancelPeriodicWork(workManager)
        }
    }

    private fun restoreExtras(b: ExtrasBackup) {
        summarizer.setArticlePrompt(b.articlePrompt)
        summarizer.setGistPrompt(b.gistPrompt)
        b.triageAutoSeconds?.let { triagePrefs().edit { putInt(TRIAGE_SECONDS, it) } }
        b.readAloudSpeed?.let { speed ->
            readAloudPrefs().edit { putFloat(READ_ALOUD_SPEED, speed) }
            // So it applies straight away, not only after the app restarts.
            tts.get().setSpeed(speed)
        }
    }

    /** Read aloud's own settings (see TextToSpeechManager). */
    private fun readAloudPrefs() = context.getSharedPreferences("read_aloud", Context.MODE_PRIVATE)

    /** The Triage screen's own settings (see TriageScreen). */
    private fun triagePrefs() = context.getSharedPreferences("triage", Context.MODE_PRIVATE)

    /** App preferences, without things that only make sense on this phone or this install. */
    private suspend fun portablePreferences(): String {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val all: Map<String, Any?> = Gson().fromJson(context.fromDataStoreToJSONString(), type)
        return Gson().toJson(all.filterNot { (key, value) -> isDeviceSpecific(key, value) })
    }

    private fun isDeviceSpecific(key: String, value: Any?): Boolean =
        key.startsWith("newVersion") ||
            key == PreferencesKey.skipVersionNumber ||
            // Which account is open: account numbers differ between phones.
            key == PreferencesKey.currentAccountId ||
            // Names an installed browser app, which may not exist on the new phone.
            key == PreferencesKey.openLinkAppSpecificBrowser ||
            // A font file that was added on this phone and won't exist on another one.
            ((key == PreferencesKey.readingFonts || key == PreferencesKey.basicFonts) &&
                (value as? Number)?.toInt() == EXTERNAL_FONT)

    private fun ZipOutputStream.put(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    /** Reads the few files we know about into memory; anything else in the ZIP is ignored. */
    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val known =
            setOf(MANIFEST, SUBSCRIPTIONS, FILTERS, PREFERENCES, ORDER, STARRED, ACCOUNT, EXTRAS, STATS)
        val result = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name !in known) continue
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = zip.read(chunk)
                    if (n <= 0) break
                    buffer.write(chunk, 0, n)
                    total += n
                    // Guards against a tiny file that expands into gigabytes.
                    if (buffer.size() > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        error("The backup file is too large")
                    }
                }
                result[entry.name] = buffer.toByteArray()
            }
        }
        if (result.isEmpty()) error("This is not a Brief You backup file")
        return result
    }

    companion object {
        const val FORMAT = 1
        const val MANIFEST = "manifest.json"
        const val SUBSCRIPTIONS = "subscriptions.opml"
        const val FILTERS = "filters.json"
        const val PREFERENCES = "preferences.json"
        const val ORDER = "order.json"
        const val STARRED = "starred.json"
        const val ACCOUNT = "account.json"
        const val EXTRAS = "extras.json"
        const val STATS = "stats.json"
        private const val TRIAGE_SECONDS = "auto_seconds"
        private const val READ_ALOUD_SPEED = "speed"
        private const val MAX_STARRED = 100_000

        /** Value of the "external font" choice in the reading and basic font settings. */
        private const val EXTERNAL_FONT = 5
        // 64 MB: far more than a normal backup (a few MB), small enough to be safe in memory.
        private const val MAX_ENTRY_BYTES = 64 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        private const val MAX_FILE_BYTES = 64 * 1024 * 1024

        /**
         * Reads a picked backup file, refusing anything over 64 MB (someone could pick a video
         * by mistake, which wouldn't fit in memory).
         */
        fun readBackupFile(input: java.io.InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val n = input.read(chunk)
                if (n <= 0) break
                out.write(chunk, 0, n)
                if (out.size() > MAX_FILE_BYTES) error("The backup file is too large")
            }
            return out.toByteArray()
        }
    }
}
