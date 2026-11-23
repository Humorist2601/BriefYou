/*
 * The pill's position maths and its wobble are taken from Trainable's floating bottom bar
 * (https://github.com/Emanuel5014/Trainable, NavPillMath.kt and NavPillJiggle.kt), which is
 * licensed under the GNU General Public License v3.0, like Brief You. Adapted for Brief You's
 * filter bar (package, names).
 */
package com.ay.briefyou.brief.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** Pill position maths: a position is measured in items (0f over the first, 1.5f between the second and third). */
internal object NavPillMath {
    private fun lastIndex(count: Int): Float = (count - 1).coerceAtLeast(0).toFloat()

    fun nearestIndex(position: Float, count: Int): Int =
        position.roundToInt().coerceIn(0, lastIndex(count).toInt())

    /** Like [nearestIndex] but stays on [current] until the pill is clearly closer to another item. */
    fun closestIndexWithHysteresis(current: Int, position: Float, count: Int, margin: Float = 0.1f): Int {
        val held = current.coerceIn(0, lastIndex(count).toInt())
        return if (abs(position - held) <= 0.5f + margin) held else nearestIndex(position, count)
    }

    /** 1f with the pill right on the item, fading to 0f one item away. */
    fun proximity(position: Float, index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)
}

/**
 * The wobble of the pill: moving makes it wider and flatter, and when it stops a spring overshoots
 * it into a brief squash the other way before it settles.
 *
 * [amount] is 0 at rest, positive while stretched and negative while squashed. Read it in draw or
 * layer lambdas, not in composition: it changes every frame while the pill wobbles.
 */
@Stable
internal class NavPillJiggle {
    var amount by mutableFloatStateOf(0f)
        private set

    private var speedOfAmount = 0f
    private var target = 0f
    private var lastPosition: Float? = null

    val scaleX: Float get() = 1f + WIDEN * amount
    val scaleY: Float get() = 1f - FLATTEN * amount

    private val isAtRest: Boolean get() = target == 0f && amount == 0f && speedOfAmount == 0f

    private fun step(position: Float, seconds: Float) {
        if (seconds <= 0f) return
        val moved = lastPosition?.let { abs(position - it) } ?: 0f
        lastPosition = position
        target = (moved / seconds / FULL_SPEED).coerceAtMost(1f)

        // A long frame must not make the integration unstable
        val dt = seconds.coerceAtMost(MAX_STEP_SECONDS)
        speedOfAmount += (STIFFNESS * (target - amount) - DAMPING * speedOfAmount) * dt
        val next = (amount + speedOfAmount * dt).coerceIn(MIN_AMOUNT, MAX_AMOUNT)

        val settled = target == 0f && abs(next) < REST_AMOUNT && abs(speedOfAmount) < REST_SPEED
        if (settled) speedOfAmount = 0f
        amount = if (settled) 0f else next
    }

    /** Drives the wobble from [position]; asks for frames only while the pill moves or wobbles. */
    suspend fun follow(position: () -> Float) {
        lastPosition = position()
        while (true) {
            val from = lastPosition
            snapshotFlow(position).first { it != from }
            var lastNanos = withFrameNanos { it }
            do {
                withFrameNanos { now ->
                    step(position(), (now - lastNanos) / NANOS_PER_SECOND)
                    lastNanos = now
                }
            } while (!isAtRest)
        }
    }

    private companion object {
        const val FULL_SPEED = 7f // items per second that give the full deformation
        const val WIDEN = 0.22f
        const val FLATTEN = 0.14f
        const val MIN_AMOUNT = -0.6f
        const val MAX_AMOUNT = 1.25f
        const val STIFFNESS = 320f
        const val DAMPING = 12.5f // about 0.35 of critical: two visible wobbles
        const val MAX_STEP_SECONDS = 1f / 30f
        const val REST_AMOUNT = 0.002f
        const val REST_SPEED = 0.02f
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

@Composable
internal fun rememberNavPillJiggle(position: () -> Float): NavPillJiggle {
    val jiggle = remember { NavPillJiggle() }
    LaunchedEffect(jiggle) { jiggle.follow(position) }
    return jiggle
}
