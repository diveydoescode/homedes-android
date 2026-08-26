package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Level
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room

/**
 * Staircase floor openings (iOS Extruder.staircaseHoles / X-9).
 *
 * A piece with [HomePieceOfFurniture.staircaseCutOut] punches a hole in the
 * floor of the storey ABOVE where it sits. Partially overlapping stairs are
 * ignored (every footprint corner must lie in the room).
 */
object StaircaseCutout {

    fun levelBelowIds(levels: List<Level>): Map<String, String> {
        if (levels.size < 2) return emptyMap()
        val ordered = levels.sortedWith(
            compareBy<Level> { it.elevationIndex }.thenBy { it.elevation },
        )
        val below = HashMap<String, String>(ordered.size - 1)
        for (i in 1 until ordered.size) {
            below[ordered[i].id] = ordered[i - 1].id
        }
        return below
    }

    /** Holes to subtract from [room]'s floor (stairs on the storey below). */
    fun floorHoles(room: Room, home: Home): List<List<Vec2>> {
        val below = levelBelowIds(home.levels)[room.level] ?: return emptyList()
        val stairs = home.furniture.filter { piece ->
            piece.visible && piece.staircaseCutOut == true && piece.level == below
        }
        return holesInRoom(room.points, stairs)
    }

    /**
     * Stair footprints fully contained in [roomPoints]. Each hole is the
     * rotated width×depth rectangle from [FurnitureGeometry.cornerPoints].
     */
    fun holesInRoom(
        roomPoints: List<Point>,
        stairs: List<HomePieceOfFurniture>,
    ): List<List<Vec2>> {
        if (roomPoints.size < 3) return emptyList()
        val poly = roomPoints.map { vec(it.x, it.y) }
        val holes = ArrayList<List<Vec2>>()
        for (piece in stairs) {
            val corners = FurnitureGeometry.cornerPoints(piece)
            if (corners.size != 4) continue
            if (corners.any { !pointInPolygon(poly, it) }) continue
            holes.add(corners)
        }
        return holes
    }
}
