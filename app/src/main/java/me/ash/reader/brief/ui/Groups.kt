package me.ash.reader.brief.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brief You's connected lists, one design everywhere: rows rounded big on the group's outside and
 * small between them, a small gap apart. Lists on pages and sheets use the big corners; lists
 * nested inside something smaller (a dialog, a card) use the nested ones.
 */
object Group {
    val Outer = 24.dp
    val Inner = 6.dp
    /** The space between two rows (draw each row with half of it above and below). */
    val Gap = 3.dp
    val NestedOuter = 16.dp
    val NestedInner = 4.dp
}

/** The shape of row [index] of [count] in a connected list. */
fun groupShape(index: Int, count: Int, outer: Dp = Group.Outer, inner: Dp = Group.Inner): RoundedCornerShape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The same for a list nested in something smaller (a dialog, a card). */
fun nestedGroupShape(index: Int, count: Int): RoundedCornerShape =
    groupShape(index, count, Group.NestedOuter, Group.NestedInner)
