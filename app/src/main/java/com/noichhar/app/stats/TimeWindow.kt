package com.noichhar.app.stats

enum class TimeWindow(val hours: Int, val label: String) {
    HOUR_1(1, "1 hr"), HOUR_24(24, "24 hr"), DAY_7(168, "7 day");
    fun sinceMillis(now: Long = System.currentTimeMillis()): Long = now - hours * 3_600_000L
}
