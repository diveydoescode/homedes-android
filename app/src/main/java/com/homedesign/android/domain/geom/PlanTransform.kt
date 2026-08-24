package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Compass
import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.PlanLabel
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallCurveProfile
import com.homedesign.android.domain.model.WallSpan
import kotlin.math.PI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Reflect across a vertical (x=const) or horizontal (y=const) axis. */
enum class PlanAxis { Vertical, Horizontal }

/** Quarter-turn in plan space (Y-down). */
enum class PlanRotation { Clockwise, CounterClockwise }

/**
 * Pure plan rewrite helpers (mirror / rotate geometry, not the camera).
 * Port of web `geom/PlanTransform.ts` / iOS `PlanTransform.swift`.
 */
object PlanTransform {

    data class Payload(
        val walls: List<Wall> = emptyList(),
        val rooms: List<Room> = emptyList(),
        val furniture: List<HomePieceOfFurniture> = emptyList(),
        val openings: List<HomeDoorOrWindow> = emptyList(),
        val dimensionLines: List<DimensionLine> = emptyList(),
        val labels: List<PlanLabel> = emptyList(),
        val compass: Compass = null,
    )

    fun normalizeAngle(a: Double): Double {
        var out = a % (PI * 2)
        if (out < 0) out += PI * 2
        return out
    }

    fun center(payload: Payload): Vec2? {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var saw = false
        fun include(x: Double, y: Double) {
            saw = true
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        for (w in payload.walls) {
            include(w.startX, w.startY)
            include(w.endX, w.endY)
        }
        for (r in payload.rooms) {
            for (p in r.points) include(p.x, p.y)
        }
        for (f in payload.furniture) include(f.x, f.y)
        for (o in payload.openings) include(o.piece.x, o.piece.y)
        for (d in payload.dimensionLines) {
            include(d.xStart, d.yStart)
            include(d.xEnd, d.yEnd)
        }
        for (l in payload.labels) include(l.x, l.y)
        if (!saw) return null
        return vec((minX + maxX) / 2, (minY + maxY) / 2)
    }

    fun mirror(payload: Payload, axis: PlanAxis, pivot: Vec2): Payload {
        fun point(p: Vec2): Vec2 = when (axis) {
            PlanAxis.Vertical -> vec(2 * pivot.x - p.x, p.y)
            PlanAxis.Horizontal -> vec(p.x, 2 * pivot.y - p.y)
        }
        fun ang(a: Double): Double = normalizeAngle(
            when (axis) {
                PlanAxis.Vertical -> PI - a
                PlanAxis.Horizontal -> -a
            },
        )
        val walls = payload.walls.map { wall ->
            val s = point(vec(wall.startX, wall.startY))
            val e = point(vec(wall.endX, wall.endY))
            wall.copy(
                startX = s.x,
                startY = s.y,
                endX = e.x,
                endY = e.y,
                leftSideColor = wall.rightSideColor,
                rightSideColor = wall.leftSideColor,
                leftSideTexture = wall.rightSideTexture,
                rightSideTexture = wall.leftSideTexture,
                leftSideShininess = wall.rightSideShininess,
                rightSideShininess = wall.leftSideShininess,
                leftSidePattern = wall.rightSidePattern,
                rightSidePattern = wall.leftSidePattern,
                leftSideBaseboard = wall.rightSideBaseboard,
                rightSideBaseboard = wall.leftSideBaseboard,
                arcExtent = wall.arcExtent?.let { -it },
                curveProfile = wall.curveProfile?.let { flipCurveProfile(it) },
            )
        }
        val rooms = payload.rooms.map { room ->
            room.copy(
                points = room.points.asReversed().map { p ->
                    val q = point(vec(p.x, p.y))
                    Point(q.x, q.y)
                },
            )
        }
        val furniture = payload.furniture.map { piece ->
            val p = point(vec(piece.x, piece.y))
            piece.copy(
                x = p.x,
                y = p.y,
                angle = ang(piece.angle),
                modelMirrored = !piece.modelMirrored,
            )
        }
        val openings = payload.openings.map { opening ->
            val p = point(vec(opening.piece.x, opening.piece.y))
            opening.copy(
                mirroredX = !opening.mirroredX,
                piece = opening.piece.copy(
                    x = p.x,
                    y = p.y,
                    angle = ang(opening.piece.angle),
                ),
            )
        }
        val dimensionLines = payload.dimensionLines.map { dim ->
            val a = point(vec(dim.xStart, dim.yStart))
            val b = point(vec(dim.xEnd, dim.yEnd))
            dim.copy(
                xStart = a.x,
                yStart = a.y,
                xEnd = b.x,
                yEnd = b.y,
                offset = -dim.offset,
            )
        }
        val labels = payload.labels.map { label ->
            val p = point(vec(label.x, label.y))
            label.copy(x = p.x, y = p.y, angle = ang(label.angle))
        }
        val compass = transformCompass(payload.compass, ::point, ::ang)
        return Payload(walls, rooms, furniture, openings, dimensionLines, labels, compass)
    }

    fun rotate(payload: Payload, rotation: PlanRotation, pivot: Vec2): Payload {
        fun point(p: Vec2): Vec2 {
            val dx = p.x - pivot.x
            val dy = p.y - pivot.y
            val d = when (rotation) {
                PlanRotation.Clockwise -> vec(-dy, dx)
                PlanRotation.CounterClockwise -> vec(dy, -dx)
            }
            return vec(pivot.x + d.x, pivot.y + d.y)
        }
        val delta = when (rotation) {
            PlanRotation.Clockwise -> PI / 2
            PlanRotation.CounterClockwise -> -PI / 2
        }
        fun ang(a: Double): Double = normalizeAngle(a + delta)
        val walls = payload.walls.map { wall ->
            val s = point(vec(wall.startX, wall.startY))
            val e = point(vec(wall.endX, wall.endY))
            wall.copy(startX = s.x, startY = s.y, endX = e.x, endY = e.y)
        }
        val rooms = payload.rooms.map { room ->
            room.copy(
                points = room.points.map { p ->
                    val q = point(vec(p.x, p.y))
                    Point(q.x, q.y)
                },
            )
        }
        val furniture = payload.furniture.map { piece ->
            val p = point(vec(piece.x, piece.y))
            piece.copy(x = p.x, y = p.y, angle = ang(piece.angle))
        }
        val openings = payload.openings.map { opening ->
            val p = point(vec(opening.piece.x, opening.piece.y))
            opening.copy(
                piece = opening.piece.copy(x = p.x, y = p.y, angle = ang(opening.piece.angle)),
            )
        }
        val dimensionLines = payload.dimensionLines.map { dim ->
            val a = point(vec(dim.xStart, dim.yStart))
            val b = point(vec(dim.xEnd, dim.yEnd))
            dim.copy(xStart = a.x, yStart = a.y, xEnd = b.x, yEnd = b.y)
        }
        val labels = payload.labels.map { label ->
            val p = point(vec(label.x, label.y))
            label.copy(x = p.x, y = p.y, angle = ang(label.angle))
        }
        val compass = transformCompass(payload.compass, ::point, ::ang)
        return Payload(walls, rooms, furniture, openings, dimensionLines, labels, compass)
    }

    private fun flipCurveProfile(profile: WallCurveProfile): WallCurveProfile {
        val spans = profile.spans.map { span ->
            when (span) {
                is WallSpan.Straight -> span
                is WallSpan.Arc -> span.copy(
                    bow = span.bow?.let { -it },
                    extent = span.extent?.let { -it },
                )
            }
        }
        return profile.copy(spans = spans)
    }

    private fun transformCompass(
        compass: Compass,
        point: (Vec2) -> Vec2,
        ang: (Double) -> Double,
    ): Compass {
        if (compass !is JsonObject) return compass
        val northEl = compass["northDirection"] ?: return compass
        val north = northEl.jsonPrimitive.doubleOrNull ?: return compass
        val x = compass["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val y = compass["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val p = point(vec(x, y))
        val next = compass.toMutableMap()
        next["x"] = JsonPrimitive(p.x)
        next["y"] = JsonPrimitive(p.y)
        next["northDirection"] = JsonPrimitive(ang(north))
        // Keep diameter if present as string/number — leave untouched.
        compass["diameter"]?.let { next["diameter"] = it }
        return JsonObject(next)
    }
}
