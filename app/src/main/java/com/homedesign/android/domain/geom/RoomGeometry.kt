package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import kotlin.math.abs

object RoomGeometry {
    /** Shoelace area in cm². Degenerate rooms (< 3 points) report 0. */
    fun polygonArea(room: Room): Double = polygonAreaPoints(room.points)

    /** Area-weighted centroid. Falls back to the vertex mean for near-zero area. */
    fun centroid(room: Room): Vec2 = polygonCentroid(room.points)
}

fun polygonAreaPoints(pts: List<Point>): Double {
    if (pts.size < 3) return 0.0
    var sum = 0.0
    for (i in pts.indices) {
        val j = (i + 1) % pts.size
        sum += pts[i].x * pts[j].y - pts[j].x * pts[i].y
    }
    return abs(sum) / 2.0
}

fun polygonCentroid(pts: List<Point>): Vec2 {
    if (pts.isEmpty()) return vec(0.0, 0.0)
    var twiceArea = 0.0
    var wx = 0.0
    var wy = 0.0
    for (i in pts.indices) {
        val a = pts[i]
        val b = pts[(i + 1) % pts.size]
        val cross = a.x * b.y - b.x * a.y
        twiceArea += cross
        wx += (a.x + b.x) * cross
        wy += (a.y + b.y) * cross
    }
    if (abs(twiceArea) < 1e-9) {
        var sx = 0.0
        var sy = 0.0
        for (p in pts) {
            sx += p.x
            sy += p.y
        }
        return vec(sx / pts.size, sy / pts.size)
    }
    return vec(wx / (3.0 * twiceArea), wy / (3.0 * twiceArea))
}
