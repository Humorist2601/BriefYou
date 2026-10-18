package me.ash.reader.ui.page.home.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ash.reader.R

/**
 * Inbox zero: when the Unread list empties, a check on an Expressive cookie shape pops in with
 * "You're all caught up", while small Expressive shapes burst out around it and fade. About 1.8 s,
 * once, then it goes away by itself. Skipped when animations are off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InboxZeroCelebration(visible: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    val haptic = LocalHapticFeedback.current
    val badge = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val animationScale =
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        if (animationScale == 0f) {
            onFinished()
            return@LaunchedEffect
        }
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        coroutineScope {
            launch { badge.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
            launch { burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        }
        delay(700)
        fade.animateTo(0f, tween(300, easing = LinearEasing))
        onFinished()
    }

    Box(
        modifier = modifier.size(280.dp).graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center,
    ) {
        CelebrationBurst(progress = { burst.value })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier.size(72.dp)
                        .graphicsLayer {
                            scaleX = badge.value
                            scaleY = badge.value
                            rotationZ = (1f - badge.value) * -45f
                        }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.reading_all_caught_up),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { alpha = badge.value.coerceIn(0f, 1f) },
            )
        }
    }
}

private const val PIECES = 12

/**
 * Small Expressive shapes bursting out from the middle, turning and fading as [progress] goes
 * from 0 to 1. Used by the inbox zero celebration and Triage's "All caught up".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CelebrationBurst(progress: () -> Float, modifier: Modifier = Modifier) {
    val colors =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
    val shapes =
        listOf(
            MaterialShapes.Sunny,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Pill,
            MaterialShapes.Cookie6Sided,
            MaterialShapes.Flower,
            MaterialShapes.Burst,
            MaterialShapes.Triangle,
            MaterialShapes.Heart,
        )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // The burst: shapes flying out from the middle, turning and fading as they go.
        val b = progress()
        for (i in 0 until PIECES) {
            val angle = Math.toRadians(i * 360.0 / PIECES - 90.0)
            val distance = 40f + 90f * b
            Box(
                modifier =
                    Modifier.size(if (i % 2 == 0) 18.dp else 14.dp)
                        .graphicsLayer {
                            translationX = (cos(angle) * distance).toFloat() * density
                            translationY = (sin(angle) * distance).toFloat() * density
                            rotationZ = (if (i % 2 == 0) 1 else -1) * 180f * b
                            val s = 0.4f + 0.8f * b
                            scaleX = s
                            scaleY = s
                            alpha = ((1f - b) * 1.4f).coerceIn(0f, 1f)
                        }
                        .clip(shapes[i % shapes.size].toShape())
                        .background(colors[i % colors.size])
            )
        }
    }
}

