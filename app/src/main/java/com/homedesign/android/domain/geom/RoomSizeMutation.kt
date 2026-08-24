package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RoomSizeResult(
    val rooms: List<Room>,
    val walls: List<Wall>,
    val openings: List<HomeDoorOrWindow>,
)

data class RoomBoundingSize(val width: Double, val depth: Double)

/**
 * Axis-aligned bounding size. The far half-space (x ≥ maxX, y ≥ maxY)
 * translates so the building grows outward. Openings in that half-space
 * move with it; openings on a stretched wall keep their absolute
 * position (Swift oracle — not a parametric-t scale).
 */
object RoomSizeMutation {
    fun boundingSize(room: Room): RoomBoundingSize {
        if (room.points.isEmpty()) return RoomBoundingSize(0.0, 0.0)
        var minX = room.points[0].x
        var maxX = minX
        var minY = room.points[0].y
        var maxY = minY
        for (p in room.points) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        return RoomBoundingSize(maxX - minX, maxY - minY)
    }

    fun resize(
        roomID: String,
        targetWidth: Double?,
        targetDepth: Double?,
        rooms: List<Room>,
        walls: List<Wall>,
        openings: List<HomeDoorOrWindow>,
        epsilonCM: Double = 2.0,
    ): RoomSizeResult {
        val room = rooms.find { it.id == roomID }
        if (room == null || room.points.size < 3) {
            return RoomSizeResult(rooms, walls, openings)
        }
        var minX = room.points[0].x
        var maxX = minX
        var minY = room.points[0].y
        var maxY = minY
        for (p in room.points) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        val width = maxX - minX
        val depth = maxY - minY
        if (width <= 1 || depth <= 1) {
            return RoomSizeResult(rooms, walls, openings)
        }

        fun delta(target: Double?, current: Double): Double {
            if (target == null) return 0.0
            if (target < minRoomResizeCM || target > maxRoomResizeCM) return 0.0
            return target - current
        }
        val dW = delta(targetWidth, width)
        val dH = delta(targetDepth, depth)
        if (abs(dW) <= 0.01 && abs(dH) <= 0.01) {
            return RoomSizeResult(rooms, walls, openings)
        }

        fun shifted(x: Double, y: Double): Point {
            var nx = x
            var ny = y
            if (dW != 0.0 && x >= maxX - epsilonCM) nx += dW
            if (dH != 0.0 && y >= maxY - epsilonCM) ny += dH
            return Point(nx, ny)
        }

        val newWalls = walls.map { w ->
            val s = shifted(w.startX, w.startY)
            val e = shifted(w.endX, w.endY)
            w.copy(startX = s.x, startY = s.y, endX = e.x, endY = e.y)
        }
        val newOpenings = openings.map { dw ->
            val c = shifted(dw.piece.x, dw.piece.y)
            dw.copy(piece = dw.piece.copy(x = c.x, y = c.y))
        }
        val newRooms = rooms.map { r ->
            r.copy(points = r.points.map { p -> shifted(p.x, p.y) })
        }
        return RoomSizeResult(newRooms, newWalls, newOpenings)
    }
}
