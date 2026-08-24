package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AlignmentGuideSegment(
    val axis: String, // "horizontal" | "vertical"
    val start: Vec2,
    val end: Vec2,
)

data class ObjectAlignmentResult(
    val center: Vec2,
    val guides: List<AlignmentGuideSegment>,
)

data class Aabb(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

private data class AlignLine(val coord: Double, val low: Double, val high: Double)

/** Port of web `ObjectAlignment.ts`. */
object ObjectAlignment {
    fun boundingBox(piece: HomePieceOfFurniture): Aabb =
        aabbOfPoints(FurnitureGeometry.cornerPoints(piece))

    fun align(
        movingPiece: HomePieceOfFurniture,
        otherPieces: List<HomePieceOfFurniture>,
        containingRoom: Room?,
        toleranceCM: Double,
        walls: List<Wall> = emptyList(),
        groupIDsToSkip: Set<String> = emptySet(),
    ): ObjectAlignmentResult {
        val proposedCenter = vec(movingPiece.x, movingPiece.y)
        if (toleranceCM <= 0) return ObjectAlignmentResult(proposedCenter, emptyList())

        val box = boundingBox(movingPiece)
        val centre = vec(movingPiece.x, movingPiece.y)
        val halfW = max(box.maxX - centre.x, centre.x - box.minX)
        val halfH = max(box.maxY - centre.y, centre.y - box.minY)

        val verticalLines = mutableListOf<AlignLine>()
        val horizontalLines = mutableListOf<AlignLine>()
        for (piece in otherPieces) {
            if (piece.id == movingPiece.id) continue
            val gid = piece.groupID
            if (gid != null && gid in groupIDsToSkip) continue
            val b = boundingBox(piece)
            val cx = (b.minX + b.maxX) / 2
            val cy = (b.minY + b.maxY) / 2
            for (x in listOf(cx, b.minX, b.maxX)) {
                verticalLines.add(AlignLine(x, b.minY, b.maxY))
            }
            for (y in listOf(cy, b.minY, b.maxY)) {
                horizontalLines.add(AlignLine(y, b.minX, b.maxX))
            }
        }

        val wallAxisVertical = mutableListOf<Double>()
        val wallAxisHorizontal = mutableListOf<Double>()
        for (wall in walls) {
            val dx = abs(wall.endX - wall.startX)
            val dy = abs(wall.endY - wall.startY)
            val half = wall.thickness / 2
            if (dx < 1e-6 && dy > 1e-6) {
                val lo = min(wall.startY, wall.endY)
                val hi = max(wall.startY, wall.endY)
                for (x in listOf(wall.startX - half, wall.startX + half)) {
                    verticalLines.add(AlignLine(x, lo, hi))
                }
                wallAxisVertical.add(wall.startX)
            } else if (dy < 1e-6 && dx > 1e-6) {
                val lo = min(wall.startX, wall.endX)
                val hi = max(wall.startX, wall.endX)
                for (y in listOf(wall.startY - half, wall.startY + half)) {
                    horizontalLines.add(AlignLine(y, lo, hi))
                }
                wallAxisHorizontal.add(wall.startY)
            }
        }

        val maxHalf = (walls.maxOfOrNull { it.thickness / 2 } ?: 0.0) + 1
        fun wallBacked(coord: Double, axis: List<Double>) =
            axis.any { abs(it - coord) <= maxHalf }

        val roomPoints = containingRoom?.points.orEmpty()
        if (roomPoints.size >= 3) {
            val r = aabbOfPoints(roomPoints.map { vec(it.x, it.y) })
            val rcx = (r.minX + r.maxX) / 2
            val rcy = (r.minY + r.maxY) / 2
            verticalLines.add(AlignLine(rcx, r.minY, r.maxY))
            for (x in listOf(r.minX, r.maxX)) {
                if (!wallBacked(x, wallAxisVertical)) {
                    verticalLines.add(AlignLine(x, r.minY, r.maxY))
                }
            }
            horizontalLines.add(AlignLine(rcy, r.minX, r.maxX))
            for (y in listOf(r.minY, r.maxY)) {
                if (!wallBacked(y, wallAxisHorizontal)) {
                    horizontalLines.add(AlignLine(y, r.minX, r.maxX))
                }
            }
        }

        val bestX = nearest(verticalLines, listOf(0.0, -halfW, halfW), proposedCenter.x, toleranceCM)
        val bestY = nearest(horizontalLines, listOf(0.0, -halfH, halfH), proposedCenter.y, toleranceCM)

        var snapped = proposedCenter
        val guides = mutableListOf<AlignmentGuideSegment>()
        if (bestX != null) {
            snapped = vec(bestX.line.coord + bestX.featureOffset, snapped.y)
            val lo = min(bestX.line.low, snapped.y - halfH)
            val hi = max(bestX.line.high, snapped.y + halfH)
            guides.add(
                AlignmentGuideSegment(
                    axis = "vertical",
                    start = vec(bestX.line.coord, lo),
                    end = vec(bestX.line.coord, hi),
                ),
            )
        }
        if (bestY != null) {
            snapped = vec(snapped.x, bestY.line.coord + bestY.featureOffset)
            val lo = min(bestY.line.low, snapped.x - halfW)
            val hi = max(bestY.line.high, snapped.x + halfW)
            guides.add(
                AlignmentGuideSegment(
                    axis = "horizontal",
                    start = vec(lo, bestY.line.coord),
                    end = vec(hi, bestY.line.coord),
                ),
            )
        }
        return ObjectAlignmentResult(snapped, guides)
    }

    private fun aabbOfPoints(pts: List<Vec2>): Aabb {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (c in pts) {
            if (c.x < minX) minX = c.x
            if (c.y < minY) minY = c.y
            if (c.x > maxX) maxX = c.x
            if (c.y > maxY) maxY = c.y
        }
        return Aabb(minX, minY, maxX, maxY)
    }

    private data class NearestHit(val line: AlignLine, val featureOffset: Double, val distance: Double)

    private fun nearest(
        lines: List<AlignLine>,
        featureOffsets: List<Double>,
        proposedCentreCoord: Double,
        toleranceCM: Double,
    ): NearestHit? {
        var best: NearestHit? = null
        for (line in lines) {
            for (offset in featureOffsets) {
                val centreCoord = line.coord + offset
                val d = abs(centreCoord - proposedCentreCoord)
                if (d < toleranceCM && (best == null || d < best.distance)) {
                    best = NearestHit(line, offset, d)
                }
            }
        }
        return best
    }
}
