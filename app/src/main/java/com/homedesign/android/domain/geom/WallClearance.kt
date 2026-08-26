package com.homedesign.android.domain.geom

import com.homedesign.android.domain.catalog.StructureCatalog
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Wall
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class WallClearanceMove(val pieceID: String, val delta: Vec2)

data class WallClearanceResult(
    val furniture: List<HomePieceOfFurniture>,
    val moves: List<WallClearanceMove>,
)

/**
 * Furniture that touches a wall belongs on the FACE — never across
 * its centreline. Doc 08 §11 / 03 §8.
 */
object WallClearance {
    val marginCM: Double = wallClearanceMarginCM

    /** Built-in structure ids sit IN walls on purpose. */
    private fun isStructure(catalogID: String?): Boolean =
        StructureCatalog.isStructure(catalogID)

    /**
     * Conservative mount class from the display name. Wall keywords run
     * first so "wall spotlight" stays a wall fixture.
     */
    private fun mountForName(name: String?): String {
        val n = (name ?: "").lowercase()
        val wallKeywords = listOf(
            "wall light", "wall lamp", "sconce", "applique", "wall-mounted",
            "wall spot", "painting light", "mirror",
        )
        if (wallKeywords.any { n.contains(it) }) return "wall"
        val ceilingKeywords = listOf(
            "ceiling", "chandelier", "pendant", "hanging", "lustre",
            "spot", "fluorescent", "track light", "neon",
        )
        if (ceilingKeywords.any { n.contains(it) }) return "ceiling"
        return "floor"
    }

    /** Four corners of the rotated plan rectangle about an explicit centre. */
    private fun corners(piece: HomePieceOfFurniture, centre: Vec2): List<Vec2> {
        val hw = piece.width / 2.0
        val hd = piece.depth / 2.0
        val ca = cos(piece.angle)
        val sa = sin(piece.angle)
        val local = listOf(
            vec(-hw, -hd),
            vec(hw, -hd),
            vec(hw, hd),
            vec(-hw, hd),
        )
        return local.map { p ->
            add(centre, vec(p.x * ca - p.y * sa, p.x * sa + p.y * ca))
        }
    }

    /**
     * Pure: returns a new furniture array. Pieces overlapping a wall
     * body by up to half-thickness + margin on one side only are pushed
     * so the near edge sits on the face. Through-wall / structure /
     * elevated / wall-mounted pieces are left alone.
     */
    fun resolve(
        furniture: List<HomePieceOfFurniture>,
        walls: List<Wall>,
        marginCM: Double = wallClearanceMarginCM,
    ): WallClearanceResult {
        if (walls.isEmpty()) {
            return WallClearanceResult(furniture.map { it.copy() }, emptyList())
        }
        val out = furniture.map { it.copy() }.toMutableList()
        val moves = mutableListOf<WallClearanceMove>()

        for (i in out.indices) {
            val piece = out[i]
            if (isStructure(piece.catalogID)) continue
            if (mountForName(piece.name) != "floor") continue
            if (piece.elevation > 1) continue

            var centre = vec(piece.x, piece.y)
            var moved = vec(0.0, 0.0)

            for (wall in walls) {
                if (wall.level != piece.level) continue
                val a = vec(wall.startX, wall.startY)
                val span = sub(vec(wall.endX, wall.endY), a)
                val len = length(span)
                if (len <= 1e-9) continue
                val dir = scale(span, 1.0 / len)
                val n = vec(-dir.y, dir.x)
                val half = wall.thickness / 2.0

                val c = corners(piece, centre)
                var dMin = Double.POSITIVE_INFINITY
                var dMax = Double.NEGATIVE_INFINITY
                var nMin = Double.POSITIVE_INFINITY
                var nMax = Double.NEGATIVE_INFINITY
                for (p in c) {
                    val rel = sub(p, a)
                    val pd = dot(rel, dir)
                    val pn = dot(rel, n)
                    dMin = min(dMin, pd)
                    dMax = max(dMax, pd)
                    nMin = min(nMin, pn)
                    nMax = max(nMax, pn)
                }
                if (!(dMax > 0 && dMin < len)) continue

                val centreN = dot(sub(centre, a), n)
                if (centreN >= 0) {
                    val depth = half - nMin
                    if (depth > 0 && nMin >= -half - marginCM) {
                        val push = scale(n, depth)
                        centre = add(centre, push)
                        moved = add(moved, push)
                    }
                } else {
                    val depth = nMax - -half
                    if (depth > 0 && nMax <= half + marginCM) {
                        val push = scale(n, depth)
                        centre = sub(centre, push)
                        moved = sub(moved, push)
                    }
                }
            }

            if (length(moved) > 1e-9) {
                out[i] = out[i].copy(x = centre.x, y = centre.y)
                moves.add(WallClearanceMove(piece.id, moved))
            }
        }

        return WallClearanceResult(out, moves)
    }
}
