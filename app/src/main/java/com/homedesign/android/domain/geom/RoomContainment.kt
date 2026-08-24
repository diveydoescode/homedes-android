package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Room
import kotlin.math.sqrt

/**
 * Port of web `geom/RoomContainment.ts`.
 * Point-in-polygon / piece-in-room; [clampFurnitureToRoom] nudges a piece centre
 * so footprint corners sit inside the smallest containing room, inset by margin.
 */
object RoomContainment {
    val defaultMarginCM: Double = roomContainmentMarginCM

    fun pointInRoom(room: Room, point: Vec2): Boolean =
        pointInPolygon(room.points.map { vec(it.x, it.y) }, point)

    /**
     * Piece is in the room if its centre is inside, or a majority
     * of the four footprint corners are inside (doc 08 §7).
     */
    fun pieceInRoom(piece: HomePieceOfFurniture, room: Room): Boolean {
        val poly = room.points.map { vec(it.x, it.y) }
        if (pointInPolygon(poly, vec(piece.x, piece.y))) return true
        var inside = 0
        for (c in FurnitureGeometry.cornerPoints(piece)) {
            if (pointInPolygon(poly, c)) inside += 1
        }
        return inside >= 3
    }

    fun clampFurnitureToRoom(
        piece: HomePieceOfFurniture,
        rooms: List<Room>,
        marginCM: Double = roomContainmentMarginCM,
    ): Vec2 {
        val centre = vec(piece.x, piece.y)
        val containing = rooms.filter { r ->
            pointInPolygon(r.points.map { vec(it.x, it.y) }, centre)
        }
        if (containing.isEmpty()) return centre
        var room = containing[0]
        var bestArea = polygonAreaPoints(room.points)
        for (i in 1 until containing.size) {
            val area = polygonAreaPoints(containing[i].points)
            if (area < bestArea) {
                bestArea = area
                room = containing[i]
            }
        }
        val poly = room.points.map { vec(it.x, it.y) }
        if (poly.size < 3) return centre

        var offset = vec(0.0, 0.0)
        for (pass in 0 until roomContainmentPasses) {
            val corners = FurnitureGeometry.cornerPoints(piece).map { add(it, offset) }
            var worstPush = vec(0.0, 0.0)
            var worstMag = 0.0
            for (corner in corners) {
                val push = inwardPush(corner, poly, marginCM)
                val mag = length(push)
                if (mag > worstMag) {
                    worstMag = mag
                    worstPush = push
                }
            }
            if (worstMag < 1e-6) break
            offset = add(offset, worstPush)
        }
        return add(centre, offset)
    }
}

private fun inwardPush(corner: Vec2, polygon: List<Vec2>, marginCM: Double): Vec2 {
    val (closest, distance) = closestBoundary(corner, polygon)
    val inside = pointInPolygon(polygon, corner)
    if (inside) {
        if (distance >= marginCM) return vec(0.0, 0.0)
        val outward = sub(closest, corner)
        val len = length(outward)
        if (len <= 1e-9) return vec(0.0, 0.0)
        val inwardDir = scale(outward, -1.0 / len)
        return scale(inwardDir, marginCM - distance)
    }
    val toBoundary = sub(closest, corner)
    val len = length(toBoundary)
    if (len <= 1e-9) return vec(0.0, 0.0)
    val inwardDir = scale(toBoundary, 1.0 / len)
    return add(toBoundary, scale(inwardDir, marginCM))
}

private fun closestBoundary(p: Vec2, polygon: List<Vec2>): Pair<Vec2, Double> {
    var bestPoint = polygon[0]
    var bestDistSq = Double.POSITIVE_INFINITY
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        val proj = closestPointOnSegment(p, a, b)
        val d = distSq(p, proj)
        if (d < bestDistSq) {
            bestDistSq = d
            bestPoint = proj
        }
    }
    return bestPoint to sqrt(bestDistSq)
}

private fun closestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
    val ab = sub(b, a)
    val lenSq = dot(ab, ab)
    if (lenSq <= 1e-12) return a
    val t = (dot(sub(p, a), ab) / lenSq).coerceIn(0.0, 1.0)
    return add(a, scale(ab, t))
}
