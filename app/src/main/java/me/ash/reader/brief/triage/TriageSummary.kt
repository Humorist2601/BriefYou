package me.ash.reader.brief.triage

import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.brief.ai.GemmaSummarizer
import me.ash.reader.domain.model.article.ArticleWithFeed

/*
 * Long-press a Triage card for a short AI summary on the card itself: the on-device model writes
 * 3 key points while a turning blue–purple–pink–gold line glows around the card (the "AI is
 * working" look of Gemini and Apple Intelligence). Once written, the line fades to a faint glow
 * and the card stays paused until you swipe it. Summaries are kept for the session, so Undo
 * brings a card back with its summary.
 */

/** A card's summary: the text so far, and whether it's complete or why it isn't there. */
@Stable
data class CardSummary(
    val text: String = "",
    val done: Boolean = false,
    val noModel: Boolean = false,
    val failed: Boolean = false,
) {
    /** The key points written so far, one per bullet. */
    val points: List<String>
        get() =
            text.lines()
                .map { it.trim().removePrefix("•").removePrefix("*").removePrefix("-").trim() }
                .filter { it.isNotEmpty() }

    val working: Boolean
        get() = !done && !noModel && !failed
}

/** The summaries of this Triage run, by article. */
@Stable
class CardSummaries internal constructor(private val summarizer: GemmaSummarizer) {
    private val summaries = mutableStateMapOf<String, CardSummary>()
    private var job: Job? = null

    operator fun get(articleId: String): CardSummary? = summaries[articleId]

    /** Starts summarising [article] (once: a summary already there or underway is kept). */
    fun request(article: ArticleWithFeed, scope: CoroutineScope) {
        val id = article.article.id
        if (summaries[id]?.let { it.working || it.done } == true) return
        if (!summarizer.hasModel()) {
            summaries[id] = CardSummary(noModel = true)
            return
        }
        // One at a time: the model writes one thing at once.
        job?.cancel()
        summaries.entries.filter { it.value.working }.forEach { summaries.remove(it.key) }
        summaries[id] = CardSummary()
        job =
            scope.launch {
                try {
                    summarizer
                        .summarizeForCard(article.article.title, article.article.rawDescription)
                        .collect { summaries[id] = CardSummary(text = it) }
                    summaries[id] = (summaries[id] ?: CardSummary()).copy(done = true)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Swiped away (or another card asked): forget the half-written summary.
                    if (summaries[id]?.done != true) summaries.remove(id)
                    throw e
                } catch (e: Exception) {
                    summaries[id] = CardSummary(failed = true)
                }
            }
    }

    /** Stops a summary still being written (the card left the screen). */
    fun stop(articleId: String) {
        if (summaries[articleId]?.working == true) job?.cancel()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TriageSummaryDependencies {
    fun summarizer(): GemmaSummarizer
}

@Composable
fun rememberCardSummaries(): CardSummaries {
    val context = LocalContext.current
    return remember {
        CardSummaries(
            EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    TriageSummaryDependencies::class.java,
                )
                .summarizer()
        )
    }
}

/** The colours of the AI glow: Google's blue, purple, pink and gold, as in Gemini. */
private val GlowColors =
    intArrayOf(
        Color(0xFF4285F4).toArgb(),
        Color(0xFF9B72CB).toArgb(),
        Color(0xFFD96570).toArgb(),
        Color(0xFFF4B400).toArgb(),
        Color(0xFF4285F4).toArgb(),
    )

/**
 * The glowing border: a line of turning colour around the card while [summary] is being written,
 * fading to a faint, slower glow once it's done. Nothing is drawn without a summary.
 */
@Composable
fun Modifier.aiGlow(summary: CardSummary?, corner: Dp): Modifier {
    val target =
        when {
            summary == null || summary.noModel || summary.failed -> 0f
            summary.working -> 1f
            else -> 0.3f
        }
    val strength by animateFloatAsState(target, tween(500), label = "glow")
    if (strength <= 0.001f && target == 0f) return this
    val transition = rememberInfiniteTransition(label = "glow turn")
    val angle by
        transition.animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
            label = "glow angle",
        )
    return drawWithContent {
        drawContent()
        val width = 3.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val shader =
            SweepGradient(cx, cy, GlowColors, null).apply {
                setLocalMatrix(Matrix().apply { setRotate(angle, cx, cy) })
            }
        val r = corner.toPx() - width / 2f
        drawRoundRect(
            brush = ShaderBrush(shader),
            topLeft = Offset(width / 2f, width / 2f),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width),
            alpha = strength,
        )
    }
}

/** What the card shows instead of its description once a summary was asked for. */
@Composable
fun CardSummaryContent(summary: CardSummary, onOpenAiSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text =
                    stringResource(
                        when {
                            summary.noModel -> R.string.triage_summary_no_model_title
                            summary.failed -> R.string.triage_summary_failed
                            summary.working -> R.string.triage_summary_working
                            else -> R.string.triage_summary_done
                        }
                    ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        when {
            summary.noModel -> {
                Text(
                    text = stringResource(R.string.triage_summary_no_model),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onOpenAiSettings) {
                    Text(stringResource(R.string.triage_summary_open_settings))
                }
            }
            summary.failed -> {}
            summary.points.isEmpty() -> ShimmerLines()
            else ->
                summary.points.take(4).forEach { point ->
                    Row {
                        Box(
                            modifier =
                                Modifier.padding(top = 9.dp)
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF4285F4), Color(0xFFD96570))
                                        )
                                    )
                        )
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
        }
    }
}

/** Placeholder lines with a light sweeping across, while the model starts writing. */
@Composable
private fun ShimmerLines() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by
        transition.animateFloat(
            -1f,
            2f,
            infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer shift",
        )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val light = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf(1f, 0.86f, 0.93f, 0.6f).forEach { fraction ->
            Box(
                modifier =
                    Modifier.fillMaxWidth(fraction)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .graphicsLayer {}
                        .drawWithContent {
                            val w = size.width
                            drawRect(
                                Brush.linearGradient(
                                    colors = listOf(base, light, base),
                                    start = Offset(w * shift - w * 0.4f, 0f),
                                    end = Offset(w * shift + w * 0.4f, 0f),
                                )
                            )
                        }
                )
        }
    }
}
