package com.ay.briefyou.brief.reading

import java.net.URI
import com.ay.briefyou.ui.ext.extractDomain

/**
 * The page a request comes from, as a real address ("https://example.com/"). The original app sent the
 * bare host ("example.com"), which isn't a valid Referer: image hosts with hotlink protection
 * refuse it with 403, so thumbnails and pictures went missing .
 */
fun String?.extractReferer(): String? {
    if (isNullOrBlank()) return null
    val uri = runCatching { URI(trim()) }.getOrNull()
    val host = uri?.host ?: return extractDomain()?.let { "https://$it/" }
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: "https"
    return "$scheme://$host/"
}
