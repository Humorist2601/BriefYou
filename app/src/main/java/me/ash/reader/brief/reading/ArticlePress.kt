package me.ash.reader.brief.reading

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.ash.reader.brief.feeds.FeedAccent
import me.ash.reader.domain.model.article.ArticleWithFeed

/**
 * An article row is pressed: what its reading page needs first is worked out while the finger is
 * still down (a tap lasts about 0.1 s), so the page opens with it instead of changing a moment
 * later. For now that is the picture's colour, which the reading page takes its colours from:
 * arriving late, it faded the whole page's colours over 0.4 s (rebuilding the page every frame)
 * and could reload the article. Brief You.
 */
object ArticlePress {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastUrl: String? = null

    fun pressed(context: Context, item: ArticleWithFeed) {
        val url =
            FeedContent.listImage(item.article.img, item.article.link)?.takeIf { it.isNotBlank() }
                ?: return
        if (url == lastUrl || FeedAccent.knownPictureColor(url).first) return
        lastUrl = url
        val appContext = context.applicationContext
        scope.launch { FeedAccent.pictureColor(appContext, url) }
    }
}
