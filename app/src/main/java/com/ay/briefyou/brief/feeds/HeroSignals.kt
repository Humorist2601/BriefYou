package com.ay.briefyou.brief.feeds

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Small readings for the Home hero card. */
object HeroSignals {
    private const val FILE = "brief_home"
    private const val KEY = "last_seen"

    /** When Home was last left (milliseconds), 0 before the first time. */
    fun lastSeen(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY, 0L)

    fun markSeen(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY, System.currentTimeMillis()).apply()
    }
}

/** Whether the phone has a working internet connection, kept up to date while shown. */
@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    fun now(): Boolean =
        manager?.let { m ->
            m.getNetworkCapabilities(m.activeNetwork)?.reaches() == true
        } ?: true
    var online by remember { mutableStateOf(now()) }
    DisposableEffect(manager) {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { online = capabilities.reaches() }
                override fun onLost(network: Network) { online = now() }
            }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    return online
}

/** A connection that really reaches the internet (a Wi-Fi without internet does not count). */
private fun NetworkCapabilities.reaches(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
