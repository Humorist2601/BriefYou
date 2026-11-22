package com.ay.briefyou.infrastructure.android

import android.content.Context
import android.util.Log
import java.io.File
import java.lang.Thread.UncaughtExceptionHandler

/**
 * The uncaught exception handler for the application.
 *
 * Brief You: the original handler only started its report screen and then returned, so a crash on
 * the main thread ended that thread but not the app: the window stayed, froze, and Android reported
 * "not responding" five seconds later with nothing about the real error. This one keeps the trace
 * in a file (last-crash.txt, in the app's files) and then hands the crash on to Android's own
 * handler, so the app closes at once and the crash report carries the real exception.
 */
class CrashHandler(private val context: Context) : UncaughtExceptionHandler {

    private val previous: UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e("RLog", "uncaughtException on ${thread.name}", throwable)
        runCatching {
            File(context.filesDir, "last-crash.txt")
                .writeText("${thread.name}\n${throwable.stackTraceToString()}")
        }
        previous?.uncaughtException(thread, throwable)
    }
}
