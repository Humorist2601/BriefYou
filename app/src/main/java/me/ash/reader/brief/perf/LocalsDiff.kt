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
            if (!a.containsKey(key)) changed += "added ${name(holder)} from ${origin(key)}"
            else if (a[key] != holder) changed += "${name(holder)} from ${origin(key)} (${sameValue(a[key], holder)})"
        }
        for (key in a.keys) if (!b.containsKey(key)) changed += "removed ${name(a[key])} from ${origin(key)}"
        return if (changed.isEmpty()) "handed-down values equal (new map)" else "handed-down values changed: " + changed.joinToString()
    }

    /**
     * Where a handed-down value is declared: the class of its default-value code, which carries
     * the file it's declared in (such as "...CompositionLocalsKt$$ExternalSyntheticLambda3").
     */
    private fun origin(key: Any?): String {
        if (key == null) return "?"
        fun fieldsOf(o: Any): List<Any?> =
            generateSequence<Class<*>>(o.javaClass) { it.superclass }
                .takeWhile { it != Any::class.java }
                .flatMap { it.declaredFields.asSequence() }
                .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .mapNotNull { f -> runCatching { f.isAccessible = true; f.get(o) }.getOrNull() }
                .toList()
        return runCatching {
                // The key's default holder, and in it the code that makes the default value.
                for (v in fieldsOf(key)) {
                    if (v is Function<*>) return v.javaClass.name
                    if (v != null && v.javaClass.name.startsWith("androidx.compose.runtime")) {
                        fieldsOf(v).firstOrNull { it is Function<*> }?.let { return it.javaClass.name }
                    }
                }
                key.javaClass.name
            }
            .getOrDefault("?")
    }

    /** Whether the old and new holders hold the very same value (then only the holder is new). */
    private fun sameValue(a: Any?, b: Any?): String {
        val x = valueOf(a)
        val y = valueOf(b)
        return when {
            x === y -> "same value, new holder"
            x == y -> "equal value"
            else -> "new value"
        }
    }

    private fun valueOf(holder: Any?): Any? {
        return runCatching {
                var c: Class<*>? = holder?.javaClass
                while (c != null) {
                    val f = c.declaredFields.firstOrNull { it.name == "value" || it.name == "state" }
                    if (f != null) {
                        f.isAccessible = true
                        val v = f.get(holder)
                        return (v as? androidx.compose.runtime.State<*>)?.value ?: v
                    }
                    c = c.superclass
                }
                holder
            }
            .getOrNull()
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
