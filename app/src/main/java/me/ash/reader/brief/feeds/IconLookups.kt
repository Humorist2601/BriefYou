package me.ash.reader.brief.feeds

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit

/**
 * Feeds whose website icon couldn't be found are looked up again only once a week, instead of on
 * every sync (which searched the web again for each of them, one by one for Fever accounts).
 * Refreshing a feed's icon by hand always searches. Kept by website address in Brief You's own
 * storage; small, and forgotten once an icon is found.
 */
object IconLookups {
    private const val PREFS = "icon_lookups"
    private const val RETRY_AFTER_MS = 7L * 24 * 60 * 60 * 1000

    /** Whether to search for [site]'s icon now (it didn't fail in the last week). */
    fun shouldTry(context: Context, site: String): Boolean {
        val failedAt = prefs(context).getLong(site, 0L)
        return failedAt == 0L || System.currentTimeMillis() - failedAt > RETRY_AFTER_MS
    }

    /**
     * Remembers whether the search for [site]'s icon found one. A search made offline (it finds
     * nothing either way) isn't remembered as a failure.
     */
    fun record(context: Context, site: String, found: Boolean) {
        if (!found && !online(context)) return
        prefs(context).edit {
            if (found) remove(site) else putLong(site, System.currentTimeMillis())
        }
    }

    private fun online(context: Context): Boolean =
        runCatching {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
            }
            .getOrDefault(false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
