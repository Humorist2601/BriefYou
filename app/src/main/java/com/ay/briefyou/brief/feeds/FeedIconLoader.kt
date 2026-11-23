package com.ay.briefyou.brief.feeds

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory

/**
 * Feed icons are kept in a store of their own, in the app's files folder, not with the article
 * pictures in the cache folder: the article pictures push small icons out of that cache when it
 * fills up, and Android or "Clear cache" can empty the cache folder, so every icon was downloaded
 * again. Here an icon is saved the first time it loads and read from the phone after that. When
 * the store is full the icons used longest ago make room.
 */
object FeedIconLoader {
    private const val MAX_BYTES = 64L * 1024 * 1024
    @Volatile private var loader: ImageLoader? = null

    /** Removes a saved icon (the key of an icon in Coil's disk cache is its address). Does nothing if the store was never opened. */
    fun forget(iconUrl: String) {
        runCatching { loader?.diskCache?.remove(iconUrl) }
        runCatching { loader?.memoryCache?.remove(coil3.memory.MemoryCache.Key(iconUrl)) }
    }

    fun get(context: Context): ImageLoader =
        loader ?: synchronized(this) {
            loader ?: SingletonImageLoader.get(context.applicationContext).newBuilder()
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.filesDir.resolve("feed_icons"))
                        .maxSizeBytes(MAX_BYTES)
                        .build()
                }
                .build()
                .also { loader = it }
        }
}
