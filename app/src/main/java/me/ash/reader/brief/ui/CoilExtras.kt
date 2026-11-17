package me.ash.reader.brief.ui

import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/**
 * Coil 2's addHeader for Coil 3 (which takes the headers as one set): used for the Referer some
 * sites want before they send a picture. Only one header is set per request here.
 */
fun ImageRequest.Builder.addHeader(name: String, value: String): ImageRequest.Builder =
    httpHeaders(NetworkHeaders.Builder().set(name, value).build())
