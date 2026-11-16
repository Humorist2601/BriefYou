package me.ash.reader.brief.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.ash.reader.infrastructure.android.MainActivity
import me.ash.reader.ui.page.common.ExtraName

/**
 * Tapping the podcast's or read aloud's notification opens the article that is playing (an
 * article already open stays as it is, and the list keeps its filter). Without an article (the
 * Gist read aloud) it just opens the app. Brief You.
 */
object PlayerIntents {
    private const val FROM_PLAYER = "brief_from_player"

    /** [readAloud]: for read aloud's notification (each notification has its own link). */
    fun open(context: Context, articleId: String?, readAloud: Boolean = false): PendingIntent =
        PendingIntent.getActivity(
            context,
            if (readAloud) READ_ALOUD_CODE else PODCAST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(FROM_PLAYER, true)
                articleId?.let { putExtra(ExtraName.ARTICLE_ID, it) }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The intent came from a player's notification (read once). */
    fun fromPlayer(intent: Intent): Boolean =
        intent.getBooleanExtra(FROM_PLAYER, false).also { if (it) intent.removeExtra(FROM_PLAYER) }

    /** The article a read aloud key names ("summary:<id>" for an article's summary). */
    fun articleOf(readAloudKey: String?): String? =
        readAloudKey?.removePrefix("summary:")?.takeIf { it.isNotBlank() && it != "gist" }

    private const val PODCAST_CODE = 4202
    private const val READ_ALOUD_CODE = 4203
}
