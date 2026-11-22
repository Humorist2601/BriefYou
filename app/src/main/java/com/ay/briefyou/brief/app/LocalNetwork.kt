package com.ay.briefyou.brief.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.ay.briefyou.domain.model.account.Account
import com.ay.briefyou.domain.model.account.AccountType
import com.ay.briefyou.domain.model.account.security.FeverSecurityKey
import com.ay.briefyou.domain.model.account.security.FreshRSSSecurityKey
import com.ay.briefyou.domain.model.account.security.GoogleReaderSecurityKey

/**
 * Android 17 asks the user before an app talks to devices on the home network (a FreshRSS or
 * Miniflux server at home, a feed on a local address). This tells whether Brief You needs that
 * permission, so it is asked for only by someone who uses such a server or feed.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** The permission exists from Android 17 (API 37). */
    val isAsked: Boolean
        get() = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The address of an account's server (null for the local account). */
    fun serverUrl(account: Account): String? =
        runCatching {
                when (account.type.id) {
                    AccountType.Fever.id -> FeverSecurityKey(account.securityKey).serverUrl
                    AccountType.GoogleReader.id -> GoogleReaderSecurityKey(account.securityKey).serverUrl
                    AccountType.FreshRSS.id -> FreshRSSSecurityKey(account.securityKey).serverUrl
                    else -> null
                }
            }
            .getOrNull()

    /** True for an address on the home network: private IPv4/IPv6 ranges and local names. */
    fun isLocalAddress(url: String?): Boolean {
        val host = url?.let { runCatching { Uri.parse(it).host }.getOrNull() }?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) {
            return true
        }
        val v4 = host.split('.').mapNotNull { it.toIntOrNull() }
        if (v4.size == 4 && host.all { it.isDigit() || it == '.' }) {
            return v4[0] == 10 ||
                (v4[0] == 192 && v4[1] == 168) ||
                (v4[0] == 172 && v4[1] in 16..31) ||
                (v4[0] == 169 && v4[1] == 254)
        }
        return host.contains(':') && (host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:"))
    }
}
