package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall

enum class DimensionFaceMode { Inner, Outer }

/** Left-hand unit normal of start→end in Y-down. Null if degenerate. */
fun wallLeftNormal(dx: Double, dy: Double): Vec2? {
    val len = kotlin.math.hypot(dx, dy)
    if (len < 1e-9) return null
    return vec(dy / len, -dx / len)
}

private fun offsetLine(start: Vec2, end: Vec2, amount: Double): Pair<Vec2, Vec2>? {
    val n = wallLeftNormal(end.x - start.x, end.y - start.y) ?: return null
    return add(start, scale(n, amount)) to add(end, scale(n, amount))
}

/** Intersection of infinite lines a→b and c→d. Null if parallel. */
fun lineLineIntersect(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Vec2? {
    val r = sub(b, a)
    val s = sub(d, c)
    val den = r.x * s.y - r.y * s.x
    if (kotlin.math.abs(den) < 1e-12) return null
    val t = ((c.x - a.x) * s.y - (c.y - a.y) * s.x) / den
    return add(a, scale(r, t))
}

/**
 * One mitered face-corner per outline edge, in edge order.
 * Outer = outside of the envelope; Inner = inside. Closed loop.
 */
fun envelopeFaceCorners(
    edges: List<RoomDetection.ExteriorEdge>,
    wallByID: Map<String, Wall>,
    mode: DimensionFaceMode,
): List<Vec2> {
    val sign = if (mode == DimensionFaceMode.Outer) 1.0 else -1.0
    val lines = mutableListOf<Pair<Vec2, Vec2>>()
    for (edge in edges) {
        val wall = wallByID[edge.wallID]
        val half = (wall?.thickness ?: 0.0) / 2.0
        val off = offsetLine(edge.start, edge.end, sign * half) ?: return emptyList()
        lines.add(off)
    }
    if (lines.size < 2) return emptyList()
    val corners = mutableListOf<Vec2>()
    for (i in lines.indices) {
        val prev = lines[(i - 1 + lines.size) % lines.size]
        val cur = lines[i]
        corners.add(lineLineIntersect(prev.first, prev.second, cur.first, cur.second) ?: cur.first)
    }
    return corners
}

/**
 * +1 if this wall's own LEFT face is the envelope outside; −1 if the
 * RIGHT face is outside; 0 if the wall is not on the envelope.
 */
fun envelopeOuterLeftSign(
    wall: Wall,
    edges: List<RoomDetection.ExteriorEdge>,
): Int {
    val edge = edges.find { it.wallID == wall.id } ?: return 0
    val wx = wall.endX - wall.startX
    val wy = wall.endY - wall.startY
    val aligned = wx * (edge.end.x - edge.start.x) + wy * (edge.end.y - edge.start.y)
    return if (aligned >= 0) 1 else -1
}

private fun chosenLeft(
    wall: Wall,
    cursor: Vec2,
    edges: List<RoomDetection.ExteriorEdge>,
    mode: DimensionFaceMode,
): Boolean {
    val left = wallLeftNormal(wall.endX - wall.startX, wall.endY - wall.startY) ?: return true
    val env = envelopeOuterLeftSign(wall, edges)
    if (env == 0) {
        val proj = nearestPointOnSegment(
            cursor,
            vec(wall.startX, wall.startY),
            vec(wall.endX, wall.endY),
        )
        return dot(sub(cursor, proj), left) >= 0
    }
    return if (mode == DimensionFaceMode.Outer) env == 1 else env == -1
}

private fun offsetToFace(wall: Wall, centre: Vec2, towardLeft: Boolean): Vec2 {
    val left = wallLeftNormal(wall.endX - wall.startX, wall.endY - wall.startY) ?: return centre
    val n = if (towardLeft) left else scale(left, -1.0)
    return add(centre, scale(n, wall.thickness / 2.0))
}

/**
 * Resolve a SnapEngine hit onto the mode's wall face. Grid / free
 * points are returned unchanged.
 */
fun resolveDimensionSnap(
    snap: SnapResult,
    cursor: Vec2,
    walls: List<Wall>,
    mode: DimensionFaceMode,
): Vec2 {
    val target = snap.target
    if (target is SnapTarget.None || target is SnapTarget.Grid) return snap.snappedPoint
    val wallID = when (target) {
        is SnapTarget.WallEndpoint -> target.wallID
        is SnapTarget.WallMidpoint -> target.wallID
        is SnapTarget.WallCentreLine -> target.wallID
        else -> return snap.snappedPoint
    }
    val wall = walls.find { it.id == wallID } ?: return snap.snappedPoint
    val edges = RoomDetection.exteriorWallEdges(walls)
    val towardLeft = chosenLeft(wall, cursor, edges, mode)

    when (target) {
        is SnapTarget.WallEndpoint -> {
            val centre = if (target.atStart) {
                vec(wall.startX, wall.startY)
            } else {
                vec(wall.endX, wall.endY)
            }
            return offsetToFace(wall, centre, towardLeft)
        }
        is SnapTarget.WallMidpoint -> {
            val centre = vec((wall.startX + wall.endX) / 2.0, (wall.startY + wall.endY) / 2.0)
            return offsetToFace(wall, centre, towardLeft)
        }
        is SnapTarget.WallCentreLine -> {
            val a = vec(wall.startX, wall.startY)
            val b = vec(wall.endX, wall.endY)
            val proj = nearestPointOnSegment(snap.snappedPoint, a, b)
            val left = wallLeftNormal(b.x - a.x, b.y - a.y) ?: return snap.snappedPoint
            val onLeft = dot(sub(snap.snappedPoint, proj), left) >= 0
            if (onLeft == towardLeft) return snap.snappedPoint
            return offsetToFace(wall, proj, towardLeft)
        }
        else -> return snap.snappedPoint
    }
}

fun dimensionFaceOffsetCM(mode: DimensionFaceMode, spacingCM: Double): Double =
    if (mode == DimensionFaceMode.Outer) spacingCM else -spacingCM
