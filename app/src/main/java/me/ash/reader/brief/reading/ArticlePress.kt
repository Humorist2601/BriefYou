package me.ash.reader.brief.reading

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.imageLoader
import coil.request.ImageRequest
import me.ash.reader.brief.feeds.FeedAccent
import me.ash.reader.domain.model.article.ArticleWithFeed

/**
 * An article row is pressed: what its reading page needs first is worked out while the finger is
 * still down (a tap lasts about 0.1 s), so the page opens with it instead of changing a moment
 * later:
 * - the article itself, read and its text tidied ([onPress], set by the list to its view model),
 *   so the page slides in with its title and text instead of empty;
 * - the picture's colour, which the reading page takes its colours from (arriving late, it faded
 *   the whole page's colours over 0.4 s, rebuilding the page every frame, and could reload the
 *   article);
 * - the picture itself, at the size the page's top picture asks for.
 * Brief You.
 */
object ArticlePress {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var lastUrl: String? = null

    /** Prepares the article for opening (the list's view model). */
    @Volatile var onPress: ((ArticleWithFeed) -> Unit)? = null

    fun pressed(context: Context, item: ArticleWithFeed) {
        onPress?.invoke(item)
        val url =
            FeedContent.listImage(item.article.img, item.article.link)?.takeIf { it.isNotBlank() }
                ?: return
        if (url == lastUrl) return
        lastUrl = url
        val appContext = context.applicationContext
        // The top picture: in the image cache when the page asks for it (same address and size).
        appContext.imageLoader.enqueue(
            ImageRequest.Builder(appContext)
                .data(url)
                .apply { url.extractReferer()?.let { addHeader("Referer", it) } }
                .size(me.ash.reader.ui.component.base.SIZE_1000)
                .scale(coil.size.Scale.FILL)
                .precision(coil.size.Precision.INEXACT)
                .build()
        )
        if (!FeedAccent.knownPictureColor(url).first) {
            scope.launch { FeedAccent.pictureColor(appContext, url) }
        }
    }
}
