package me.ash.reader.domain.service

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
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.fromDataStoreToJSONString
import me.ash.reader.ui.ext.fromJSONStringToDataStore

@Serializable
data class BackupManifest(val format: Int, val app: String, val createdAt: Long)

@Serializable
data class FiltersBackup(val mutedKeywords: List<String>, val hideDuplicates: Boolean)

/**
 * One-file backup of everything that makes the app yours, as a ZIP:
 * - `subscriptions.opml`: groups and feeds with the additional info (notification, full content,
 *   browser flags and custom feed icons), the same file "Export as OPML" writes.
 * - `filters.json`: muted keywords and the duplicate-stories switch.
 * - `preferences.json`: appearance, reading and sync settings.
 * - `manifest.json`: format version, so future versions can still read old backups.
 *
 * Articles, read state and cached summaries are not included: they come back from the feeds (or the
 * account's server) with the next sync.
 */
@Singleton
class BackupService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val opmlService: OpmlService,
    private val filters: BriefFilters,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
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
            val filtersBackup =
                FiltersBackup(
                    mutedKeywords = filters.mutedKeywords.value,
                    hideDuplicates = filters.hideDuplicates.value,
                )
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.put(MANIFEST, json.encodeToString(manifest))
                zip.put(SUBSCRIPTIONS, opmlService.saveToString(accountId, attachInfo = true))
                zip.put(FILTERS, json.encodeToString(filtersBackup))
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

            // Settings go last: they can change the language or theme of the running app.
            val steps =
                listOf(Part.Filters to FILTERS, Part.Subscriptions to SUBSCRIPTIONS, Part.Settings to PREFERENCES)
                    .mapNotNull { (part, name) -> entries[name]?.let { part to it } }
            for ((p, data) in steps) {
                runCatching {
                        when (p) {
                            Part.Filters -> {
                                val f = json.decodeFromString<FiltersBackup>(String(data))
                                f.mutedKeywords.forEach { filters.addKeyword(it) }
                                filters.setHideDuplicates(f.hideDuplicates)
                            }
                            Part.Subscriptions ->
                                opmlService.saveToDatabase(ByteArrayInputStream(data))
                            Part.Settings -> String(data).fromJSONStringToDataStore(context)
                        }
                    }
                    .onSuccess { restored += p }
                    .onFailure { failed += p }
            }
            RestoreResult(restored, failed)
        }

    /** App preferences, without things that only make sense on this phone or this install. */
    private suspend fun portablePreferences(): String {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val all: Map<String, Any?> = Gson().fromJson(context.fromDataStoreToJSONString(), type)
        return Gson().toJson(all.filterNot { (key, value) -> isDeviceSpecific(key, value) })
    }

    private fun isDeviceSpecific(key: String, value: Any?): Boolean =
        key.startsWith("newVersion") ||
            key == DataStoreKey.skipVersionNumber ||
            // Names an installed browser app, which may not exist on the new phone.
            key == DataStoreKey.openLinkAppSpecificBrowser ||
            // A font file that was added on this phone and won't exist on another one.
            ((key == DataStoreKey.readingFonts || key == DataStoreKey.basicFonts) &&
                (value as? Number)?.toInt() == EXTERNAL_FONT)

    private fun ZipOutputStream.put(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    /** Reads the few files we know about into memory; anything else in the ZIP is ignored. */
    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val known = setOf(MANIFEST, SUBSCRIPTIONS, FILTERS, PREFERENCES)
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

        /** Value of the "external font" choice in the reading and basic font settings. */
        private const val EXTERNAL_FONT = 5
        private const val MAX_ENTRY_BYTES = 30 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 60L * 1024 * 1024
    }
}
