package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Wall
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** FurnitureSnap front at angle `a`. Agrees with FurnitureFacing at 0. */
fun furnitureSnapFront(angle: Double): Vec2 = vec(sin(angle), cos(angle))

private fun halfExtentAlong(piece: HomePieceOfFurniture, axis: Vec2): Double {
    val hw = piece.width / 2.0
    val hd = piece.depth / 2.0
    val ca = cos(piece.angle)
    val sa = sin(piece.angle)
    val local = listOf(
        vec(-hw, -hd),
        vec(hw, -hd),
        vec(hw, hd),
        vec(-hw, hd),
    )
    var maxVal = 0.0
    for (p in local) {
        val wx = p.x * ca - p.y * sa
        val wy = p.x * sa + p.y * ca
        maxVal = max(maxVal, abs(wx * axis.x + wy * axis.y))
    }
    return maxVal
}

private data class WallSnapCand(
    val proj: Vec2,
    val away: Vec2,
    val halfThickness: Double,
    val halfExtent: Double,
    val gap: Double,
)

private fun closestStraightWall(
    centre: Vec2,
    walls: List<Wall>,
    halfAlongAway: (Vec2) -> Double,
    toleranceCM: Double,
): WallSnapCand? {
    var best: WallSnapCand? = null
    for (wall in walls) {
        if (ArcWallGeometry.isCurved(wall)) continue
        val a = vec(wall.startX, wall.startY)
        val b = vec(wall.endX, wall.endY)
        val ab = sub(b, a)
        val len2 = lengthSq(ab)
        if (len2 <= 1e-9) continue
        val t = max(0.0, min(1.0, dot(sub(centre, a), ab) / len2))
        val proj = add(a, scale(ab, t))
        val offset = sub(centre, proj)
        val distVal = length(offset)
        if (distVal <= 1e-9) continue
        val away = scale(offset, 1.0 / distVal)
        val halfExtent = halfAlongAway(away)
        val halfThickness = wall.thickness / 2.0
        val gap = distVal - halfThickness - halfExtent
        if (gap >= toleranceCM) continue
        if (best == null || gap < best.gap) {
            best = WallSnapCand(proj, away, halfThickness, halfExtent, gap)
        }
    }
    return best
}

private fun abs(v: Double): Double = kotlin.math.abs(v)

object FurnitureSnap {
    val defaultToleranceCM: Double = furnitureSnapToWallCM

    fun front(angle: Double): Vec2 = furnitureSnapFront(angle)

    data class SnapPose(val x: Double, val y: Double, val angle: Double)
    data class SnapXY(val x: Double, val y: Double)

    fun snapToWall(
        piece: HomePieceOfFurniture,
        walls: List<Wall>,
        toleranceCM: Double = furnitureSnapToWallCM,
    ): SnapPose? {
        val centre = vec(piece.x, piece.y)
        val halfDepth = (piece.depthInPlan ?: piece.depth) / 2.0
        val best = closestStraightWall(centre, walls, { halfDepth }, toleranceCM) ?: return null
        val newCentre = add(best.proj, scale(best.away, best.halfThickness + halfDepth))
        return SnapPose(
            x = newCentre.x,
            y = newCentre.y,
            angle = atan2(best.away.x, best.away.y),
        )
    }

    fun snapToWallKeepAngle(
        piece: HomePieceOfFurniture,
        walls: List<Wall>,
        toleranceCM: Double = furnitureSnapToWallCM,
    ): SnapXY? {
        val centre = vec(piece.x, piece.y)
        val best = closestStraightWall(
            centre,
            walls,
            { away -> halfExtentAlong(piece, away) },
            toleranceCM,
        ) ?: return null
        val newCentre = add(
            best.proj,
            scale(best.away, best.halfThickness + best.halfExtent),
        )
        return SnapXY(newCentre.x, newCentre.y)
    }
}
