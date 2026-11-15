package me.ash.reader.brief.perf

import androidx.compose.runtime.CompositionLocalMap

/**
 * Nightly stutter log: which values handed down the screen ("composition locals") changed between
 * two builds of the same list row. When any of them changes above a list, Compose builds every
 * visible row again even though nothing in the rows changed, so this names the culprit.
 * Brief You.
 */
internal object LocalsDiff {
    fun describe(before: CompositionLocalMap?, now: CompositionLocalMap): String {
        if (before == null) return "no earlier values"
        if (before === now) return "same handed-down values"
        val a = before as? Map<*, *> ?: return "values changed (can't list them)"
        val b = now as? Map<*, *> ?: return "values changed (can't list them)"
        val changed = mutableListOf<String>()
        for ((key, holder) in b) {
            if (!a.containsKey(key)) changed += "added ${name(holder)}"
            else if (a[key] != holder) changed += name(holder)
        }
        for (key in a.keys) if (!b.containsKey(key)) changed += "removed ${name(a[key])}"
        return if (changed.isEmpty()) "handed-down values equal (new map)" else "handed-down values changed: " + changed.joinToString()
    }

    /** What a value holder holds: its class, so "ColorScheme", "Density" and so on. */
    private fun name(holder: Any?): String {
        if (holder == null) return "null"
        val value =
            runCatching {
                    var c: Class<*>? = holder.javaClass
                    var found: Any? = holder
                    while (c != null) {
                        val f = c.declaredFields.firstOrNull { it.name == "value" || it.name == "state" }
                        if (f != null) {
                            f.isAccessible = true
                            found = f.get(holder)
                            break
                        }
                        c = c.superclass
                    }
                    (found as? androidx.compose.runtime.State<*>)?.value ?: found
                }
                .getOrNull()
        val cls = value?.javaClass ?: return "${holder.javaClass.simpleName}(null)"
        return cls.name.substringAfterLast('.').ifEmpty { cls.name }
    }
}
