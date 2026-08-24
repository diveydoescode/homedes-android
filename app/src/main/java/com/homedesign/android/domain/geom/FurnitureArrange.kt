package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import kotlin.math.abs

enum class AlignEdge { Left, CenterX, Right, Top, CenterY, Bottom }
enum class DistributeAxis { Horizontal, Vertical }

data class ArrangeMove(val id: String, val x: Double, val y: Double)

/** Port of web `FurnitureArrange.ts` — align/distribute over rotated AABBs. */
object FurnitureArrange {
    fun align(edge: AlignEdge, pieces: List<HomePieceOfFurniture>): List<ArrangeMove> {
        if (pieces.size < 2) return emptyList()
        val boxes = pieces.map { ObjectAlignment.boundingBox(it) }
        val minX = boxes.minOf { it.minX }
        val maxX = boxes.maxOf { it.maxX }
        val minY = boxes.minOf { it.minY }
        val maxY = boxes.maxOf { it.maxY }
        val moves = mutableListOf<ArrangeMove>()
        for (i in pieces.indices) {
            val piece = pieces[i]
            val box = boxes[i]
            var x = piece.x
            var y = piece.y
            when (edge) {
                AlignEdge.Left -> x += minX - box.minX
                AlignEdge.Right -> x += maxX - box.maxX
                AlignEdge.CenterX -> x += (minX + maxX) / 2 - (box.minX + box.maxX) / 2
                AlignEdge.Top -> y += minY - box.minY
                AlignEdge.Bottom -> y += maxY - box.maxY
                AlignEdge.CenterY -> y += (minY + maxY) / 2 - (box.minY + box.maxY) / 2
            }
            if (x != piece.x || y != piece.y) moves.add(ArrangeMove(piece.id, x, y))
        }
        return moves
    }

    fun distribute(axis: DistributeAxis, pieces: List<HomePieceOfFurniture>): List<ArrangeMove> {
        if (pieces.size < 3) return emptyList()
        val items = pieces.map { piece ->
            val box = ObjectAlignment.boundingBox(piece)
            val lo = if (axis == DistributeAxis.Horizontal) box.minX else box.minY
            val hi = if (axis == DistributeAxis.Horizontal) box.maxX else box.maxY
            Triple(piece, lo, hi - lo)
        }.sortedBy { it.second }
        val first = items.first()
        val last = items.last()
        val span = (last.second + last.third) - first.second
        val totalSize = items.sumOf { it.third }
        val gap = (span - totalSize) / (items.size - 1)
        val moves = mutableListOf<ArrangeMove>()
        var cursor = first.second
        for (item in items) {
            val targetLo = cursor
            val delta = targetLo - item.second
            cursor = targetLo + item.third + gap
            if (abs(delta) <= 1e-9) continue
            moves.add(
                if (axis == DistributeAxis.Horizontal) {
                    ArrangeMove(item.first.id, item.first.x + delta, item.first.y)
                } else {
                    ArrangeMove(item.first.id, item.first.x, item.first.y + delta)
                },
            )
        }
        return moves
    }
}
