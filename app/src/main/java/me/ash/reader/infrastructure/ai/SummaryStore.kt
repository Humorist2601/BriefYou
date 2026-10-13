package me.ash.reader.infrastructure.ai

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Kinds of AI output that are saved per article. */
enum class SavedKind(val ext: String) {
    Summary("summary"),
    Translation("translation"),
}

/**
 * Saves AI output per article as small files, so reopening an article is instant and the article
 * list can show a one-line summary. Files older than [MAX_AGE_MS] are pruned.
 */
@Singleton
class SummaryStore @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "ai_cache").apply { mkdirs() }
    private val oneLiners = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap.newKeySet<String>()
    private val _version = MutableStateFlow(0)
    /** Bumps whenever something is saved, so lists can refresh their one-liners. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile private var pruned = false

    suspend fun get(articleId: String, kind: SavedKind): String? =
        withContext(Dispatchers.IO) {
            prune()
            file(articleId, kind).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        }

    suspend fun put(articleId: String, kind: SavedKind, text: String) =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext
            file(articleId, kind).writeText(text)
            if (kind == SavedKind.Summary) {
                misses.remove(articleId)
                firstLine(text)?.let { oneLiners[articleId] = it }
                _version.value++
            }
        }

    /** Cached one-liner without touching disk (null if unknown or none). */
    fun peekOneLiner(articleId: String): String? = oneLiners[articleId]

    suspend fun oneLiner(articleId: String): String? {
        oneLiners[articleId]?.let { return it }
        if (articleId in misses) return null
        val text = get(articleId, SavedKind.Summary)
        val line = text?.let(::firstLine)
        if (line != null) oneLiners[articleId] = line else misses.add(articleId)
        return line
    }

    private fun firstLine(text: String): String? =
        text.lineSequence()
            .map { it.trim().trimStart('•', '*', '-', ' ').trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("Why it matters", ignoreCase = true) }

    private fun file(articleId: String, kind: SavedKind) =
        File(dir, "${hash(articleId)}.${kind.ext}")

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun prune() {
        if (pruned) return
        pruned = true
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private companion object {
        const val MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000 // 60 days
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SummaryStoreEntryPoint {
    fun summaryStore(): SummaryStore
}

/** The saved one-line summary for an article, or null. Updates when a new summary is saved. */
@Composable
fun rememberSavedOneLiner(articleId: String): String? {
    val context = LocalContext.current
    val store = remember {
        EntryPointAccessors.fromApplication(
                context.applicationContext,
                SummaryStoreEntryPoint::class.java,
            )
            .summaryStore()
    }
    val version by store.version.collectAsState()
    val line by
        produceState(store.peekOneLiner(articleId), articleId, version) {
            value = store.oneLiner(articleId)
        }
    return line
}
