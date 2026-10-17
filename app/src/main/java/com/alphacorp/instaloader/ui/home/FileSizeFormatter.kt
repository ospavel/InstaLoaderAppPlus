package com.alphacorp.instaloader.ui.home

import java.util.Locale

internal object FileSizeFormatter {

    private val units = listOf("KB", "MB", "GB", "TB")

    fun format(byteCount: Long): String {
        if (byteCount < 1024L) return "$byteCount B"

        var value = byteCount.toDouble()
        var unitIndex = -1
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return "%.1f %s".format(Locale.getDefault(), value, units[unitIndex])
    }
}
