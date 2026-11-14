package me.ash.reader.brief.perf

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.ash.reader.BuildConfig
import me.ash.reader.ui.ext.showToast

/**
 * A small timing log for finding what is slow, only in the Nightly test build: how long some
 * actions take and which frames were slow on which screen. Tips & support has a button that copies
 * it, to send over. Nothing leaves the phone by itself. Brief You.
 */
object PerfLog {
    val enabled: Boolean = BuildConfig.BUILD_TYPE == "nightly"

    /** The screen the frames below are counted for. */
    @Volatile var screen: String = "start"

    private class Stat(var frames: Int = 0, var janky: Int = 0, var worst: Long = 0)

    private val stats = LinkedHashMap<String, Stat>()
    private val events = ArrayDeque<String>()
    private val started = SystemClock.elapsedRealtime()
    private var thread: HandlerThread? = null

    /** An action that took [ms] milliseconds. */
    fun note(what: String, ms: Long) {
        if (!enabled) return
        add("${stamp()}  $what: $ms ms")
    }

    /** Now, to pass to [since] when the action ends. */
    fun mark(): Long = SystemClock.elapsedRealtime()

    fun since(what: String, from: Long) = note(what, SystemClock.elapsedRealtime() - from)

    private fun stamp(): String {
        val s = (SystemClock.elapsedRealtime() - started) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    @Synchronized
    private fun add(line: String) {
        events.addLast(line)
        while (events.size > 300) events.removeFirst()
    }

    /** Starts counting the window's frames. */
    fun attach(activity: Activity) {
        if (!enabled || Build.VERSION.SDK_INT < 24) return
        val t = HandlerThread("perf-log").also { it.start() }
        thread = t
        val window: Window = activity.window
        window.addOnFrameMetricsAvailableListener(
            { _, metrics, _ ->
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000
                val where = screen
                synchronized(this) {
                    val s = stats.getOrPut(where) { Stat() }
                    s.frames++
                    if (total > 33) s.janky++
                    if (total > s.worst) s.worst = total
                }
                if (total >= 50) {
                    val layout = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1_000_000
                    val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION) / 1_000_000
                    val sync = metrics.getMetric(FrameMetrics.SYNC_DURATION) / 1_000_000
                    add("${stamp()}  slow frame on $where: $total ms (layout $layout, draw $draw, sync $sync)")
                }
            },
            Handler(t.looper),
        )
    }

    @Synchronized
    fun text(): String = buildString {
        appendLine("Brief You timing log (${Build.MODEL}, Android ${Build.VERSION.RELEASE})")
        appendLine()
        appendLine("Frames per screen (janky = over 33 ms):")
        for ((name, s) in stats) appendLine("  $name: ${s.frames} frames, ${s.janky} janky, worst ${s.worst} ms")
        appendLine()
        appendLine("Events (newest last):")
        events.forEach { appendLine(it) }
    }
}

/** A button in Tips & support (Nightly only) that copies the timing log. */
@Composable
fun PerfLogButton() {
    if (!PerfLog.enabled) return
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        FilledTonalButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Brief You timing log", PerfLog.text()))
                context.showToast("Timing log copied")
            }
        ) {
            Text("Copy timing log")
        }
    }
}
