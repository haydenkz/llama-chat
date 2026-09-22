package com.llamacpp.mobile.ui.util

import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) "$bytes ${units[index]}"
    else String.format(Locale.US, "%.2f %s", value, units[index])
}

fun formatParams(count: Long?): String {
    if (count == null || count <= 0L) return "—"
    return when {
        count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", count / 1e9)
        count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1e6)
        count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1e3)
        else -> count.toString()
    }
}

fun formatTokens(count: Long?): String {
    if (count == null) return "—"
    return String.format(Locale.US, "%,d", count)
}

fun formatSpeed(value: Double?): String =
    if (value == null || value <= 0.0) "—" else String.format(Locale.US, "%.2f t/s", value)

fun formatDuration(ms: Double?): String {
    if (ms == null || ms <= 0.0) return "—"
    val seconds = ms / 1000.0
    return if (seconds < 60.0) {
        String.format(Locale.US, "%.1fs", seconds)
    } else {
        String.format(Locale.US, "%.0fs", seconds)
    }
}

fun formatFloat(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
