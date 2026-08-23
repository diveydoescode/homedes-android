package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room

object RoomMutation {
    fun add(rooms: List<Room>, room: Room): List<Room> =
        rooms.map { it.copy(points = it.points.map { p -> p.copy() }) } +
            room.copy(points = room.points.map { it.copy() })

    fun remove(rooms: List<Room>, roomID: String): List<Room> =
        rooms.filter { it.id != roomID }.map { it.copy(points = it.points.map { p -> p.copy() }) }

    /** Sets `name` and marks the room hand-edited (`autoDetected = false`). */
    fun rename(rooms: List<Room>, roomID: String, name: String): List<Room> =
        rooms.map { r ->
            if (r.id != roomID) {
                r.copy(points = r.points.map { it.copy() })
            } else {
                r.copy(points = r.points.map { it.copy() }, name = name, autoDetected = false)
            }
        }

    fun shiftCoincidentVertices(
        rooms: List<Room>,
        anchor: Vec2,
        delta: Vec2,
        radiusCM: Double = roomVertexFollowCM,
    ): List<Room> {
        if (radiusCM <= 0) {
            return rooms.map { it.copy(points = it.points.map { p -> p.copy() }) }
        }
        val r2 = radiusCM * radiusCM
        return rooms.map { room ->
            room.copy(
                points = room.points.map { p ->
                    val dx = p.x - anchor.x
                    val dy = p.y - anchor.y
                    if (dx * dx + dy * dy <= r2) {
                        Point(p.x + delta.x, p.y + delta.y)
                    } else {
                        p.copy()
                    }
                },
            )
        }
    }
}
