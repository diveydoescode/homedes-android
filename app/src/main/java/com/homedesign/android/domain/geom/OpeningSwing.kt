package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeDoorOrWindow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Row-major 4×4. */
typealias Mat4 = DoubleArray

data class Vec3(val x: Double, val y: Double, val z: Double)

/**
 * Port of web `geom/OpeningSwing.ts`.
 * 3D hinge / tilt math; 2D plan art uses [OpeningSymbol.sashArc] + mirroredX/Y flips.
 */
object OpeningSwing {
    const val doorSwingDeg = 85.0
    const val windowTiltDeg = 14.0

    fun isWindow(opening: HomeDoorOrWindow): Boolean {
        val name = (opening.piece.name ?: "").lowercase()
        return name.contains("window") || name.contains("fenetre") || name.contains("fenêtre")
    }

    fun hingePoint(opening: HomeDoorOrWindow): Vec2? {
        val corners = FurnitureGeometry.cornerPoints(opening.piece)
        if (corners.size != 4) return null
        val left = vec((corners[0].x + corners[3].x) / 2, (corners[0].y + corners[3].y) / 2)
        val right = vec((corners[1].x + corners[2].x) / 2, (corners[1].y + corners[2].y) / 2)
        return if (opening.mirroredX) right else left
    }

    fun signedSwingRadians(opening: HomeDoorOrWindow): Double {
        var degrees = doorSwingDeg
        if (opening.mirroredX) degrees = -degrees
        if (opening.mirroredY) degrees = -degrees
        return (degrees * PI) / 180.0
    }

    fun toggleDelta(opening: HomeDoorOrWindow, isOpening: Boolean): Mat4? =
        partialDelta(opening, isOpening, 1.0)

    fun partialDelta(opening: HomeDoorOrWindow, isOpening: Boolean, fraction: Double): Mat4? {
        if (isWindow(opening)) {
            val lowered = (opening.piece.name ?: "").lowercase()
            if (lowered.contains("double") || lowered.contains("french")) {
                val hinge = hingePoint(opening) ?: return null
                val theta = signedSwingRadians(opening) * 0.62 * (if (isOpening) 1.0 else -1.0)
                return deltaAbout(
                    Vec3(0.0, 1.0, 0.0),
                    theta * fraction,
                    Vec3(hinge.x, 0.0, hinge.y),
                )
            }
            val corners = FurnitureGeometry.cornerPoints(opening.piece)
            if (corners.size != 4) return null
            val spanX = corners[1].x - corners[0].x
            val spanY = corners[1].y - corners[0].y
            val len = hypot(spanX, spanY)
            if (len <= 1e-9) return null
            val axis = Vec3(spanX / len, 0.0, spanY / len)
            var theta = (windowTiltDeg * PI) / 180.0
            if (opening.mirroredY) theta = -theta
            if (!isOpening) theta = -theta
            return deltaAbout(
                axis,
                theta * fraction,
                Vec3(opening.piece.x, opening.piece.elevation, opening.piece.y),
            )
        }
        val hinge = hingePoint(opening) ?: return null
        val theta = signedSwingRadians(opening) * (if (isOpening) 1.0 else -1.0)
        return deltaAbout(
            Vec3(0.0, 1.0, 0.0),
            theta * fraction,
            Vec3(hinge.x, 0.0, hinge.y),
        )
    }
}

fun identityMat4(): Mat4 =
    doubleArrayOf(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0,
    )

fun applyMat4(m: Mat4, p: Vec3): Vec3 =
    Vec3(
        m[0] * p.x + m[1] * p.y + m[2] * p.z + m[3],
        m[4] * p.x + m[5] * p.y + m[6] * p.z + m[7],
        m[8] * p.x + m[9] * p.y + m[10] * p.z + m[11],
    )

private fun mulMat4(a: Mat4, b: Mat4): Mat4 {
    val out = DoubleArray(16)
    for (r in 0 until 4) {
        for (c in 0 until 4) {
            out[r * 4 + c] =
                a[r * 4 + 0] * b[0 * 4 + c] +
                    a[r * 4 + 1] * b[1 * 4 + c] +
                    a[r * 4 + 2] * b[2 * 4 + c] +
                    a[r * 4 + 3] * b[3 * 4 + c]
        }
    }
    return out
}

private fun translationMat4(p: Vec3): Mat4 =
    doubleArrayOf(
        1.0, 0.0, 0.0, p.x,
        0.0, 1.0, 0.0, p.y,
        0.0, 0.0, 1.0, p.z,
        0.0, 0.0, 0.0, 1.0,
    )

private fun rotationMat4(axis: Vec3, theta: Double): Mat4 {
    val len = hypot(axis.x, hypot(axis.y, axis.z)).coerceAtLeast(1e-12)
    val x = axis.x / len
    val y = axis.y / len
    val z = axis.z / len
    val c = cos(theta)
    val s = sin(theta)
    val t = 1.0 - c
    return doubleArrayOf(
        t * x * x + c, t * x * y - s * z, t * x * z + s * y, 0.0,
        t * x * y + s * z, t * y * y + c, t * y * z - s * x, 0.0,
        t * x * z - s * y, t * y * z + s * x, t * z * z + c, 0.0,
        0.0, 0.0, 0.0, 1.0,
    )
}

private fun deltaAbout(axis: Vec3, theta: Double, pivot: Vec3): Mat4 =
    mulMat4(
        mulMat4(translationMat4(pivot), rotationMat4(axis, theta)),
        translationMat4(Vec3(-pivot.x, -pivot.y, -pivot.z)),
    )
