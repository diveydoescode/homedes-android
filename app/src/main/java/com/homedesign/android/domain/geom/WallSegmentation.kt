package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.Wall

/** Rectangular cutout along a wall centreline (t-range + vertical extents). */
data class WallCutout(
    val tStart: Double,
    val tEnd: Double,
    val bottomCutoutY: Double,
    val topCutoutY: Double,
)

/** Role of a solid box after opening cutout subtraction (iOS WallSegmentation parity). */
enum class WallSegmentRole {
    FullHeight,
    Lintel,
    Sill,
}

/** One solid box along a wall after door/window holes are carved out. */
data class WallSegment(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
    val thickness: Double,
    val bottomY: Double,
    val topY: Double,
    val role: WallSegmentRole = WallSegmentRole.FullHeight,
)

/**
 * Port of web `geom/WallSegmentation.ts` + iOS `WallSegmentation.wallSegments`.
 * 2D canvas uses t-ranges to punch holes in the wall fill (even-odd).
 * 3D extrusion carves the wall prism into full-height / lintel / sill boxes.
 */
object WallSegmentation {
    fun makeCutout(binding: OpeningBind, opening: HomeDoorOrWindow): WallCutout =
        WallCutout(
            tStart = binding.tStart,
            tEnd = binding.tEnd,
            bottomCutoutY = opening.piece.elevation,
            topCutoutY = opening.piece.elevation + opening.piece.height,
        )

    fun cutoutsByWallID(
        walls: List<Wall>,
        openings: List<HomeDoorOrWindow>,
    ): Map<String, List<WallCutout>> {
        if (walls.isEmpty() || openings.isEmpty()) return emptyMap()

        val levels = linkedSetOf<String?>()
        for (w in walls) levels.add(w.level)
        for (o in openings) levels.add(o.piece.level)

        val openingByID = openings.associateBy { it.piece.id }
        val result = mutableMapOf<String, MutableList<WallCutout>>()

        for (level in levels) {
            val wallsAt = walls.filter { it.level == level }
            val openingsAt = openings.filter { it.piece.level == level }
            if (wallsAt.isEmpty() || openingsAt.isEmpty()) continue
            val bindings = OpeningBinding.bind(wallsAt, openingsAt)
            for (binding in bindings) {
                val opening = openingByID[binding.openingID] ?: continue
                result.getOrPut(binding.wallID) { mutableListOf() }
                    .add(makeCutout(binding, opening))
            }
        }
        return result
    }

    /**
     * Remaining solid boxes after subtracting [cutouts] from [wall].
     * Full-height spans between openings; lintel above and sill below each hole.
     */
    fun wallSegments(wall: Wall, cutouts: List<WallCutout>): List<WallSegment> {
        val startX = wall.startX
        val startY = wall.startY
        val endX = wall.endX
        val endY = wall.endY
        val thickness = wall.thickness
        val height = wall.height.coerceAtLeast(1.0)
        val dx = endX - startX
        val dy = endY - startY

        fun centerAt(t: Double): Pair<Double, Double> =
            (startX + dx * t) to (startY + dy * t)

        if (cutouts.isEmpty()) {
            return listOf(
                WallSegment(startX, startY, endX, endY, thickness, 0.0, height, WallSegmentRole.FullHeight),
            )
        }

        val cleaned = cutouts.mapNotNull { c ->
            if (c.tEnd <= c.tStart) return@mapNotNull null
            val t0 = maxOf(0.0, c.tStart)
            val t1 = minOf(1.0, c.tEnd)
            if (t1 <= t0) null
            else WallCutout(t0, t1, c.bottomCutoutY, c.topCutoutY)
        }.sortedBy { it.tStart }

        val nonOverlapping = ArrayList<WallCutout>(cleaned.size)
        for (c in cleaned) {
            val last = nonOverlapping.lastOrNull()
            if (last != null && c.tStart < last.tEnd) continue
            nonOverlapping.add(c)
        }

        if (nonOverlapping.isEmpty()) {
            return listOf(
                WallSegment(startX, startY, endX, endY, thickness, 0.0, height, WallSegmentRole.FullHeight),
            )
        }

        val epsilon = 1e-6
        val segments = ArrayList<WallSegment>()
        var currentT = 0.0
        for (c in nonOverlapping) {
            if (c.tStart > currentT + epsilon) {
                val a = centerAt(currentT)
                val b = centerAt(c.tStart)
                segments.add(
                    WallSegment(a.first, a.second, b.first, b.second, thickness, 0.0, height, WallSegmentRole.FullHeight),
                )
            }
            if (c.topCutoutY < height) {
                val a = centerAt(c.tStart)
                val b = centerAt(c.tEnd)
                segments.add(
                    WallSegment(
                        a.first, a.second, b.first, b.second, thickness,
                        c.topCutoutY, height, WallSegmentRole.Lintel,
                    ),
                )
            }
            if (c.bottomCutoutY > 0.0) {
                val a = centerAt(c.tStart)
                val b = centerAt(c.tEnd)
                segments.add(
                    WallSegment(
                        a.first, a.second, b.first, b.second, thickness,
                        0.0, c.bottomCutoutY, WallSegmentRole.Sill,
                    ),
                )
            }
            currentT = c.tEnd
        }
        if (currentT < 1.0 - epsilon) {
            val a = centerAt(currentT)
            segments.add(
                WallSegment(a.first, a.second, endX, endY, thickness, 0.0, height, WallSegmentRole.FullHeight),
            )
        }
        return segments
    }
}

/** Plan-space hole polygon for a binding (wall thickness + a hair of bleed). */
fun openingHoleFromBinding(wall: Wall, tStart: Double, tEnd: Double): List<Vec2> {
    val a = vec(wall.startX, wall.startY)
    val b = vec(wall.endX, wall.endY)
    val ab = sub(b, a)
    val len = length(ab)
    if (len <= 1e-9) return emptyList()
    val unit = scale(ab, 1 / len)
    val perp = vec(-unit.y, unit.x)
    val ht = wall.thickness / 2 + 0.4
    val p0 = add(a, scale(ab, tStart))
    val p1 = add(a, scale(ab, tEnd))
    val n = scale(perp, ht)
    return listOf(add(p0, n), add(p1, n), sub(p1, n), sub(p0, n))
}
