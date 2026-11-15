package me.ash.reader.brief.media

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What an article carries besides its text: a podcast episode or a video (the feed item's
 * attachment), the episode's own picture and length, and a link to its comments.
 *
 * Kept in a small database of Brief You's own, by article id, so Read You's tables stay as they
 * are. Filled when feeds are read (RSS and Atom, and sync services that send attachments).
 * Articles read before this existed still get their audio or video: Read You took a feed item's
 * first attachment as its picture whatever it was, so a podcast's MP3 sits in the article's
 * picture field ([fromPicture]). Brief You.
 */
@Entity(tableName = "article_extras")
data class ArticleExtras(
    @PrimaryKey val articleId: String,
    /** The audio or video file's address; null when the article has none. */
    val mediaUrl: String? = null,
    /** [AUDIO] or [VIDEO]. */
    val mediaKind: String? = null,
    /** The file's type, as the feed gives it ("audio/mpeg"). */
    val mediaType: String? = null,
    /** Length in milliseconds, when the feed gives it. */
    val durationMs: Long? = null,
    /** The episode's own picture (a podcast's per-episode artwork). */
    val image: String? = null,
    /** Where the article's comments are. */
    val comments: String? = null,
) {
    val isAudio get() = mediaUrl != null && mediaKind == AUDIO
    val isVideo get() = mediaUrl != null && mediaKind == VIDEO

    companion object {
        const val AUDIO = "audio"
        const val VIDEO = "video"
    }
}

/** Where an episode or video was left, so it carries on from there. */
@Entity(tableName = "playback_position")
data class PlaybackPosition(
    @PrimaryKey val articleId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface ArticleMediaDao {
    @Query("SELECT * FROM article_extras WHERE articleId = :articleId")
    suspend fun extras(articleId: String): ArticleExtras?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putExtras(items: List<ArticleExtras>)

    @Query("SELECT * FROM playback_position WHERE articleId = :articleId")
    suspend fun position(articleId: String): PlaybackPosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPosition(position: PlaybackPosition)

    @Query("DELETE FROM playback_position WHERE articleId = :articleId")
    suspend fun clearPosition(articleId: String)

    @Query("SELECT articleId FROM article_extras")
    suspend fun allExtrasIds(): List<String>

    @Query("SELECT articleId FROM playback_position")
    suspend fun allPositionIds(): List<String>

    @Query("DELETE FROM article_extras WHERE articleId IN (:ids)")
    suspend fun deleteExtras(ids: List<String>)

    @Query("DELETE FROM playback_position WHERE articleId IN (:ids)")
    suspend fun deletePositions(ids: List<String>)
}

// The schema is exported (app/schemas), so a later change can be a migration.
@Database(entities = [ArticleExtras::class, PlaybackPosition::class], version = 1, exportSchema = true)
abstract class ArticleMediaDatabase : RoomDatabase() {
    abstract fun dao(): ArticleMediaDao
}

/** The store, started with the app ([start]). */
object ArticleMedia {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var db: ArticleMediaDatabase? = null

    @Volatile private var articles: me.ash.reader.brief.data.BriefArticleDao? = null

    fun start(context: Context, articles: me.ash.reader.brief.data.BriefArticleDao) {
        this.articles = articles
        if (db != null) return
        db = Room.databaseBuilder(context.applicationContext, ArticleMediaDatabase::class.java, "brief_media")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val dao: ArticleMediaDao? get() = db?.dao()

    /** Forgets the attachments and places of articles that are gone (after a sync's clean-up). */
    suspend fun forget(articleIds: List<String>) {
        val dao = dao ?: return
        articleIds.chunked(500).forEach {
            dao.deleteExtras(it)
            dao.deletePositions(it)
        }
    }

    /** Saves what feed reading found (in the background; feed reading doesn't wait for it). */
    fun found(items: List<ArticleExtras>) {
        val useful = items.filter { it.mediaUrl != null || it.comments != null || it.image != null }
        if (useful.isEmpty()) return
        // Kept in memory until the items are articles: feed reading sees every item, and the
        // ones Read You (or mute keywords, duplicates) skips must leave nothing behind.
        synchronized(pending) { useful.forEach { pending[it.articleId] = it to 0 } }
        flushLater()
    }

    private val pending = LinkedHashMap<String, Pair<ArticleExtras, Int>>()
    private var flushJob: kotlinx.coroutines.Job? = null

    /** Saves what became articles a few seconds after feed reading goes quiet (tries three times). */
    private fun flushLater() {
        synchronized(pending) {
            flushJob?.cancel()
            flushJob =
                scope.launch {
                    kotlinx.coroutines.delay(FLUSH_DELAY_MS)
                    flush()
                }
        }
    }

    private suspend fun flush() {
        val dao = dao ?: return
        val articles = articles ?: return
        val batch = synchronized(pending) { pending.values.toList() }
        if (batch.isEmpty()) return
        val existing = runCatching { batch.map { it.first.articleId }.chunked(500).flatMap { articles.existingIds(it) }.toSet() }.getOrNull() ?: return
        val ready = batch.filter { it.first.articleId in existing }.map { it.first }
        runCatching { if (ready.isNotEmpty()) dao.putExtras(ready) }
        val again =
            synchronized(pending) {
                batch.forEach { (extras, tries) ->
                    val id = extras.articleId
                    // Replaced meanwhile by a newer find: left for its own round.
                    if (pending[id]?.first !== extras) return@forEach
                    if (id in existing || tries + 1 >= FLUSH_TRIES) pending.remove(id) else pending[id] = extras to tries + 1
                }
                pending.isNotEmpty()
            }
        if (again) flushLater()
    }

    /** Forgets attachments and places whose article is gone (see BriefCleanup). */
    suspend fun sweep() {
        val dao = dao ?: return
        val articles = articles ?: return
        val ids = (dao.allExtrasIds() + dao.allPositionIds()).distinct()
        val existing = ids.chunked(500).flatMap { articles.existingIds(it) }.toSet()
        forget(ids.filterNot { it in existing })
    }

    private const val FLUSH_DELAY_MS = 5_000L
    private const val FLUSH_TRIES = 3

    /**
     * An article's extras: saved ones, or, for articles read before this existed, the audio or
     * video kept in its picture field.
     */
    suspend fun of(articleId: String, picture: String?): ArticleExtras? =
        runCatching { dao?.extras(articleId) }.getOrNull() ?: fromPicture(articleId, picture)

    fun fromPicture(articleId: String, picture: String?): ArticleExtras? {
        if (WebUrl.of(picture) == null) return null
        val kind = MediaFiles.kindOf(picture, null) ?: return null
        return ArticleExtras(articleId = articleId, mediaUrl = picture, mediaKind = kind)
    }
}

/**
 * Web addresses only (http or https), for anything a feed gives the player, the notification or a
 * button: a feed can't point them at files on the phone (file:, content:) or elsewhere.
 */
object WebUrl {
    fun of(url: String?): String? =
        url?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}

/** Telling audio and video files apart from pictures, by their type or their address. */
object MediaFiles {
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "m4b")
    private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mov", "mkv", "3gp")

    /** [ArticleExtras.AUDIO], [ArticleExtras.VIDEO], or null for anything else (pictures). */
    fun kindOf(url: String?, type: String?): String? {
        val t = type?.lowercase()?.trim().orEmpty()
        when {
            t.startsWith("audio/") -> return ArticleExtras.AUDIO
            t.startsWith("video/") -> return ArticleExtras.VIDEO
            t.startsWith("image/") -> return null
        }
        val ext = url?.substringBefore('#')?.substringBefore('?')?.substringAfterLast('/')
            ?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            in AUDIO_EXT -> ArticleExtras.AUDIO
            in VIDEO_EXT -> ArticleExtras.VIDEO
            else -> null
        }
    }

    /** Whether [url] is an audio or video file rather than a picture. */
    fun isMedia(url: String?): Boolean = kindOf(url, null) != null

    /** A web page or a Flash player, not a file a player can play. */
    fun isPage(url: String?, type: String?): Boolean {
        val t = type?.lowercase()?.trim().orEmpty()
        if (t.startsWith("text/") || t.contains("html") || t.contains("shockwave") || t.contains("flash")) return true
        val ext = url?.substringBefore('#')?.substringBefore('?')?.substringAfterLast('/')
            ?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext in setOf("html", "htm", "php", "aspx", "swf")) return true
        val host = url?.substringAfter("://")?.substringBefore('/')?.lowercase().orEmpty()
        return listOf("youtube.com", "youtu.be", "vimeo.com", "dailymotion.com").any { host == it || host.endsWith(".$it") }
    }
}
