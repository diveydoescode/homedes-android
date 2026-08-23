package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

enum class WallSide { Left, Right }
enum class WallVertex { Start, End }

/**
 * Plan is Y-DOWN. Left-hand perpendicular travelling start→end is
 * (sin θ, −cos θ). Never flip that sign.
 */
object WallGeometry {
    val slopeVertical: Double = mitreVerticalSlope

    /**
     * 4 corner points, no neighbour mitering.
     * [0] left start  [1] left end
     * [3] right start [2] right end
     */
    fun unjoinedOutline(wall: Wall): List<Vec2> {
        val dx = wall.endX - wall.startX
        val dy = wall.endY - wall.startY
        val angle = atan2(dy, dx)
        val sinA = sin(angle)
        val cosA = cos(angle)
        val t = wall.thickness / 2.0
        val ldx = sinA * t
        val ldy = cosA * t
        return listOf(
            vec(wall.startX + ldx, wall.startY - ldy),
            vec(wall.endX + ldx, wall.endY - ldy),
            vec(wall.endX - ldx, wall.endY + ldy),
            vec(wall.startX - ldx, wall.startY + ldy),
        )
    }

    /** Four-case mitre. Result is still 4 points, same winding. */
    fun miteredPoints(wall: Wall, wallsByID: Map<String, Wall>): List<Vec2> {
        val pts = unjoinedOutline(wall).toMutableList()
        val lsi = 0
        val rsi = 3
        val lei = 1
        val rei = 2

        wall.atStart?.let { nid ->
            val neighbor = wallsByID[nid] ?: return@let
            val n = unjoinedOutline(neighbor)
            val nLSI = 0
            val nRSI = 3
            val nLEI = 1
            val nREI = 2
            val joinedAtEnd =
                neighbor.atEnd == wall.id &&
                    (neighbor.atStart != wall.id ||
                        (neighbor.endX == wall.startX && neighbor.endY == wall.startY))
            val joinedAtStart =
                neighbor.atStart == wall.id &&
                    (neighbor.atEnd != wall.id ||
                        (neighbor.startX == wall.startX && neighbor.startY == wall.startY))
            val limit = 2.0 * max(wall.thickness, neighbor.thickness)
            when {
                joinedAtEnd -> {
                    pts[lsi] = computeIntersection(pts[lsi], pts[lsi + 1], n[nLEI], n[nLEI - 1], limit)
                    pts[rsi] = computeIntersection(pts[rsi], pts[rsi - 1], n[nREI], n[nREI + 1], limit)
                }
                joinedAtStart -> {
                    pts[lsi] = computeIntersection(pts[lsi], pts[lsi + 1], n[nRSI], n[nRSI - 1], limit)
                    pts[rsi] = computeIntersection(pts[rsi], pts[rsi - 1], n[nLSI], n[nLSI + 1], limit)
                }
            }
        }

        wall.atEnd?.let { nid ->
            val neighbor = wallsByID[nid] ?: return@let
            val n = unjoinedOutline(neighbor)
            val nLSI = 0
            val nRSI = 3
            val nLEI = 1
            val nREI = 2
            val joinedAtStart =
                neighbor.atStart == wall.id &&
                    (neighbor.atEnd != wall.id ||
                        (neighbor.startX == wall.endX && neighbor.startY == wall.endY))
            val joinedAtEnd =
                neighbor.atEnd == wall.id &&
                    (neighbor.atStart != wall.id ||
                        (neighbor.endX == wall.endX && neighbor.endY == wall.endY))
            val limit = 2.0 * max(wall.thickness, neighbor.thickness)
            when {
                joinedAtStart -> {
                    pts[lei] = computeIntersection(pts[lei], pts[lei - 1], n[nLSI], n[nLSI + 1], limit)
                    pts[rei] = computeIntersection(pts[rei], pts[rei + 1], n[nRSI], n[nRSI - 1], limit)
                }
                joinedAtEnd -> {
                    pts[lei] = computeIntersection(pts[lei], pts[lei - 1], n[nREI], n[nREI + 1], limit)
                    pts[rei] = computeIntersection(pts[rei], pts[rei + 1], n[nLEI], n[nLEI - 1], limit)
                }
            }
        }

        return pts
    }

    /** Interior (room-facing) side of [wall] at the corner shared with [neighbour]. */
    fun interiorSide(wall: Wall, vertex: WallVertex, neighbour: Wall): WallSide? {
        val v = if (vertex == WallVertex.Start) {
            vec(wall.startX, wall.startY)
        } else {
            vec(wall.endX, wall.endY)
        }
        val angle = atan2(wall.endY - wall.startY, wall.endX - wall.startX)
        val rightNormal = vec(-sin(angle), cos(angle))
        val nStart = vec(neighbour.startX, neighbour.startY)
        val nEnd = vec(neighbour.endX, neighbour.endY)
        val nFar =
            if (hypot(nStart.x - v.x, nStart.y - v.y) <= hypot(nEnd.x - v.x, nEnd.y - v.y)) {
                nEnd
            } else {
                nStart
            }
        val rel = vec(nFar.x - v.x, nFar.y - v.y)
        val proj = rel.x * rightNormal.x + rel.y * rightNormal.y
        return when {
            proj > 0 -> WallSide.Right
            proj < 0 -> WallSide.Left
            else -> null
        }
    }
}

private fun slope(a: Vec2, b: Vec2): Double {
    val dx = b.x - a.x
    if (dx == 0.0) return Double.POSITIVE_INFINITY
    return (b.y - a.y) / dx
}

/** Slope-form intersection. Vertical sentinel 4000. Returns [a1] if parallel / too far. */
fun computeIntersection(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2, limit: Double): Vec2 {
    val alpha1 = slope(a1, a2)
    val alpha2 = slope(b1, b2)
    if (alpha1 == alpha2) return a1

    var x = a1.x
    var y = a1.y

    when {
        abs(alpha1) > mitreVerticalSlope -> {
            if (abs(alpha2) < mitreVerticalSlope) {
                x = a1.x
                val beta2 = b2.y - alpha2 * b2.x
                y = alpha2 * x + beta2
            }
        }
        abs(alpha2) > mitreVerticalSlope -> {
            if (abs(alpha1) < mitreVerticalSlope) {
                x = b1.x
                val beta1 = a2.y - alpha1 * a2.x
                y = alpha1 * x + beta1
            }
        }
        else -> {
            val sameSign = (alpha1 >= 0) == (alpha2 >= 0)
            val a = abs(alpha1)
            val b = abs(alpha2)
            val ratio = when {
                a > b -> a / b
                b > 0 -> b / a
                else -> Double.POSITIVE_INFINITY
            }
            if (abs(alpha1 - alpha2) > 1e-5 && (!sameSign || ratio > mitreNearParallelRatio)) {
                val beta1 = a2.y - alpha1 * a2.x
                val beta2 = b2.y - alpha2 * b2.x
                x = (beta2 - beta1) / (alpha1 - alpha2)
                y = alpha1 * x + beta1
            }
        }
    }

    val dx = x - a1.x
    val dy = y - a1.y
    return if (dx * dx + dy * dy < limit * limit) vec(x, y) else a1
}
