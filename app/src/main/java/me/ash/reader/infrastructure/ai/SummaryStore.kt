package me.ash.reader.infrastructure.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.domain.repository.ArticleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kinds of AI output that are saved per article. */
enum class SavedKind(val ext: String) {
    Summary("summary"),
}

/**
 * Saves AI output per article as small files, so reopening an article's summary is instant. A saved summary lives exactly as long as its article: once
 * the article is deleted (for example by "Keep archived articles"), [prune] removes it too.
 */
@Singleton
class SummaryStore
@Inject
constructor(
    @ApplicationContext context: Context,
    private val articleDao: ArticleDao,
) {
    private val dir = File(context.filesDir, "ai_cache").apply { mkdirs() }

    @Volatile private var pruned = false

    // Which articles have a saved summary, so the article list can show an AI icon.
    private val known = ConcurrentHashMap<String, Boolean>()
    private val _version = MutableStateFlow(0)
    /** Bumps whenever a summary is saved or deleted, so lists can refresh their icons. */
    val version: StateFlow<Int> = _version

    /** Whether a summary is saved for this article (cached after the first check). */
    suspend fun hasSummary(articleId: String): Boolean =
        known[articleId]
            ?: withContext(Dispatchers.IO) { file(articleId, SavedKind.Summary).isFile }
                .also { known[articleId] = it }

    fun peekHasSummary(articleId: String): Boolean = known[articleId] == true

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
                known[articleId] = true
                _version.value++
            }
        }

    private fun file(articleId: String, kind: SavedKind) =
        File(dir, "${hash(articleId)}.${kind.ext}")

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    /** Deletes saved output for these articles (called when they are removed). */
    suspend fun delete(articleIds: Collection<String>) =
        withContext(Dispatchers.IO) {
            if (articleIds.isEmpty()) return@withContext
            for (id in articleIds) {
                SavedKind.entries.forEach { file(id, it).delete() }
                known.remove(id)
            }
            _version.value++
        }

    /** Deletes saved output whose article no longer exists. Runs once per app start. */
    suspend fun prune() =
        withContext(Dispatchers.IO) {
            if (pruned) return@withContext
            pruned = true
            val files = dir.listFiles().orEmpty()
            if (files.isEmpty()) return@withContext
            val live = articleDao.queryAllIds().mapTo(HashSet()) { hash(it) }
            files.forEach { if (it.nameWithoutExtension !in live) it.delete() }
        }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SummaryStoreEntryPoint {
    fun summaryStore(): SummaryStore
}

/** True when this article has a saved AI summary. Updates when summaries are saved or deleted. */
@Composable
fun rememberHasSummary(articleId: String): Boolean {
    val context = LocalContext.current
    val store = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SummaryStoreEntryPoint::class.java)
            .summaryStore()
    }
    val version by store.version.collectAsState()
    val has by
        produceState(store.peekHasSummary(articleId), articleId, version) {
            value = store.hasSummary(articleId)
        }
    return has
}
