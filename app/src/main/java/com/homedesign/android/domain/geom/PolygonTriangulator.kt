package com.homedesign.android.domain.geom

/**
 * Ear-clip triangulation for simple polygons, plus a bridge-merge path
 * that punches interior holes (staircase floor openings).
 *
 * Triangles keep the outer ring's winding. Degenerate input falls back
 * to a fan of the leftover ring rather than throwing.
 */
object PolygonTriangulator {

    fun triangulate(polygon: List<Vec2>): List<Int> =
        triangulateRing(polygon, List(polygon.size) { it })

    /**
     * Outer ring plus interior holes. Positions are outer first, then each
     * hole (wound opposite the outer). Rightmost hole is bridged first so
     * later bridges cannot cross earlier ones.
     */
    fun triangulate(outer: List<Vec2>, holes: List<List<Vec2>>): Pair<List<Vec2>, List<Int>> {
        val usableHoles = holes.filter { it.size >= 3 }
        if (usableHoles.isEmpty()) return outer to triangulate(outer)
        if (outer.size < 3) return outer to emptyList()

        val positions = ArrayList<Vec2>(outer.size + usableHoles.sumOf { it.size })
        positions.addAll(outer)
        val outerCcw = signedArea(outer) > 0
        val holeRings = ArrayList<List<Int>>(usableHoles.size)
        for (hole in usableHoles) {
            val sameWinding = (signedArea(hole) > 0) == outerCcw
            val ordered = if (sameWinding) hole.asReversed() else hole
            val base = positions.size
            positions.addAll(ordered)
            holeRings.add(List(ordered.size) { base + it })
        }

        val ring = ArrayList<Int>(outer.size)
        for (i in outer.indices) ring.add(i)
        val byMaxX = holeRings.sortedByDescending { hole ->
            hole.maxOf { positions[it].x }
        }
        for (hole in byMaxX) {
            val hPos = hole.indices.maxBy { positions[hole[it]].x }
            val hVertex = hole[hPos]
            val hp = positions[hVertex]
            var best = -1
            var bestD = Double.POSITIVE_INFINITY
            for ((i, r) in ring.withIndex()) {
                if (positions[r].x < hp.x) continue
                val d = distSq(positions[r], hp)
                if (d < bestD) {
                    bestD = d
                    best = i
                }
            }
            if (best < 0) {
                for ((i, r) in ring.withIndex()) {
                    val d = distSq(positions[r], hp)
                    if (d < bestD) {
                        bestD = d
                        best = i
                    }
                }
            }
            if (best < 0) continue
            val rotated = hole.drop(hPos) + hole.take(hPos)
            val bridgeTarget = ring[best]
            ring.addAll(best + 1, rotated + listOf(hVertex, bridgeTarget))
        }
        return positions to triangulateRing(positions, ring)
    }

    fun signedArea(poly: List<Vec2>): Double {
        var area = 0.0
        val n = poly.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += poly[i].x * poly[j].y - poly[j].x * poly[i].y
        }
        return area
    }

    private fun triangulateRing(polygon: List<Vec2>, startRing: List<Int>): List<Int> {
        val n = startRing.size
        if (n < 3 || polygon.size < 3) return emptyList()
        if (n == 3) return startRing

        val ccw = signedArea(startRing.map { polygon[it] }) > 0
        val remaining = ArrayList(startRing)
        val result = ArrayList<Int>((n - 2) * 3)
        var stalls = 0
        while (remaining.size > 3) {
            val m = remaining.size
            var clipped = false
            for (i in 0 until m) {
                val pi = remaining[(i + m - 1) % m]
                val ci = remaining[i]
                val ni = remaining[(i + 1) % m]
                if (isEar(polygon, pi, ci, ni, remaining, ccw)) {
                    result.add(pi)
                    result.add(ci)
                    result.add(ni)
                    remaining.removeAt(i)
                    clipped = true
                    break
                }
            }
            if (!clipped) {
                stalls += 1
                if (stalls > 1) break
            } else {
                stalls = 0
            }
        }
        when {
            remaining.size == 3 -> {
                result.addAll(remaining)
            }
            remaining.size > 3 -> {
                for (i in 1 until remaining.size - 1) {
                    result.add(remaining[0])
                    result.add(remaining[i])
                    result.add(remaining[i + 1])
                }
            }
        }
        return result
    }

    private fun isEar(
        poly: List<Vec2>,
        prev: Int,
        curr: Int,
        next: Int,
        ring: List<Int>,
        ccw: Boolean,
    ): Boolean {
        val a = poly[prev]
        val b = poly[curr]
        val c = poly[next]
        val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        if (ccw) {
            if (cross <= 0) return false
        } else {
            if (cross >= 0) return false
        }
        for (idx in ring) {
            if (idx == prev || idx == curr || idx == next) continue
            if (pointInTriangle(poly[idx], a, b, c)) return false
        }
        return true
    }

    private fun pointInTriangle(p: Vec2, a: Vec2, b: Vec2, c: Vec2): Boolean {
        val d1 = edgeSign(p, a, b)
        val d2 = edgeSign(p, b, c)
        val d3 = edgeSign(p, c, a)
        val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
        val hasPos = d1 > 0 || d2 > 0 || d3 > 0
        return !(hasNeg && hasPos)
    }

    private fun edgeSign(p: Vec2, a: Vec2, b: Vec2): Double =
        (p.x - b.x) * (a.y - b.y) - (a.x - b.x) * (p.y - b.y)
}
