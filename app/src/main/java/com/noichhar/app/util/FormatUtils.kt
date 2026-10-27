package com.noichhar.app.util

import java.util.Locale

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var number = bytes.toDouble(); var unit = 0
        while (number >= 1024 && unit < units.lastIndex) { number /= 1024; unit++ }
        return if (unit == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", number, units[unit])
    }
    fun formatRate(bytesPerSecond: Long?): String = bytesPerSecond?.let { "${formatBytes(it)}/s" } ?: "—"
    fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", n / 1_000.0)
        else -> n.toString()
    }
    fun formatRelativeTime(epochMs: Long): String {
        val diff = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} hr ago"
            else -> "${diff / 86_400_000} d ago"
        }
    }
}
