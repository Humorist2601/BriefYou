package me.ash.reader.infrastructure.di

import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import coil3.ImageLoader // Brief You: Coil 3
import coil3.disk.directory // Brief You: Coil 3
import coil3.gif.GifDecoder // Brief You: Coil 3
import coil3.gif.AnimatedImageDecoder // Brief You: Coil 3
import coil3.svg.SvgDecoder // Brief You: Coil 3
import coil3.disk.DiskCache // Brief You: Coil 3
import coil3.network.okhttp.OkHttpNetworkFetcherFactory // Brief You: Coil 3
import coil3.memory.MemoryCache // Brief You: Coil 3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Provides singleton [ImageLoader] for Coil.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader {
        return ImageLoader.Builder(context)
            // This slightly improves scrolling performance
            .coroutineContext(Dispatchers.Default) // Brief You: Coil 3
            .components {
                // Brief You: Coil 3 fetches through the shared OkHttpClient this way.
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient)) // Brief You
                // Support SVG decoding
                add(SvgDecoder.Factory())
                // Support GIF decoding
                add(
                    if (SDK_INT >= Build.VERSION_CODES.P) {
                        AnimatedImageDecoder.Factory() // Brief You: Coil 3
                    } else {
                        GifDecoder.Factory()
                    }
                )
            }
            // Brief You: keep icons and pictures in the disk cache even when a site says not to
            // (they were downloaded again every time); the cache still clears itself when full.
            // (Coil 3's network fetcher does this by default: CacheStrategy.DEFAULT.)
            // Enable disk cache
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("images"))
                    .maxSizePercent(0.02)
                    .build()
            )
            // Enable memory cache
            .memoryCache(
                MemoryCache.Builder() // Brief You: Coil 3
                    .maxSizePercent(context, 0.25) // Brief You
                    .build()
            )
            .build()
    }
}
