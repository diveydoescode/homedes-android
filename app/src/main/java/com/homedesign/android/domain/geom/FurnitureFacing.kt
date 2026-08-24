package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Wall
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of web `FurnitureFacing.ts` — face a piece toward/away from nearest wall.
 */
object FurnitureFacing {
    fun faceTowardNearestWall(piece: HomePieceOfFurniture, walls: List<Wall>): HomePieceOfFurniture {
        val snap = FurnitureSnap.snapToWall(piece, walls) ?: return piece
        return piece.copy(angle = snap.angle)
    }

    fun rotateBy(piece: HomePieceOfFurniture, deltaRad: Double): HomePieceOfFurniture =
        piece.copy(angle = wrapFurnitureAngle(piece.angle + deltaRad))

    fun frontDirection(piece: HomePieceOfFurniture): Vec2 {
        // Front is +local Y after rotation (plan Y-down).
        return vec(sin(piece.angle), cos(piece.angle))
    }

    fun wallFacingAngle(wall: Wall, fromOutside: Boolean): Double {
        val dx = wall.endX - wall.startX
        val dy = wall.endY - wall.startY
        val along = atan2(dy, dx)
        val inward = along + if (fromOutside) Math.PI / 2 else -Math.PI / 2
        return wrapFurnitureAngle(inward)
    }
}
