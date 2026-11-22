package com.ay.briefyou.ui.ext

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Convert [InputStream] to [String].
 */
fun InputStream.readString(): String =
    BufferedReader(InputStreamReader(this)).useLines { lines ->
        val results = StringBuilder()
        lines.forEach { results.append(it) }
        results.toString()
    }

/**
 * Delete a file or directory.
 */
fun File.del() {
    if (this.isFile) {
        delete()
        return
    }
    this.listFiles()?.forEach { it.del() }
    delete()
}

