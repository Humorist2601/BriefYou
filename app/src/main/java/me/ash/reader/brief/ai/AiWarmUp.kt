package me.ash.reader.brief.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AiWarmUpDependencies {
    fun summarizer(): GemmaSummarizer
}

/** The AI model's warm-up (see [GemmaSummarizer.warmUp]), for screens that may use it next. */
@Composable
fun rememberAiWarmUp(): (AiUse, Int) -> Unit {
    val context = LocalContext.current
    return remember {
        val summarizer =
            EntryPointAccessors.fromApplication(context.applicationContext, AiWarmUpDependencies::class.java)
                .summarizer()
        { use, minUses -> summarizer.warmUp(use, minUses) }
    }
}

/**
 * Warms the model up when this screen appears, if [use] is something you do here regularly
 * (twice in the last week) and the phone can afford it. Three places: an article (its summary),
 * the Unread list (the gist) and Triage (card summaries).
 */
@Composable
fun WarmUpAiIfHabit(use: AiUse) {
    val warmUp = rememberAiWarmUp()
    LaunchedEffect(use) {
        // After the screen's opening animation, so loading never makes it stutter.
        kotlinx.coroutines.delay(1_500)
        warmUp(use, GemmaSummarizer.HABIT_MIN_USES)
    }
}
