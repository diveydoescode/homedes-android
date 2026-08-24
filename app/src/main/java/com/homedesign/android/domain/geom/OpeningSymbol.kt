package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.Sash
import com.homedesign.android.domain.model.Wall
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OpeningSymbolKind { FixedWindow, Operable }

data class SashArc(
    val center: Vec2,
    val radius: Double,
    val startAngle: Double,
    val endAngle: Double,
)

data class ParallelLine(
    val start: Vec2,
    val end: Vec2,
)

/** Port of web `geom/OpeningSymbol.ts`. */
object OpeningSymbol {
    fun classify(opening: HomeDoorOrWindow): OpeningSymbolKind =
        if (opening.sashes.isEmpty()) OpeningSymbolKind.FixedWindow else OpeningSymbolKind.Operable

    /**
     * World-space swing arc. mirroredX: xAxis → 1−xAxis, θ → π−θ.
     * mirroredY: yAxis → 1−yAxis, θ → −θ.
     */
    fun sashArc(sash: Sash, opening: HomeDoorOrWindow): SashArc {
        val piece = opening.piece
        var xAxis = sash.xAxis
        var yAxis = sash.yAxis
        var startAngle = sash.startAngle
        var endAngle = sash.endAngle
        if (opening.mirroredX) {
            xAxis = 1.0 - xAxis
            startAngle = PI - startAngle
            endAngle = PI - endAngle
        }
        if (opening.mirroredY) {
            yAxis = 1.0 - yAxis
            startAngle = -startAngle
            endAngle = -endAngle
        }
        val xLocal = (xAxis - 0.5) * piece.width
        val yLocal = (yAxis - 0.5) * piece.depth
        val c = cos(piece.angle)
        val s = sin(piece.angle)
        return SashArc(
            center = vec(
                piece.x + xLocal * c - yLocal * s,
                piece.y + xLocal * s + yLocal * c,
            ),
            radius = sash.width * piece.width,
            startAngle = startAngle + piece.angle,
            endAngle = endAngle + piece.angle,
        )
    }

    /** Glass lines when unbound: parallel to opening axis, offset ±depth/4. */
    fun glassLinesUnbound(opening: HomeDoorOrWindow): List<ParallelLine> {
        val piece = opening.piece
        val hw = piece.width / 2.0
        val c = cos(piece.angle)
        val s = sin(piece.angle)
        val axis = vec(c, s)
        val perp = scale(vec(-s, c), piece.depth / 4.0)
        val a = sub(vec(piece.x, piece.y), scale(axis, hw))
        val b = add(vec(piece.x, piece.y), scale(axis, hw))
        return listOf(
            ParallelLine(add(a, perp), add(b, perp)),
            ParallelLine(sub(a, perp), sub(b, perp)),
        )
    }

    fun glassLines(bind: OpeningBind, wall: Wall): List<ParallelLine> {
        val start = vec(wall.startX, wall.startY)
        val end = vec(wall.endX, wall.endY)
        val dir = sub(end, start)
        val len = length(dir)
        if (len <= 1e-9) return emptyList()
        val unit = scale(dir, 1 / len)
        val perp = scale(vec(-unit.y, unit.x), wall.thickness / 4)
        val cutStart = add(start, scale(dir, bind.tStart))
        val cutEnd = add(start, scale(dir, bind.tEnd))
        return listOf(
            ParallelLine(add(cutStart, perp), add(cutEnd, perp)),
            ParallelLine(sub(cutStart, perp), sub(cutEnd, perp)),
        )
    }

    fun jambLines(bind: OpeningBind, wall: Wall): List<ParallelLine> {
        val start = vec(wall.startX, wall.startY)
        val end = vec(wall.endX, wall.endY)
        val dir = sub(end, start)
        val len = length(dir)
        if (len <= 1e-9) return emptyList()
        val unit = scale(dir, 1 / len)
        val perp = scale(vec(-unit.y, unit.x), wall.thickness / 2)
        val cutStart = add(start, scale(dir, bind.tStart))
        val cutEnd = add(start, scale(dir, bind.tEnd))
        return listOf(
            ParallelLine(add(cutStart, perp), sub(cutStart, perp)),
            ParallelLine(add(cutEnd, perp), sub(cutEnd, perp)),
        )
    }

    fun thresholdLine(bind: OpeningBind, wall: Wall): ParallelLine? {
        val start = vec(wall.startX, wall.startY)
        val end = vec(wall.endX, wall.endY)
        val dir = sub(end, start)
        if (length(dir) <= 1e-9) return null
        return ParallelLine(
            start = add(start, scale(dir, bind.tStart)),
            end = add(start, scale(dir, bind.tEnd)),
        )
    }
}
