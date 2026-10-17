package me.ash.reader.ui.page.splash

import android.os.Build
import android.provider.Settings
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ash.reader.R

/*
 * The animated splash, drawn over the app right after the system splash hands over.
 *
 * It starts on the exact frame the system splash shows (icon A's front card alone, in the splash
 * colours, see ic_launcher_splash_screen and colors_splash), so the system splash is simply
 * removed, and then right away, in about 1.2 s:
 * 1. the two back cards fan out from behind the front card on a bouncy spring;
 * 2. the three lines of the brief write in from the left;
 * 3. the AI sparkle pops in with a little twist and settles;
 * 4. the front card straightens and grows until it fills the screen, its corners meeting the
 *    display's corners: the card is a window, and through it you see the app, which is already
 *    live underneath. The rest of the stack fades away.
 *
 * Everything is one Canvas in an offscreen layer (the window clears pixels), so each frame is one
 * draw pass with no recomposition.
 */

/**
 * The size the system splash draws `windowSplashScreenAnimatedIcon` at when it has no icon
 * background: the 108 dp canvas at 288 dp. Drawing the card at this size puts the first frame
 * exactly over the system one.
 */
private val SystemSplashIconSize = 288.dp

private const val LinesDelayMs = 150L
private const val SparkleDelayMs = 430L
private const val GrowDelayMs = 680L
private const val GrowDurationMs = 520

// The card (icon units) and where it turns around (as in the launcher icon).
private const val CardX = 35f
private const val CardY = 26f
private const val CardW = 38f
private const val CardH = 50f
private const val CardRadius = 7f
private val CardCenter = Offset(54f, 51f)
private val CardPivot = Offset(54f, 72f)
private const val CardScale = 0.8f
private const val FrontRotation = 3f

private val GrowEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
fun BriefSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val background = colorResource(R.color.splash_background)
    val back = colorResource(R.color.splash_card_back)
    val middle = colorResource(R.color.splash_card_middle)
    val front = colorResource(R.color.splash_card_front)
    val line = colorResource(R.color.splash_line)
    val sparkleColor = colorResource(R.color.splash_sparkle)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val displayCorner = displayCornerRadius()
    val iconPx = with(LocalDensity.current) { SystemSplashIconSize.toPx() }

    val fan = remember { Animatable(0f) }
    val lines = remember { Animatable(0f) }
    val sparkle = remember { Animatable(0f) }
    val twist = remember { Animatable(25f) }
    val grow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // With animations switched off in the system settings, go straight to the app.
        val animationScale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        if (animationScale == 0f) {
            currentOnFinished()
            return@LaunchedEffect
        }
        // No waiting: the two back cards start fanning out on the very first frame.
        coroutineScope {
            launch { fan.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
            launch {
                delay(LinesDelayMs)
                lines.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
            launch {
                delay(SparkleDelayMs)
                launch { twist.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
                sparkle.animateTo(1.35f, tween(140, easing = FastOutSlowInEasing))
                sparkle.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
            }
            delay(GrowDelayMs)
            grow.animateTo(1f, tween(GrowDurationMs, easing = GrowEasing))
        }
        currentOnFinished()
    }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                // The app underneath is already live; keep stray taps from reaching it blind.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                                it.consume()
                            }
                        }
                    }
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val unit = iconPx / 108f
        val g = grow.value
        val f = fan.value
        // How far the front card grows: until it covers the whole screen.
        val cardPx = unit * CardScale
        val endScale = max(size.width / (CardW * cardPx), size.height / (CardH * cardPx)) * 1.02f
        val k = exp(ln(endScale) * g)
        val stackAlpha = (1f - g / 0.4f).coerceIn(0f, 1f)
        val contentAlpha = (1f - g / 0.3f).coerceIn(0f, 1f)
        val fillAlpha = (1f - g / 0.5f).coerceIn(0f, 1f)
        // The front card's corners end as the display's own corners.
        val radiusPx = CardRadius * cardPx + (displayCorner - CardRadius * cardPx) * g
        val radius = radiusPx / (cardPx * k)

        drawRect(background)

        withTransform({
            translate(center.x - 54f * unit, center.y - 54f * unit)
            scale(unit, unit, pivot = Offset.Zero)
            translate(0f, 3f)
            scale(CardScale, CardScale, pivot = CardCenter)
        }) {
            val backAlpha = (f * 3f).coerceIn(0f, 1f) * stackAlpha
            if (backAlpha > 0f) {
                drawCard(rotation = FrontRotation + (-16f - FrontRotation) * f, color = back, alpha = backAlpha)
                drawCard(rotation = FrontRotation + (-7f - FrontRotation) * f, color = middle, alpha = backAlpha)
            }

            withTransform({
                scale(k, k, pivot = CardCenter)
                rotate(FrontRotation * (1f - g), pivot = CardPivot)
            }) {
                val rect = Size(CardW, CardH)
                val topLeft = Offset(CardX, CardY)
                if (g > 0f) {
                    // The window onto the app.
                    drawRoundRect(
                        color = Color.Black,
                        topLeft = topLeft,
                        size = rect,
                        cornerRadius = CornerRadius(radius),
                        blendMode = BlendMode.Clear,
                    )
                }
                if (fillAlpha > 0f) {
                    drawRoundRect(
                        color = front,
                        topLeft = topLeft,
                        size = rect,
                        cornerRadius = CornerRadius(radius),
                        alpha = fillAlpha,
                    )
                }
                if (contentAlpha > 0f) {
                    drawBriefLines(lines.value, line, contentAlpha)
                    drawSparkle(sparkle.value, twist.value, sparkleColor, contentAlpha)
                }
            }
        }
    }
}

/** One card of the stack, turned by [rotation] degrees around the stack's pivot. */
private fun DrawScope.drawCard(rotation: Float, color: Color, alpha: Float) {
    withTransform({ rotate(rotation, pivot = CardPivot) }) {
        drawRoundRect(
            color = color,
            topLeft = Offset(CardX, CardY),
            size = Size(CardW, CardH),
            cornerRadius = CornerRadius(CardRadius),
            alpha = alpha,
        )
    }
}

/** The brief's three lines, written in from the left as [progress] goes from 0 to 1. */
private fun DrawScope.drawBriefLines(progress: Float, color: Color, alpha: Float) {
    val first = progress.coerceIn(0f, 1f)
    val second = (progress * 1.2f - 0.2f).coerceIn(0f, 1f)
    val third = (progress * 1.5f - 0.5f).coerceIn(0f, 1f)
    if (first > 0f) {
        drawRoundRect(color, Offset(41f, 35f), Size(24f * first, 4.5f), CornerRadius(2.25f), alpha = alpha)
    }
    if (second > 0f) {
        drawRoundRect(color, Offset(41f, 44f), Size(26f * second, 3f), CornerRadius(1.5f), alpha = 0.55f * alpha)
    }
    if (third > 0f) {
        drawRoundRect(color, Offset(41f, 50f), Size(19f * third, 3f), CornerRadius(1.5f), alpha = 0.55f * alpha)
    }
}

/** The AI sparkle, at [scale] and turned by [twist] degrees. */
private fun DrawScope.drawSparkle(scale: Float, twist: Float, color: Color, alpha: Float) {
    if (scale <= 0f) return
    val c = Offset(62f, 63.5f)
    val r = 6.5f
    val path =
        Path().apply {
            moveTo(c.x, c.y - r)
            quadraticTo(c.x, c.y, c.x + r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y + r)
            quadraticTo(c.x, c.y, c.x - r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y - r)
            close()
        }
    withTransform({
        scale(scale, scale, pivot = c)
        rotate(twist, pivot = c)
    }) {
        drawPath(path, color, alpha = alpha)
    }
}

/** The display's own corner radius in px, or 28 dp where the phone doesn't say. */
@Composable
private fun displayCornerRadius(): Float {
    val view = LocalView.current
    val fallback = with(LocalDensity.current) { 28.dp.toPx() }
    return remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?.radius
                ?.toFloat()
                ?.takeIf { it > 0f } ?: fallback
        } else {
            fallback
        }
    }
}
