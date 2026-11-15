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

    @Query("DELETE FROM article_extras WHERE articleId IN (:ids)")
    suspend fun deleteExtras(ids: List<String>)

    @Query("DELETE FROM playback_position WHERE articleId IN (:ids)")
    suspend fun deletePositions(ids: List<String>)
}

@Database(entities = [ArticleExtras::class, PlaybackPosition::class], version = 1, exportSchema = false)
abstract class ArticleMediaDatabase : RoomDatabase() {
    abstract fun dao(): ArticleMediaDao
}

/** The store, started with the app ([start]). */
object ArticleMedia {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var db: ArticleMediaDatabase? = null

    fun start(context: Context) {
        if (db != null) return
        db = Room.databaseBuilder(context.applicationContext, ArticleMediaDatabase::class.java, "brief_media")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val dao: ArticleMediaDao? get() = db?.dao()

    /** Saves what feed reading found (in the background; feed reading doesn't wait for it). */
    fun found(items: List<ArticleExtras>) {
        val useful = items.filter { it.mediaUrl != null || it.comments != null || it.image != null }
        if (useful.isEmpty()) return
        scope.launch { runCatching { dao?.putExtras(useful) } }
    }

    /**
     * An article's extras: saved ones, or, for articles read before this existed, the audio or
     * video kept in its picture field.
     */
    suspend fun of(articleId: String, picture: String?): ArticleExtras? =
        runCatching { dao?.extras(articleId) }.getOrNull() ?: fromPicture(articleId, picture)

    fun fromPicture(articleId: String, picture: String?): ArticleExtras? {
        val kind = MediaFiles.kindOf(picture, null) ?: return null
        return ArticleExtras(articleId = articleId, mediaUrl = picture, mediaKind = kind)
    }
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
}
