package me.ash.reader.brief.tour

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import kotlinx.coroutines.delay
import me.ash.reader.R

/** Where a hint sits: above what it points at, or inside it near the bottom. */
enum class HintPlacement { Above, InsideBottom }

/**
 * One-time hints: a small bubble the first time you reach something whose best part is easy to
 * miss (holding a Triage card, the AI button, Triage itself). "Got it" and it never shows again;
 * one at a time. Remembered on the phone.
 */
object BriefHints {
    const val TRIAGE_HOLD = "triage_hold"
    const val READING_AI = "reading_ai"
    const val UNREAD_TRIAGE = "unread_triage"

    /** The hint on screen now, so two never show at once. */
    internal var showing by mutableStateOf<String?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("brief_hints", Context.MODE_PRIVATE)

    fun seen(context: Context, key: String): Boolean = prefs(context).getBoolean(key, false)

    fun markSeen(context: Context, key: String) {
        prefs(context).edit { putBoolean(key, true) }
        if (showing == key) showing = null
    }

    /** Shows the hints again (Settings → the tour). */
    fun reset(context: Context) {
        prefs(context).edit { clear() }
    }
}

/**
 * A one-time hint for whatever this is placed in (put it inside the Box of the thing it points
 * at: the bubble is placed against that). Shown a moment after [enabled] turns on, if it wasn't
 * seen before and no other hint is showing.
 */
@Composable
fun BriefHint(key: String, text: AnnotatedString, enabled: Boolean = true, placement: HintPlacement = HintPlacement.Above) {
    val context = LocalContext.current
    var seen by remember(key) { mutableStateOf(BriefHints.seen(context, key)) }
    var ready by remember(key) { mutableStateOf(false) }
    // Leaving the screen before "Got it" frees the slot (and it shows again next time).
    androidx.compose.runtime.DisposableEffect(key) {
        onDispose { if (BriefHints.showing == key) BriefHints.showing = null }
    }
    LaunchedEffect(key, enabled, seen) {
        ready = false
        if (!enabled || seen) return@LaunchedEffect
        // After the screen has settled, and only if nothing else is pointing at something.
        delay(900)
        if (BriefHints.showing == null) {
            BriefHints.showing = key
            ready = true
        }
    }
    if (!ready || !enabled || seen) return
    val pop = remember(key) { Animatable(0.85f) }
    LaunchedEffect(key) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.roundToPx() }
    val provider =
        remember(placement, gap) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val x =
                        (anchorBounds.center.x - popupContentSize.width / 2)
                            .coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap))
                    val y =
                        when (placement) {
                            HintPlacement.Above -> anchorBounds.top - popupContentSize.height - gap
                            HintPlacement.InsideBottom -> anchorBounds.bottom - popupContentSize.height - gap * 2
                        }.coerceAtLeast(gap)
                    return IntOffset(x, y)
                }
            }
        }
    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Surface(
            // Material's rich tooltip colours: a raised surface in the theme's own tone (dark in
            // dark mode), not the inverse colours of a one-line tooltip.
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 6.dp,
            modifier =
                Modifier.widthIn(max = 260.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = ((pop.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
                },
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                TextButton(
                    onClick = {
                        BriefHints.markSeen(context, key)
                        seen = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.hint_got_it))
                }
            }
        }
    }
}

/** "**Bold** rest": the hint's first words in bold, the way the bubbles read. */
fun hintText(bold: String, rest: String): AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        append(bold)
        pop()
        append(' ')
        append(rest)
    }

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface HintDependencies {
    fun summarizer(): me.ash.reader.brief.ai.GemmaSummarizer
}

/** Whether the AI model is ready, for hints about AI features. */
@Composable
fun rememberHasModel(): Boolean {
    val context = LocalContext.current
    val summarizer =
        remember {
            dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, HintDependencies::class.java)
                .summarizer()
        }
    val state by summarizer.modelState.collectAsState()
    return state is me.ash.reader.brief.ai.ModelState.Ready
}
