package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object WallMutation {
    fun moveWallEndpoint(
        walls: List<Wall>,
        wallID: String,
        atStart: Boolean,
        newPosition: Vec2,
        joinedCorner: Boolean = true,
        epsilonCM: Double = 1.0,
    ): List<Wall> {
        val reference = walls.find { it.id == wallID } ?: return walls.toList()
        val oldPosition = if (atStart) {
            vec(reference.startX, reference.startY)
        } else {
            vec(reference.endX, reference.endY)
        }
        val delta = sub(newPosition, oldPosition)

        val movers = mutableListOf(wallID to atStart)
        if (joinedCorner) {
            for (sib in HitTest.coincidentEndpoints(wallID, atStart, walls, epsilonCM)) {
                movers.add(sib.wallID to sib.atStart)
            }
        }
        val moverSet = movers.map { keyOf(it.first, it.second) }.toSet()

        return walls.map { wall ->
            var copy = wall
            if (keyOf(wall.id, true) in moverSet) {
                copy = copy.copy(startX = wall.startX + delta.x, startY = wall.startY + delta.y)
            }
            if (keyOf(wall.id, false) in moverSet) {
                copy = copy.copy(endX = wall.endX + delta.x, endY = wall.endY + delta.y)
            }
            copy
        }
    }

    fun moveWall(
        walls: List<Wall>,
        wallID: String,
        delta: Vec2,
        joinedCorner: Boolean = true,
        epsilonCM: Double = 1.0,
    ): List<Wall> {
        val reference = walls.find { it.id == wallID } ?: return walls.toList()
        val afterStart = moveWallEndpoint(
            walls,
            wallID,
            true,
            add(vec(reference.startX, reference.startY), delta),
            joinedCorner,
            epsilonCM,
        )
        val moved = afterStart.first { it.id == wallID }
        return moveWallEndpoint(
            afterStart,
            wallID,
            false,
            add(vec(moved.endX, moved.endY), delta),
            joinedCorner,
            epsilonCM,
        )
    }

    fun addWall(
        walls: List<Wall>,
        start: Vec2,
        end: Vec2,
        thickness: Double,
        height: Double,
        level: String? = null,
        id: String = UUID.randomUUID().toString(),
        snapToEndpointEpsilonCM: Double = 1.0,
    ): List<Wall> {
        val startSnap =
            if (snapToEndpointEpsilonCM > 0) HitTest.closestEndpoint(start, walls, snapToEndpointEpsilonCM) else null
        val endSnap =
            if (snapToEndpointEpsilonCM > 0) HitTest.closestEndpoint(end, walls, snapToEndpointEpsilonCM) else null

        val resolvedStart = startSnap?.let { snap ->
            val w = walls.first { it.id == snap.wallID }
            if (snap.atStart) vec(w.startX, w.startY) else vec(w.endX, w.endY)
        } ?: start
        val resolvedEnd = endSnap?.let { snap ->
            val w = walls.first { it.id == snap.wallID }
            if (snap.atStart) vec(w.startX, w.startY) else vec(w.endX, w.endY)
        } ?: end

        val newWall = Wall(
            id = id,
            startX = resolvedStart.x,
            startY = resolvedStart.y,
            endX = resolvedEnd.x,
            endY = resolvedEnd.y,
            thickness = thickness,
            height = height,
            atStart = startSnap?.wallID,
            atEnd = endSnap?.wallID,
            level = level,
        )

        val result = walls.map { w ->
            var copy = w
            if (startSnap != null && w.id == startSnap.wallID) {
                copy = if (startSnap.atStart) {
                    if (w.atStart == null) w.copy(atStart = newWall.id) else w
                } else {
                    if (w.atEnd == null) w.copy(atEnd = newWall.id) else w
                }
            }
            if (endSnap != null && copy.id == endSnap.wallID) {
                copy = if (endSnap.atStart) {
                    if (copy.atStart == null) copy.copy(atStart = newWall.id) else copy
                } else {
                    if (copy.atEnd == null) copy.copy(atEnd = newWall.id) else copy
                }
            }
            copy
        }
        return result + newWall
    }

    fun splitWall(
        wallID: String,
        t: Double,
        walls: List<Wall>,
        minSegmentCM: Double = minSplitSegmentCM,
    ): List<Wall> {
        val index = walls.indexOfFirst { it.id == wallID }
        if (index < 0) return walls.toList()
        val w = walls[index]
        if (ArcWallGeometry.isCurved(w)) return walls.toList()

        val start = vec(w.startX, w.startY)
        val end = vec(w.endX, w.endY)
        val span = sub(end, start)
        val wallLength = length(span)
        if (wallLength <= 2 * minSegmentCM) return walls.toList()

        val minT = minSegmentCM / wallLength
        val clampedT = max(minT, min(1.0 - minT, t))
        val split = add(start, scale(span, clampedT))

        val originalAtEnd = w.atEnd
        val w2ID = UUID.randomUUID().toString()

        val w1 = w.copy(endX = split.x, endY = split.y, atEnd = w2ID)
        val w2 = w.copy(
            id = w2ID,
            startX = split.x,
            startY = split.y,
            endX = end.x,
            endY = end.y,
            atStart = w1.id,
            atEnd = originalAtEnd,
        )

        val result = walls.toMutableList()
        result[index] = w1
        if (originalAtEnd != null) {
            val nIndex = result.indexOfFirst { it.id == originalAtEnd }
            if (nIndex >= 0) {
                val n = result[nIndex]
                result[nIndex] = when {
                    n.atStart == w.id -> n.copy(atStart = w2ID)
                    n.atEnd == w.id -> n.copy(atEnd = w2ID)
                    else -> n
                }
            }
        }
        result.add(w2)
        return result
    }

    fun setLength(
        walls: List<Wall>,
        wallID: String,
        newLength: Double,
        epsilonCM: Double = 1.0,
    ): List<Wall> = setWallLength(walls, wallID, newLength, epsilonCM)

    fun setWallLength(
        walls: List<Wall>,
        wallID: String,
        newLength: Double,
        epsilonCM: Double = 1.0,
    ): List<Wall> {
        val wall = walls.find { it.id == wallID }
        if (wall == null || ArcWallGeometry.isCurved(wall) || newLength < minDrawnWallCM) {
            return walls.toList()
        }
        val start = vec(wall.startX, wall.startY)
        val end = vec(wall.endX, wall.endY)
        val span = sub(end, start)
        val wallLength = length(span)
        if (wallLength <= 1e-9) return walls.toList()
        val dir = scale(span, 1.0 / wallLength)
        val delta = sub(add(start, scale(dir, newLength)), end)
        if (length(delta) <= 1e-9) return walls.toList()

        val moved = mutableSetOf(keyOf(wallID, false))
        val rigid = mutableSetOf<String>()

        for (sibling in HitTest.coincidentEndpoints(wallID, false, walls, epsilonCM)) {
            val sw = walls.find { it.id == sibling.wallID } ?: continue
            val sSpan = vec(sw.endX - sw.startX, sw.endY - sw.startY)
            val sLen = length(sSpan)
            val isPerpendicular =
                sLen > 1e-9 && abs(dot(dir, scale(sSpan, 1.0 / sLen))) <= 0.7071 + 1e-9
            if (!isPerpendicular) {
                moved.add(keyOf(sibling.wallID, sibling.atStart))
                continue
            }
            rigid.add(sibling.wallID)
            moved.add(keyOf(sibling.wallID, true))
            moved.add(keyOf(sibling.wallID, false))
            for (mate in HitTest.coincidentEndpoints(sibling.wallID, !sibling.atStart, walls, epsilonCM)) {
                if (mate.wallID == wallID || mate.wallID in rigid) continue
                moved.add(keyOf(mate.wallID, mate.atStart))
            }
        }

        return walls.map { w ->
            var copy = w
            if (keyOf(w.id, true) in moved) {
                copy = copy.copy(startX = w.startX + delta.x, startY = w.startY + delta.y)
            }
            if (keyOf(w.id, false) in moved) {
                copy = copy.copy(endX = w.endX + delta.x, endY = w.endY + delta.y)
            }
            copy
        }
    }

    fun setHeight(walls: List<Wall>, wallID: String, height: Double): List<Wall> {
        if (walls.none { it.id == wallID }) return walls.toList()
        return walls.map { if (it.id == wallID) it.copy(height = height) else it }
    }

    fun removeWall(walls: List<Wall>, wallID: String): List<Wall> {
        if (walls.none { it.id == wallID }) return walls.toList()
        return walls
            .filter { it.id != wallID }
            .map { wall ->
                wall.copy(
                    atStart = if (wall.atStart == wallID) null else wall.atStart,
                    atEnd = if (wall.atEnd == wallID) null else wall.atEnd,
                )
            }
    }

    private fun keyOf(id: String, atStart: Boolean): String =
        "$id:${if (atStart) "s" else "e"}"
}
