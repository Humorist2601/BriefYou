package me.ash.reader.brief.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AiDependencies {
    fun summarizer(): GemmaSummarizer
}

/**
 * Starts loading the AI model in the background while you read (an article, the Unread list), so
 * the AI button answers without waiting for the model to load. See [GemmaSummarizer.preload].
 */
@Composable
fun PreloadAi(enabled: Boolean = true) {
    val context = LocalContext.current
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, AiDependencies::class.java)
                .summarizer()
                .preload()
        }
    }
}
