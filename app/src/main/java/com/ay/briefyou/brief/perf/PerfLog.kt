package com.ay.briefyou.brief.perf

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.FrameMetrics
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
import com.ay.briefyou.BuildConfig
import com.ay.briefyou.ui.ext.showToast

/**
 * A stutter recorder, only in the Nightly test build: after each marked action (opening a list,
 * opening an article, switching place in Triage) it notes every slow frame (how long its parts
 * took) and, while the main thread is stuck, which code it is running (sampled every few
 * milliseconds). Tips & support has a button that copies it to send over. Nothing leaves the
 * phone by itself. Brief You.
 */
object PerfLog {
    val enabled: Boolean = BuildConfig.BUILD_TYPE == "nightly"

    /** The screen shown (set from the navigation). */
    @Volatile var screen: String = "start"

    /** How long after an action its frames and samples are counted for it. */
    private const val WINDOW_MS = 1_500L
    /** The main thread counts as stuck once a frame is this late. */
    private const val STUCK_MS = 20L
    private const val SAMPLE_EVERY_MS = 4L
    /** Kept per kind of action, so many of one kind don't push out the others. */
    private const val ACTIONS_KEPT_PER_KIND = 12

    private class Action(val name: String, val at: Long, val window: Long) {
        val frames = ArrayList<String>()
        var frameCount = 0
        var lateCount = 0
        val samples = HashMap<String, Int>()
        var sampleCount = 0
        /** What happened in the app after the action (data arriving, rows built, the slide ending). */
        val events = ArrayList<String>()
        var lastEvent: String? = null
        var lastEventAt = 0L
        var lastEventRepeats = 0
    }

    private val actions = ArrayDeque<Action>()
    @Volatile private var current: Action? = null
    @Volatile private var lastFrameAt = 0L
    private var attached = false

    private var tick: Choreographer.FrameCallback? = null
    private var ticking = false

    /** An action whose frames are worth looking at starts now (call it on the main thread). */
    fun action(name: String, windowMs: Long = WINDOW_MS) {
        if (!enabled) return
        val a = Action(name, SystemClock.uptimeMillis(), windowMs)
        synchronized(this) {
            // What happened just before (a press prepares what the tap opens): kept with the action.
            loose.filter { a.at - it.first <= 1_500 }.forEach { (at, event) -> a.events += "${at - a.at} ms: $event" }
            loose.clear()
            actions.addLast(a)
            // The oldest of this kind goes once there are too many of it.
            if (actions.count { it.name == name } > ACTIONS_KEPT_PER_KIND) {
                actions.remove(actions.first { it.name == name })
            }
        }
        current = a
        lastFrameAt = a.at
        // The frame tick runs only during an action's window (so it costs nothing the rest of the time).
        val t = tick ?: return
        if (!ticking) {
            ticking = true
            Choreographer.getInstance().postFrameCallback(t)
        }
    }

    private fun live(now: Long): Action? = current?.takeIf { now - it.at <= it.window }

    /** Notes from outside any action, handed to the next action that starts soon after. */
    private val loose = ArrayDeque<Pair<Long, String>>()

    /**
     * Something happened that may explain a slow frame (any thread). The same event again within
     * 30 ms is counted on the same line ("row built ×12").
     */
    fun note(event: String) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        val a = live(now)
        if (a == null) {
            synchronized(this) {
                loose.addLast(now to event)
                while (loose.size > 40) loose.removeFirst()
            }
            return
        }
        synchronized(this) {
            if (event == a.lastEvent && now - a.lastEventAt <= 30 && a.events.isNotEmpty()) {
                a.lastEventRepeats++
                a.lastEventAt = now
                val first = a.events.last().substringBefore(" ×")
                a.events[a.events.size - 1] = "$first ×${a.lastEventRepeats}"
            } else {
                a.lastEvent = event
                a.lastEventAt = now
                a.lastEventRepeats = 1
                a.events += "+${now - a.at} ms: $event"
            }
        }
    }

    fun attach(activity: Activity) {
        if (!enabled || attached || Build.VERSION.SDK_INT < 26) return
        attached = true
        val thread = HandlerThread("perf-log").also { it.start() }
        val handler = Handler(thread.looper)

        // Every frame's parts, for the frames right after an action.
        activity.window.addOnFrameMetricsAvailableListener(
            { _, m, _ ->
                val now = SystemClock.uptimeMillis()
                val a = live(now) ?: return@addOnFrameMetricsAvailableListener
                fun ms(id: Int) = m.getMetric(id) / 1_000_000
                val total = ms(FrameMetrics.TOTAL_DURATION)
                // Late: past the phone's own deadline for the frame (about 8 ms at 120 Hz), so it
                // missed its turn on screen. Before Android 12, a 60 Hz frame's 17 ms.
                val late =
                    if (Build.VERSION.SDK_INT >= 31) {
                        m.getMetric(FrameMetrics.TOTAL_DURATION) > m.getMetric(FrameMetrics.DEADLINE)
                    } else {
                        total >= 17
                    }
                synchronized(this) {
                    a.frameCount++
                    if (late) a.lateCount++
                    if (late && a.frames.size < 40) {
                        val gpu = if (Build.VERSION.SDK_INT >= 31) " gpu ${ms(FrameMetrics.GPU_DURATION)}" else ""
                        a.frames +=
                            "+${now - a.at} ms: $total ms (input ${ms(FrameMetrics.INPUT_HANDLING_DURATION)}, " +
                                "compose/anim ${ms(FrameMetrics.ANIMATION_DURATION)}, layout ${ms(FrameMetrics.LAYOUT_MEASURE_DURATION)}, " +
                                "draw ${ms(FrameMetrics.DRAW_DURATION)}, sync ${ms(FrameMetrics.SYNC_DURATION)}, " +
                                "issue ${ms(FrameMetrics.COMMAND_ISSUE_DURATION)}, swap ${ms(FrameMetrics.SWAP_BUFFERS_DURATION)}$gpu, " +
                                "wait before ${ms(FrameMetrics.UNKNOWN_DELAY_DURATION)})"
                    }
                }
            },
            handler,
        )

        // A tick on the main thread every frame of an action's window: when it stops coming, the
        // main thread is stuck.
        val main = Looper.getMainLooper().thread
        val choreographer = Choreographer.getInstance()
        tick =
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val now = SystemClock.uptimeMillis()
                    lastFrameAt = now
                    if (live(now) != null) choreographer.postFrameCallback(this) else ticking = false
                }
            }

        // While it is stuck right after an action: what it is running.
        val sampler =
            object : Runnable {
                override fun run() {
                    val now = SystemClock.uptimeMillis()
                    val a = live(now)
                    if (a != null && now - lastFrameAt >= STUCK_MS) {
                        val where = describe(main.stackTrace)
                        synchronized(this@PerfLog) {
                            a.sampleCount++
                            a.samples[where] = (a.samples[where] ?: 0) + 1
                        }
                    }
                    handler.postDelayed(this, SAMPLE_EVERY_MS)
                }
            }
        handler.post(sampler)
    }

    /**
     * Where the main thread is: the innermost frame (what is actually working, often Compose,
     * SQLite, text or a picture), then the app's own frames that led there, innermost first.
     */
    private fun describe(stack: Array<StackTraceElement>): String {
        if (stack.isEmpty()) return "?"
        val top = stack.first().let { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        val inner =
            stack.take(40).firstOrNull { !it.className.startsWith("java.") && !it.className.startsWith("kotlin.") }
                ?.let { "${it.className}.${it.methodName}" }
        val app =
            stack.filter { it.className.startsWith("com.ay.briefyou") }
                .take(4)
                .joinToString(" < ") { "${it.className.removePrefix("com.ay.briefyou.").substringBefore('$')}.${it.methodName}:${it.lineNumber}" }
        return "$top | ${inner ?: ""} | ${app.ifEmpty { "(no app code)" }}"
    }

    @Synchronized
    fun text(): String = buildString {
        appendLine("Brief You stutter log (${Build.MODEL}, Android ${Build.VERSION.RELEASE}, ${BuildConfig.VERSION_NAME})")
        appendLine("Each action: its late frames (past the phone's deadline, about 8 ms at 120 Hz) in the ${WINDOW_MS / 1000.0} s after it, and what the")
        appendLine("main thread was running while stuck (each sample is about $SAMPLE_EVERY_MS ms).")
        appendLine("\"draw\" includes Compose measuring and building list rows; \"wait before\" is the")
        appendLine("main thread busy before the frame could start.")
        for (a in actions) {
            appendLine()
            appendLine("== ${a.name} (${a.window / 1000.0} s, ${a.lateCount} of ${a.frameCount} frames late, stuck ~${a.sampleCount * SAMPLE_EVERY_MS} ms)")
            a.frames.forEach { appendLine("  $it") }
            if (a.events.isNotEmpty()) {
                appendLine("  what happened:")
                a.events.forEach { appendLine("    $it") }
            }
            a.samples.entries.sortedByDescending { it.value }.take(12).forEach { (where, n) ->
                appendLine("  ~${n * SAMPLE_EVERY_MS} ms: $where")
            }
        }
    }
}

/** A button in Tips & support (Nightly only) that copies the stutter log. */
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
                clipboard.setPrimaryClip(ClipData.newPlainText("Brief You stutter log", PerfLog.text()))
                context.showToast("Stutter log copied")
            }
        ) {
            Text("Copy stutter log")
        }
    }
}
