package me.ash.reader.brief.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

/** Whether a settings row sits in a [SettingGroup] (it then pads itself for the card). */
val LocalInSettingGroup = compositionLocalOf { false }

/**
 * A section of settings as one joined card, like the app's other lists: each row on its own
 * surface, rounded big on the outside and small between rows, a small gap apart. Put the rows of
 * one section in it (Read You's SettingItem and the settings page's group items); they draw no
 * background of their own, so the card is drawn behind them here, row by row.
 */
@Composable
fun SettingGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh // the colour of the app's other cards (Filters, AI)
    // Each row's top and height, from the last layout, for drawing its card.
    var rows by remember { mutableStateOf(emptyList<Pair<Int, Int>>()) }
    Layout(
        content = { CompositionLocalProvider(LocalInSettingGroup provides true, content = content) },
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(Group.Outer))
                .drawBehind {
                    val count = rows.size
                    rows.forEachIndexed { index, (top, height) ->
                        val outline =
                            groupShape(index, count).createOutline(Size(size.width, height.toFloat()), layoutDirection, this)
                        translate(top = top.toFloat()) { drawOutline(outline, color) }
                    }
                },
    ) { measurables, constraints ->
        val gap = Group.Gap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        // Rows that show (a row hidden by its setting has no height and gets no card).
        val shown = placeables.filter { it.height > 0 }
        val height = shown.sumOf { it.height } + gap * (shown.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            val bounds = ArrayList<Pair<Int, Int>>(shown.size)
            shown.forEach {
                it.placeRelative(0, y)
                bounds += y to it.height
                y += it.height + gap
            }
            if (bounds != rows) rows = bounds
        }
    }
}
