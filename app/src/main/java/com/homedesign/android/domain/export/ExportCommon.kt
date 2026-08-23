package com.homedesign.android.domain.export

import com.homedesign.android.domain.model.Home

data class ExportFile(val filename: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportFile) return false
        return filename == other.filename && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * filename.hashCode() + bytes.contentHashCode()
}

fun sanitizeExportFilename(name: String?): String {
    val base = (name ?: "plan").trim().ifEmpty { "plan" }
    return base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
}

data class PlanBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

fun computePlanBounds(home: Home, levelId: String?): PlanBounds? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var any = false
    fun grow(x: Double, y: Double) {
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
        any = true
    }
    for (w in home.walls) {
        if (levelId != null && w.level != levelId) continue
        grow(w.startX, w.startY)
        grow(w.endX, w.endY)
    }
    for (r in home.rooms) {
        if (levelId != null && r.level != levelId) continue
        for (p in r.points) grow(p.x, p.y)
    }
    if (!any) return null
    return PlanBounds(minX, minY, maxX, maxY)
}
