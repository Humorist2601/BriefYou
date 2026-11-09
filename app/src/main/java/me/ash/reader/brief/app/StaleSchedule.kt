package me.ash.reader.brief.app

import androidx.work.WorkInfo
import androidx.work.WorkManager

/**
 * Whether a periodic job's next run is much further away than its interval allows: the phone's
 * clock was set ahead and back, and the job is still waiting for that later date (it would not
 * run again until then). Such a job is cancelled and scheduled again from now.
 */
fun WorkManager.scheduledTooFar(uniqueName: String, intervalMinutes: Long): Boolean {
    val info =
        runCatching { getWorkInfosForUniqueWork(uniqueName).get().firstOrNull() }.getOrNull()
            ?: return false
    if (info.state != WorkInfo.State.ENQUEUED) return false
    val next = info.nextScheduleTimeMillis
    if (next == Long.MAX_VALUE) return false
    // Twice the interval, and never less than a day, so normal waits and backoffs are left alone.
    val allowed = maxOf(intervalMinutes * 2 * 60_000L, 24 * 60 * 60_000L)
    return next - System.currentTimeMillis() > allowed
}
