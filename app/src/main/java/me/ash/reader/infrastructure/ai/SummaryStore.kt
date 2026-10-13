package me.ash.reader.infrastructure.ai

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

    suspend fun get(articleId: String, kind: SavedKind): String? =
        withContext(Dispatchers.IO) {
            prune()
            file(articleId, kind).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        }

    suspend fun put(articleId: String, kind: SavedKind, text: String) =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext
            file(articleId, kind).writeText(text)
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
            }
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
