package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall

data class InferResult(
    val walls: List<Wall>,
    val joints: Int,
)

private data class End(
    val wallIndex: Int,
    val isStart: Boolean,
    val point: Vec2,
    val free: Boolean,
)

/**
 * Port of web `WallJoinInference.ts`.
 * Cluster endpoints on a grid of cell 2ε. Exactly two free ends of
 * different same-level walls within ε → write mutual atStart/atEnd.
 * Never overwrite. Never join 3+.
 */
object WallJoinInference {
    fun infer(
        walls: List<Wall>,
        epsilonCM: Double = joinEpsilonCM,
    ): InferResult {
        val out = walls.toMutableList()
        val ends = mutableListOf<End>()
        for (i in out.indices) {
            val wall = out[i]
            ends.add(
                End(
                    wallIndex = i,
                    isStart = true,
                    point = vec(wall.startX, wall.startY),
                    free = wall.atStart == null,
                ),
            )
            ends.add(
                End(
                    wallIndex = i,
                    isStart = false,
                    point = vec(wall.endX, wall.endY),
                    free = wall.atEnd == null,
                ),
            )
        }

        val cell = maxOf(epsilonCM, 0.001) * 2
        val grid = HashMap<String, MutableList<Int>>()
        fun cellKey(ix: Int, iy: Int) = "$ix,$iy"
        for (idx in ends.indices) {
            val e = ends[idx]
            val kx = roundTiesAway(e.point.x / cell).toInt()
            val ky = roundTiesAway(e.point.y / cell).toInt()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val key = cellKey(kx + dx, ky + dy)
                    grid.getOrPut(key) { mutableListOf() }.add(idx)
                }
            }
        }

        var joints = 0
        val consumed = HashSet<Int>()
        for (idx in ends.indices) {
            val e = ends[idx]
            if (!e.free || idx in consumed) continue
            val kx = roundTiesAway(e.point.x / cell).toInt()
            val ky = roundTiesAway(e.point.y / cell).toInt()
            val candidates = grid[cellKey(kx, ky)].orEmpty()
            val here = candidates.filter { other ->
                dist(ends[other].point, e.point) <= epsilonCM
            }
            if (here.size != 2) continue
            val a = ends[here[0]]
            val b = ends[here[1]]
            if (a.wallIndex == b.wallIndex || !a.free || !b.free) continue
            if (out[a.wallIndex].level != out[b.wallIndex].level) continue

            val aID = out[a.wallIndex].id
            val bID = out[b.wallIndex].id
            out[a.wallIndex] = if (a.isStart) {
                out[a.wallIndex].copy(atStart = bID)
            } else {
                out[a.wallIndex].copy(atEnd = bID)
            }
            out[b.wallIndex] = if (b.isStart) {
                out[b.wallIndex].copy(atStart = aID)
            } else {
                out[b.wallIndex].copy(atEnd = aID)
            }
            consumed.add(here[0])
            consumed.add(here[1])
            joints += 1
        }
        return InferResult(walls = out, joints = joints)
    }
}
