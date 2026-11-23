package com.ay.briefyou.brief.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val FadePositions = floatArrayOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.88f, 1f)
private val FadeAlphas = floatArrayOf(0f, 0.04f, 0.15f, 0.35f, 0.6f, 0.82f, 0.95f, 1f)

/**
 * The fade of the list into the bars at the screen's edges. A straight line from clear to solid
 * shows where it starts and stops; this one eases in and out (an S curve), so it has no visible
 * edge. [solidAtBottom]: the bottom bar's fade (solid next to the bar); otherwise the top bar's
 * (solid next to the bar above).
 */
fun edgeFadeBrush(color: Color, solidAtBottom: Boolean): Brush {
    val n = FadePositions.size
    val stops =
        Array(n) { i ->
            if (solidAtBottom) FadePositions[i] to color.copy(alpha = FadeAlphas[i])
            else (1f - FadePositions[n - 1 - i]) to color.copy(alpha = FadeAlphas[n - 1 - i])
        }
    return Brush.verticalGradient(colorStops = stops)
}
