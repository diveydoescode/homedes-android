package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

object OrthoLock {
    /**
     * Constrain [end] relative to [start] to the nearest of the four
     * axes. Length is preserved along the winning axis — the
     * perpendicular component is dropped, not projected.
     */
    fun constrain(start: Vec2, end: Vec2): Vec2 {
        val d = sub(end, start)
        return if (abs(d.x) >= abs(d.y)) vec(end.x, start.y) else vec(start.x, end.y)
    }

    /** Eight-direction lock. Keeps the drag's full LENGTH. */
    fun constrainToOctant(start: Vec2, end: Vec2): Vec2 {
        val d = sub(end, start)
        val len = length(d)
        if (len <= 1e-9) return end
        val step = Math.PI / 4.0
        val snapped = round(atan2(d.y, d.x) / step) * step
        return add(start, scale(vec(cos(snapped), sin(snapped)), len))
    }
}

object RectangleRoom {
    /**
     * Four walls around the rect spanned by two opposite corners,
     * wound clockwise on screen (top → right → bottom → left).
     * Empty list if either side < minSideCM.
     */
    fun walls(
        from: Vec2,
        to: Vec2,
        thickness: Double,
        height: Double,
        level: String? = null,
        minSideCM: Double = minRectRoomSideCM,
        idPrefix: String = UUID.randomUUID().toString(),
    ): List<Wall> {
        val minX = min(from.x, to.x)
        val maxX = max(from.x, to.x)
        val minY = min(from.y, to.y)
        val maxY = max(from.y, to.y)
        if (maxX - minX < minSideCM || maxY - minY < minSideCM) return emptyList()

        val corners = listOf(
            vec(minX, minY),
            vec(maxX, minY),
            vec(maxX, maxY),
            vec(minX, maxY),
        )
        val ids = (0..3).map { "$idPrefix-w$it" }
        return (0..3).map { i ->
            val next = (i + 1) % 4
            val prev = (i + 3) % 4
            Wall(
                id = ids[i],
                startX = corners[i].x,
                startY = corners[i].y,
                endX = corners[next].x,
                endY = corners[next].y,
                thickness = thickness,
                height = height,
                atStart = ids[prev],
                atEnd = ids[next],
                level = level,
            )
        }
    }
}
