package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Screen-px stick beyond the front edge, converted to cm via / scale. */
const val ROTATE_HANDLE_STICK_PX = 24.0
/** Hit radius in screen px (visual 6 px + forgiveness). */
const val ROTATE_HANDLE_HIT_PX = 22.0

/**
 * Rotate-handle position: beyond the FRONT edge on the FurnitureSnap
 * front axis `(sin a, cos a)`. Extra stick is `24 / scale` plan-cm.
 */
fun rotateHandlePosition(piece: HomePieceOfFurniture, scale: Double): Vec2 {
    val halfD = (piece.depthInPlan ?: piece.depth) / 2.0
    val extra = ROTATE_HANDLE_STICK_PX / max(scale, 0.001)
    val front = vec(sin(piece.angle), cos(piece.angle))
    val reach = halfD + extra
    return vec(piece.x + front.x * reach, piece.y + front.y * reach)
}

fun rotateHandleHit(
    piece: HomePieceOfFurniture,
    point: Vec2,
    scale: Double,
    hitScale: Double = 1.0,
): Boolean {
    val handle = rotateHandlePosition(piece, scale)
    val tol = (ROTATE_HANDLE_HIT_PX * hitScale) / max(scale, 0.001)
    val dx = point.x - handle.x
    val dy = point.y - handle.y
    return dx * dx + dy * dy <= tol * tol
}

/** Soft snap furniture angle to [rotateSnapDeg] within [rotateSnapWindowDeg] (web parity). */
fun snapFurnitureAngle(angle: Double): Double {
    val step = (rotateSnapDeg * PI) / 180.0
    val window = (rotateSnapWindowDeg * PI) / 180.0
    val snapped = kotlin.math.round(angle / step) * step
    return if (abs(angle - snapped) < window) snapped else angle
}

/** Wrap angle into [0, 2π). */
fun wrapFurnitureAngle(angle: Double): Double {
    val twoPi = PI * 2.0
    var wrapped = angle % twoPi
    if (wrapped < 0) wrapped += twoPi
    return wrapped
}

object FurnitureGeometry {
    /**
     * Four corner points of the piece's plan footprint, after rotation.
     * [0] (−w/2, −d/2)  [1] (+w/2, −d/2)
     * [2] (+w/2, +d/2)  [3] (−w/2, +d/2)
     */
    fun cornerPoints(piece: HomePieceOfFurniture): List<Vec2> {
        val halfWidth = (piece.widthInPlan ?: piece.width) / 2.0
        val halfDepth = (piece.depthInPlan ?: piece.depth) / 2.0
        val local = listOf(
            vec(-halfWidth, -halfDepth),
            vec(halfWidth, -halfDepth),
            vec(halfWidth, halfDepth),
            vec(-halfWidth, halfDepth),
        )
        val cosA = cos(piece.angle)
        val sinA = sin(piece.angle)
        return local.map { p ->
            vec(
                p.x * cosA - p.y * sinA + piece.x,
                p.x * sinA + p.y * cosA + piece.y,
            )
        }
    }
}
