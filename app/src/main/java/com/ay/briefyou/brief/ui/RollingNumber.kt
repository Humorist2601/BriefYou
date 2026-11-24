package com.ay.briefyou.brief.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * A number that rolls to its new value digit by digit, like the count in Home's cookie: a digit that
 * changes slides up (the value went up) or down and fades into the next. Columns are counted from
 * the right, so separators stay put. [number] is the text shown; [value] only gives the direction.
 */
@Composable
fun RollingNumber(value: Int, number: String, style: TextStyle, color: Color) {
    val density = LocalDensity.current
    val last = remember { mutableIntStateOf(value) }
    val up = value >= last.intValue
    SideEffect { last.intValue = value }
    val size = style.fontSize.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        number.forEachIndexed { i, ch ->
            key(number.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val d = with(density) { (size * 0.55f).sp.roundToPx() }
                        (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { if (up) d else -d } +
                            fadeIn(tween(200, delayMillis = 60)) togetherWith
                            slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { if (up) -d else d } +
                            fadeOut(tween(160))) using SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    label = "rollingDigit",
                ) { c ->
                    BasicText(text = c.toString(), color = { color }, maxLines = 1, softWrap = false, style = style)
                }
            }
        }
    }
}
