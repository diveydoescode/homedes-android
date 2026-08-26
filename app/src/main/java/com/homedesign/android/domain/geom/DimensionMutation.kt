package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.Wall
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private data class OrientedChord(val start: Vec2, val end: Vec2, val offset: Double)

object DimensionMutation {
    val defaultSpacingCM: Double = dimensionFaceGapCM

    /**
     * One-tap wall dimension (iOS `dimension(forWall:)`): centreline chord
     * oriented so a positive offset lands outside when the wall bounds the envelope.
     */
    fun dimension(
        forWall: Wall,
        inWalls: List<Wall>,
        spacingCM: Double = defaultSpacingCM,
    ): DimensionLine {
        val chord = orientedChord(forWall, inWalls, spacingCM)
        return DimensionLine(
            id = UUID.randomUUID().toString().lowercase(),
            xStart = chord.start.x,
            yStart = chord.start.y,
            xEnd = chord.end.x,
            yEnd = chord.end.y,
            offset = chord.offset,
            level = forWall.level,
        )
    }

    /**
     * Openings-aware wall dimensions (iOS `dimensions(forWall:)`): broken at
     * doors/windows so a wall with a centred door yields pier | opening | pier.
     */
    fun dimensions(
        forWall: Wall,
        inWalls: List<Wall>,
        openings: List<HomeDoorOrWindow> = emptyList(),
        spacingCM: Double = defaultSpacingCM,
    ): List<DimensionLine> {
        val chord = orientedChord(forWall, inWalls, spacingCM)
        val levelOpenings = openings.filter { it.piece.level == forWall.level }
        val bindings = OpeningBinding.bind(
            walls = inWalls.filter { it.level == forWall.level },
            openings = levelOpenings,
        )
        val segs = openingWorldSegments(forWall, bindings)
        return splitDimensions(
            start = chord.start,
            end = chord.end,
            openingSegments = segs,
            offset = chord.offset,
            level = forWall.level,
        )
    }

    fun openingWorldSegments(
        wall: Wall,
        bindings: List<OpeningBind>,
    ): List<Pair<Vec2, Vec2>> {
        val a = vec(wall.startX, wall.startY)
        val spanX = wall.endX - a.x
        val spanY = wall.endY - a.y
        return bindings
            .filter { it.wallID == wall.id }
            .map { b ->
                vec(a.x + spanX * b.tStart, a.y + spanY * b.tStart) to
                    vec(a.x + spanX * b.tEnd, a.y + spanY * b.tEnd)
            }
    }

    fun splitDimensions(
        start: Vec2,
        end: Vec2,
        openingSegments: List<Pair<Vec2, Vec2>>,
        offset: Double,
        level: String?,
        idPrefix: String = "",
    ): List<DimensionLine> {
        val dirx = end.x - start.x
        val diry = end.y - start.y
        val lenSq = dirx * dirx + diry * diry
        if (lenSq <= 1e-9) return emptyList()
        fun pointAt(t: Double): Vec2 = vec(start.x + dirx * t, start.y + diry * t)
        fun line(tA: Double, tB: Double): DimensionLine {
            val p = pointAt(tA)
            val q = pointAt(tB)
            return DimensionLine(
                id = "$idPrefix${UUID.randomUUID().toString().lowercase()}",
                xStart = p.x,
                yStart = p.y,
                xEnd = q.x,
                yEnd = q.y,
                offset = offset,
                level = level,
            )
        }
        val epsilon = 1e-6
        val spans = mutableListOf<Pair<Double, Double>>()
        for ((oa, ob) in openingSegments) {
            val tA = ((oa.x - start.x) * dirx + (oa.y - start.y) * diry) / lenSq
            val tB = ((ob.x - start.x) * dirx + (ob.y - start.y) * diry) / lenSq
            val lo = max(0.0, min(tA, tB))
            val hi = min(1.0, max(tA, tB))
            if (hi - lo > epsilon) spans.add(lo to hi)
        }
        if (spans.isEmpty()) return listOf(line(0.0, 1.0))
        spans.sortBy { it.first }
        val merged = mutableListOf<Pair<Double, Double>>()
        for (s in spans) {
            val last = merged.lastOrNull()
            if (last != null && s.first < last.second) continue
            merged.add(s)
        }
        val result = mutableListOf<DimensionLine>()
        var cursor = 0.0
        for ((lo, hi) in merged) {
            if (lo - cursor > epsilon) result.add(line(cursor, lo))
            result.add(line(lo, hi))
            cursor = hi
        }
        if (1.0 - cursor > epsilon) result.add(line(cursor, 1.0))
        return result
    }

    fun exteriorChain(
        walls: List<Wall>,
        level: String?,
        openings: List<HomeDoorOrWindow> = emptyList(),
        spacingCM: Double = dimensionFaceGapCM,
        idPrefix: String = "",
        faceMode: DimensionFaceMode = DimensionFaceMode.Outer,
    ): List<DimensionLine> {
        val levelWalls = walls.filter { it.level == level }
        val levelOpenings = openings.filter { it.piece.level == level }
        val bindings = OpeningBinding.bind(levelWalls, levelOpenings)
        val byID = levelWalls.associateBy { it.id }
        val edges = RoomDetection.exteriorWallEdges(levelWalls)
        val corners = envelopeFaceCorners(edges, byID, faceMode)
        val offset = dimensionFaceOffsetCM(faceMode, spacingCM)
        return edges.flatMapIndexed { i, edge ->
            val wall = byID[edge.wallID]
            if (wall == null || ArcWallGeometry.isCurved(wall)) emptyList()
            else {
                val start = corners.getOrNull(i) ?: edge.start
                val end = corners.getOrNull((i + 1) % max(corners.size, 1)) ?: edge.end
                val segs = openingWorldSegments(wall, bindings)
                splitDimensions(start, end, segs, offset, level, idPrefix)
            }
        }
    }

    private fun orientedChord(
        wall: Wall,
        walls: List<Wall>,
        spacingCM: Double,
    ): OrientedChord {
        val levelWalls = walls.filter { it.level == wall.level }
        val outline = RoomDetection.exteriorWallEdges(levelWalls)
        val offset = wall.thickness / 2.0 + spacingCM
        val s = vec(wall.startX, wall.startY)
        val e = vec(wall.endX, wall.endY)
        val edge = outline.firstOrNull { it.wallID == wall.id }
        if (edge != null) {
            val edgeDir = sub(edge.end, edge.start)
            val wallDir = sub(e, s)
            val aligned = dot(edgeDir, wallDir) > 0
            return if (aligned) OrientedChord(s, e, offset) else OrientedChord(e, s, offset)
        }
        return OrientedChord(s, e, offset)
    }
}
