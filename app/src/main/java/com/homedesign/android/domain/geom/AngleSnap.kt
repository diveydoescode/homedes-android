package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object AngleSnap {
    val snapThresholdRad: Double = angleSnapRad
    val defaultWallReferenceCM: Double = angleSnapWallReferenceCM

    /**
     * Snap the end of a wall-being-drawn to the nearest preferred
     * angle. Callers apply this only when SnapEngine did not land
     * on a wall endpoint.
     */
    fun snapWallEnd(
        start: Vec2,
        rawEnd: Vec2,
        nearbyWalls: List<Wall>,
        wallReferenceCM: Double = angleSnapWallReferenceCM,
    ): Vec2 {
        val rawVector = vec(rawEnd.x - start.x, rawEnd.y - start.y)
        val rawLength = hypot(rawVector.x, rawVector.y)
        if (rawLength <= 1e-6) return rawEnd
        val rawAngle = atan2(rawVector.y, rawVector.x)

        val candidates = mutableListOf(
            0.0, PI / 2.0, PI, -PI / 2.0, -PI,
        )
        for (wall in nearbyWalls) {
            val wallVec = vec(wall.endX - wall.startX, wall.endY - wall.startY)
            val wallLen = hypot(wallVec.x, wallVec.y)
            if (wallLen <= 1e-6) continue
            val wallStart = vec(wall.startX, wall.startY)
            val wallEnd = vec(wall.endX, wall.endY)
            if (dist(wallStart, start) > wallReferenceCM && dist(wallEnd, start) > wallReferenceCM) {
                continue
            }
            val wallAngle = atan2(wallVec.y, wallVec.x)
            candidates.add(wallAngle)
            candidates.add(wallAngle + PI / 2.0)
            candidates.add(wallAngle - PI / 2.0)
        }

        var bestAngle = rawAngle
        var bestDist = angleSnapRad
        for (candidate in candidates) {
            val d = angularDistance(rawAngle, candidate)
            if (d < bestDist) {
                bestDist = d
                bestAngle = candidate
            }
        }

        return vec(
            start.x + cos(bestAngle) * rawLength,
            start.y + sin(bestAngle) * rawLength,
        )
    }

    /** Smallest absolute angular distance modulo 2π. Result in [0, π]. */
    private fun angularDistance(a: Double, b: Double): Double {
        var d = (a - b) % (2 * PI)
        if (d > PI) d -= 2 * PI
        if (d < -PI) d += 2 * PI
        return abs(d)
    }
}
