package com.homedesign.android.core.ui

import java.text.DateFormat
import java.util.Date

/** Web `chrome/relativeTime.ts` parity for epoch millis. */
fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMs <= 0L) return ""
    val delta = (now - epochMs).coerceAtLeast(0L)
    val minutes = (delta / 60_000L).toInt()
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes min ago"
    val hours = minutes / 60
    if (hours < 24) return "$hours h ago"
    val days = hours / 24
    if (days < 7) return "$days d ago"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
}
