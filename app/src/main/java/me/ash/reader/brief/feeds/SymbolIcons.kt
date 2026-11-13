package me.ash.reader.brief.feeds

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material Symbols (outlined, drawn as lines) a feed or group can wear instead of its website icon. Stored as `icon:<name>`
 * (the name, not the drawing, so the choice survives app updates and backups).
 */
object SymbolIcons {
    const val PREFIX = "icon:"

    /** The icons in the order of the picker: their stored name and drawing. */
    val all: List<Pair<String, ImageVector>> by lazy { SYMBOL_PATHS.map { (name, path) -> name to symbol(name, path) } }

    private val byName by lazy { all.toMap() }

    private fun symbol(name: String, path: String): ImageVector =
        ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()

    /** The drawing for a stored `icon:<name>` value, or null if it isn't one. */
    fun of(value: String?): ImageVector? =
        if (value != null && value.startsWith(PREFIX)) byName[value.removePrefix(PREFIX)] else null

    /** Whether [value] is a stored icon this app knows (for imports). */
    fun isKnown(value: String): Boolean = of(value) != null
}
