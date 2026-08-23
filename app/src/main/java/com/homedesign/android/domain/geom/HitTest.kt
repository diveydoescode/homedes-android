package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.PlanLabel
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class ClosestWallHit(val wallID: String, val distanceCM: Double)
data class ClosestEndpointHit(val wallID: String, val atStart: Boolean, val distanceCM: Double)
data class CoincidentEndpoint(val wallID: String, val atStart: Boolean)
data class IdDistance(val id: String, val distanceCM: Double)
data class OpeningHandleHit(val id: String, val side: String, val distanceCM: Double)

private fun pointSegmentDistance(point: Vec2, segStart: Vec2, segEnd: Vec2): Double {
    val segVec = sub(segEnd, segStart)
    val lenSq = lengthSq(segVec)
    if (lenSq < 1e-12) return dist(point, segStart)
    val t = dot(sub(point, segStart), segVec) / lenSq
    val clampedT = max(0.0, min(1.0, t))
    val projection = add(segStart, scale(segVec, clampedT))
    return dist(point, projection)
}

/** Even-odd ray cast. */
fun pointInPolygon(polygon: List<Vec2>, p: Vec2): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > p.y) != (b.y > p.y)) {
            if (p.x < ((b.x - a.x) * (p.y - a.y)) / (b.y - a.y) + a.x) {
                inside = !inside
            }
        }
        j = i
    }
    return inside
}

fun pointInPolygonPoints(polygon: List<Point>, p: Vec2): Boolean =
    pointInPolygon(polygon.map { vec(it.x, it.y) }, p)

object HitTest {
    fun closestWall(point: Vec2, walls: List<Wall>, maxDistanceCM: Double): ClosestWallHit? {
        if (maxDistanceCM <= 0) return null
        var best: ClosestWallHit? = null
        for (wall in walls) {
            val centre = if (ArcWallGeometry.isCurved(wall)) {
                ArcWallGeometry.distanceToArc(point, wall)
            } else {
                pointSegmentDistance(point, vec(wall.startX, wall.startY), vec(wall.endX, wall.endY))
            }
            val d = max(0.0, centre - wall.thickness / 2.0)
            if (d >= maxDistanceCM) continue
            if (best == null || d < best.distanceCM) {
                best = ClosestWallHit(wall.id, d)
            }
        }
        return best
    }

    fun closestEndpoint(point: Vec2, walls: List<Wall>, maxDistanceCM: Double): ClosestEndpointHit? {
        if (maxDistanceCM <= 0) return null
        var best: ClosestEndpointHit? = null
        for (wall in walls) {
            val startDist = dist(point, vec(wall.startX, wall.startY))
            if (startDist < maxDistanceCM && (best == null || startDist < best.distanceCM)) {
                best = ClosestEndpointHit(wall.id, true, startDist)
            }
            val endDist = dist(point, vec(wall.endX, wall.endY))
            if (endDist < maxDistanceCM && (best == null || endDist < best.distanceCM)) {
                best = ClosestEndpointHit(wall.id, false, endDist)
            }
        }
        return best
    }

    fun coincidentEndpoints(
        wallID: String,
        atStart: Boolean,
        walls: List<Wall>,
        epsilonCM: Double = 1.0,
    ): List<CoincidentEndpoint> {
        val reference = walls.find { it.id == wallID } ?: return emptyList()
        val refPoint = if (atStart) {
            vec(reference.startX, reference.startY)
        } else {
            vec(reference.endX, reference.endY)
        }
        val results = mutableListOf<CoincidentEndpoint>()
        for (wall in walls) {
            if (dist(refPoint, vec(wall.startX, wall.startY)) < epsilonCM) {
                if (!(wall.id == wallID && atStart)) {
                    results.add(CoincidentEndpoint(wall.id, true))
                }
            }
            if (dist(refPoint, vec(wall.endX, wall.endY)) < epsilonCM) {
                if (!(wall.id == wallID && !atStart)) {
                    results.add(CoincidentEndpoint(wall.id, false))
                }
            }
        }
        return results
    }

    fun roomContaining(point: Vec2, rooms: List<Room>): Room? {
        var hit: Room? = null
        for (room in rooms) {
            if (pointInPolygonPoints(room.points, point)) hit = room
        }
        return hit
    }

    fun furnitureContains(piece: HomePieceOfFurniture, point: Vec2): Boolean =
        pointInPolygon(FurnitureGeometry.cornerPoints(piece), point)

    fun furnitureDistance(piece: HomePieceOfFurniture, point: Vec2, haloCM: Double = 0.0): Double {
        val local = toPieceLocal(piece, point)
        val hw = (piece.widthInPlan ?: piece.width) / 2.0 + haloCM
        val hd = (piece.depthInPlan ?: piece.depth) / 2.0 + haloCM
        val dx = max(abs(local.x) - hw, 0.0)
        val dy = max(abs(local.y) - hd, 0.0)
        return hypot(dx, dy)
    }

    fun closestFurniture(
        point: Vec2,
        pieces: List<HomePieceOfFurniture>,
        haloCM: Double,
    ): IdDistance? {
        var best: IdDistance? = null
        for (piece in pieces) {
            if (!piece.visible) continue
            val d = furnitureDistance(piece, point, haloCM)
            if (d > 1e-9) continue
            if (best == null || d < best.distanceCM) best = IdDistance(piece.id, d)
        }
        return best
    }

    fun closestOpening(
        point: Vec2,
        openings: List<HomeDoorOrWindow>,
        toleranceCM: Double,
    ): IdDistance? {
        var best: IdDistance? = null
        for (opening in openings) {
            val piece = opening.piece
            val hw = piece.width / 2.0
            val c = cos(piece.angle)
            val s = sin(piece.angle)
            val a = vec(piece.x - c * hw, piece.y - s * hw)
            val b = vec(piece.x + c * hw, piece.y + s * hw)
            val centre = pointSegmentDistance(point, a, b)
            val d = max(0.0, centre - max(piece.depth, 8.0) / 2.0)
            if (d >= toleranceCM) continue
            if (best == null || d < best.distanceCM) best = IdDistance(piece.id, d)
        }
        return best
    }

    fun closestOpeningHandle(
        point: Vec2,
        opening: HomeDoorOrWindow,
        toleranceCM: Double,
    ): OpeningHandleHit? {
        val piece = opening.piece
        val hw = piece.width / 2.0
        val c = cos(piece.angle)
        val s = sin(piece.angle)
        val start = vec(piece.x - c * hw, piece.y - s * hw)
        val end = vec(piece.x + c * hw, piece.y + s * hw)
        val dStart = dist(point, start)
        val dEnd = dist(point, end)
        if (dStart <= toleranceCM && dStart <= dEnd) {
            return OpeningHandleHit(piece.id, "start", dStart)
        }
        if (dEnd <= toleranceCM) {
            return OpeningHandleHit(piece.id, "end", dEnd)
        }
        return null
    }

    fun closestDimension(
        point: Vec2,
        dims: List<DimensionLine>,
        toleranceCM: Double,
    ): IdDistance? {
        var best: IdDistance? = null
        for (dim in dims) {
            val dx = dim.xEnd - dim.xStart
            val dy = dim.yEnd - dim.yStart
            val len = hypot(dx, dy)
            if (len < 1e-9) continue
            val nrmX = (dy / len) * dim.offset
            val nrmY = (-dx / len) * dim.offset
            val a = vec(dim.xStart + nrmX, dim.yStart + nrmY)
            val b = vec(dim.xEnd + nrmX, dim.yEnd + nrmY)
            val d = pointSegmentDistance(point, a, b)
            if (d >= toleranceCM) continue
            if (best == null || d < best.distanceCM) best = IdDistance(dim.id, d)
        }
        return best
    }

    fun closestLabel(
        point: Vec2,
        labels: List<PlanLabel>,
        toleranceCM: Double,
    ): IdDistance? {
        var best: IdDistance? = null
        for (label in labels) {
            if (label.pitch != null && abs(label.pitch) >= 0.01) continue
            val d = dist(point, vec(label.x, label.y))
            if (d >= toleranceCM) continue
            if (best == null || d < best.distanceCM) best = IdDistance(label.id, d)
        }
        return best
    }

    private fun toPieceLocal(piece: HomePieceOfFurniture, point: Vec2): Vec2 {
        val dx = point.x - piece.x
        val dy = point.y - piece.y
        val c = cos(piece.angle)
        val s = sin(piece.angle)
        return vec(dx * c + dy * s, -dx * s + dy * c)
    }

    private fun abs(v: Double): Double = kotlin.math.abs(v)
}
