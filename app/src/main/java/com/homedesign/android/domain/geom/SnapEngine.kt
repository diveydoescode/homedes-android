package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

sealed interface SnapTarget {
    data object None : SnapTarget
    data object Grid : SnapTarget
    data class WallEndpoint(val wallID: String, val atStart: Boolean) : SnapTarget
    data class WallMidpoint(val wallID: String) : SnapTarget
    /** Historical name; geometry is the nearer long FACE, not the centreline. */
    data class WallCentreLine(val wallID: String) : SnapTarget
}

data class SnapResult(
    val snappedPoint: Vec2,
    val target: SnapTarget,
    val distanceCM: Double,
)

enum class AlignmentAxis { Horizontal, Vertical }

data class AlignmentGuide(
    val axis: AlignmentAxis,
    val anchor: Vec2,
)

data class AlignmentGuidesResult(
    val point: Vec2,
    val guides: List<AlignmentGuide>,
)

/** Point on segment `a`→`b` nearest to `p`. `t` clamped to [0, 1]. */
fun nearestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
    val ab = sub(b, a)
    val lenSq = lengthSq(ab)
    if (lenSq <= 1e-12) return a
    val t = dot(sub(p, a), ab) / lenSq
    val clamped = max(0.0, min(1.0, t))
    return add(a, scale(ab, clamped))
}

object SnapEngine {
    fun nearestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 =
        com.homedesign.android.domain.geom.nearestPointOnSegment(p, a, b)

    fun snap(
        point: Vec2,
        walls: List<Wall>,
        snapRadiusCM: Double,
        gridSpacingCM: Double = snapGridDefaultCM,
    ): SnapResult {
        if (snapRadiusCM <= 0) return SnapResult(point, SnapTarget.None, 0.0)

        var bestEndpoint: EndpointCand? = null
        for (wall in walls) {
            val startPoint = vec(wall.startX, wall.startY)
            val startDist = dist(point, startPoint)
            if (startDist < snapRadiusCM && (bestEndpoint == null || startDist < bestEndpoint.dist)) {
                bestEndpoint = EndpointCand(startPoint, wall.id, true, startDist)
            }
            val endPoint = vec(wall.endX, wall.endY)
            val endDist = dist(point, endPoint)
            if (endDist < snapRadiusCM && (bestEndpoint == null || endDist < bestEndpoint.dist)) {
                bestEndpoint = EndpointCand(endPoint, wall.id, false, endDist)
            }
        }
        bestEndpoint?.let {
            return SnapResult(
                it.point,
                SnapTarget.WallEndpoint(it.wallID, it.atStart),
                it.dist,
            )
        }

        var bestMid: MidCand? = null
        for (wall in walls) {
            val mid = vec((wall.startX + wall.endX) / 2.0, (wall.startY + wall.endY) / 2.0)
            val d = dist(point, mid)
            if (d < snapRadiusCM && (bestMid == null || d < bestMid.dist)) {
                bestMid = MidCand(mid, wall.id, d)
            }
        }
        bestMid?.let {
            return SnapResult(it.point, SnapTarget.WallMidpoint(it.wallID), it.dist)
        }

        var bestLine: MidCand? = null
        for (wall in walls) {
            val a = vec(wall.startX, wall.startY)
            val b = vec(wall.endX, wall.endY)
            val ab = sub(b, a)
            val len = hypot(ab.x, ab.y)
            if (len <= 1e-9) continue
            val halfT = wall.thickness / 2.0
            val off = vec((-ab.y / len) * halfT, (ab.x / len) * halfT)
            val faces = listOf(
                add(a, off) to add(b, off),
                sub(a, off) to sub(b, off),
            )
            for ((la, lb) in faces) {
                val proj = nearestPointOnSegment(point, la, lb)
                val d = dist(point, proj)
                if (d < snapRadiusCM && (bestLine == null || d < bestLine.dist)) {
                    bestLine = MidCand(proj, wall.id, d)
                }
            }
        }
        bestLine?.let {
            return SnapResult(it.point, SnapTarget.WallCentreLine(it.wallID), it.dist)
        }

        val gridPoint = nearestGridIntersection(point, gridSpacingCM)
        val gridDist = dist(point, gridPoint)
        if (gridDist < snapRadiusCM) {
            return SnapResult(gridPoint, SnapTarget.Grid, gridDist)
        }
        return SnapResult(point, SnapTarget.None, 0.0)
    }

    fun nearFaceButtPoint(from: Vec2, approx: Vec2, wall: Wall): Vec2 {
        val a0 = vec(wall.startX, wall.startY)
        val a1 = vec(wall.endX, wall.endY)
        val abx = a1.x - a0.x
        val aby = a1.y - a0.y
        val len = hypot(abx, aby)
        if (len <= 1e-9) return approx
        val px = -aby / len
        val py = abx / len
        val sideDot = (from.x - a0.x) * px + (from.y - a0.y) * py
        val sign = if (sideDot >= 0) 1.0 else -1.0
        val ox = px * (wall.thickness / 2.0) * sign
        val oy = py * (wall.thickness / 2.0) * sign
        return nearestPointOnSegment(
            approx,
            vec(a0.x + ox, a0.y + oy),
            vec(a1.x + ox, a1.y + oy),
        )
    }

    fun alignmentGuides(
        point: Vec2,
        walls: List<Wall>,
        toleranceCM: Double,
    ): AlignmentGuidesResult {
        if (toleranceCM <= 0) return AlignmentGuidesResult(point, emptyList())
        var bestX: AxisCandX? = null
        var bestY: AxisCandY? = null
        for (wall in walls) {
            val mid = vec((wall.startX + wall.endX) / 2.0, (wall.startY + wall.endY) / 2.0)
            val anchors = listOf(
                vec(wall.startX, wall.startY),
                vec(wall.endX, wall.endY),
                mid,
            )
            for (ep in anchors) {
                val dx = abs(point.x - ep.x)
                if (dx < toleranceCM && (bestX == null || dx < bestX.d)) {
                    bestX = AxisCandX(ep.x, ep, dx)
                }
                val dy = abs(point.y - ep.y)
                if (dy < toleranceCM && (bestY == null || dy < bestY.d)) {
                    bestY = AxisCandY(ep.y, ep, dy)
                }
            }
        }
        var snapped = point
        val guides = mutableListOf<AlignmentGuide>()
        bestX?.let {
            snapped = vec(it.x, snapped.y)
            guides.add(AlignmentGuide(AlignmentAxis.Vertical, it.anchor))
        }
        bestY?.let {
            snapped = vec(snapped.x, it.y)
            guides.add(AlignmentGuide(AlignmentAxis.Horizontal, it.anchor))
        }
        return AlignmentGuidesResult(snapped, guides)
    }

    private fun nearestGridIntersection(point: Vec2, spacing: Double): Vec2 =
        vec(
            floor((point.x + spacing / 2.0) / spacing) * spacing,
            floor((point.y + spacing / 2.0) / spacing) * spacing,
        )

    private data class EndpointCand(val point: Vec2, val wallID: String, val atStart: Boolean, val dist: Double)
    private data class MidCand(val point: Vec2, val wallID: String, val dist: Double)
    private data class AxisCandX(val x: Double, val anchor: Vec2, val d: Double)
    private data class AxisCandY(val y: Double, val anchor: Vec2, val d: Double)
}
