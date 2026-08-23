package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import kotlin.math.abs

data class HealResult(val walls: List<Wall>, val trimmed: Int)
data class SplitResult(val walls: List<Wall>, val splits: Int)

private fun startOf(w: Wall): Vec2 = vec(w.startX, w.startY)
private fun endOf(w: Wall): Vec2 = vec(w.endX, w.endY)

private data class CurvedFrame(val n: Vec2, val across: Double, val point: Vec2)

private fun curvedHostFrame(end: Vec2, host: Wall): CurvedFrame? {
    val hit = ArcWallGeometry.closestPoint(end, host)
    if (!(hit.t > 0.001 && hit.t < 0.999)) return null
    val n = vec(-hit.tangent.y, hit.tangent.x)
    return CurvedFrame(n, dot(sub(end, hit.point), n), hit.point)
}

private fun trimToCurvedHost(
    end: Vec2,
    other: Vec2,
    u: Vec2,
    axisLen: Double,
    host: Wall,
    toleranceCM: Double,
): Vec2? {
    val frame = curvedHostFrame(end, host) ?: return null
    val half = host.thickness / 2.0
    if (!(abs(frame.across) < half - toleranceCM)) return null
    val approach = dot(sub(other, frame.point), frame.n)
    if (abs(approach) <= 1e-9) return null
    val face = if (approach > 0) half else -half
    val rate = dot(u, frame.n)
    if (abs(rate) <= tJunctionParallelReject) return null
    val t = (frame.across - face) / rate
    if (!(t > 0 && t < axisLen - 1)) return null
    return sub(end, scale(u, t))
}

private fun projectToCurvedHost(
    end: Vec2,
    u: Vec2,
    host: Wall,
    toleranceCM: Double,
): Vec2? {
    val hit = ArcWallGeometry.closestPoint(end, host)
    if (hit.t < -0.05 || hit.t > 1.05) return null
    val n = vec(-hit.tangent.y, hit.tangent.x)
    val across = dot(sub(end, hit.point), n)
    val half = host.thickness / 2.0
    if (!(abs(across) > toleranceCM && abs(across) <= half + toleranceCM)) return null
    val rate = dot(u, n)
    if (abs(rate) <= tJunctionParallelReject) return null
    val t = -across / rate
    if (!(t > 0 && t <= half * 2 + toleranceCM)) return null
    return add(end, scale(u, t))
}

private fun trim(
    wallIndex: Int,
    atStart: Boolean,
    walls: List<Wall>,
    toleranceCM: Double,
): Vec2? {
    val stem = walls[wallIndex]
    if (atStart && stem.atStart != null) return null
    if (!atStart && stem.atEnd != null) return null

    val end = if (atStart) startOf(stem) else endOf(stem)
    val other = if (atStart) endOf(stem) else startOf(stem)
    val axis = sub(end, other)
    val axisLen = length(axis)
    if (axisLen <= 1e-6) return null
    val u = scale(axis, 1.0 / axisLen)

    for (j in walls.indices) {
        if (j == wallIndex) continue
        val host = walls[j]
        if (host.level != stem.level) continue
        if (ArcWallGeometry.isCurved(host)) {
            trimToCurvedHost(end, other, u, axisLen, host, toleranceCM)?.let { return it }
            continue
        }
        val ha = startOf(host)
        val hb = endOf(host)
        val hv = sub(hb, ha)
        val hLen = length(hv)
        if (hLen <= 1e-6) continue
        val hu = scale(hv, 1.0 / hLen)
        val n = vec(-hu.y, hu.x)

        val rel = sub(end, ha)
        val along = dot(rel, hu) / hLen
        val across = dot(rel, n)
        val half = host.thickness / 2.0

        if (!(along > 0.001 && along < 0.999)) continue
        if (!(abs(across) < half - toleranceCM)) continue

        val approach = dot(sub(other, ha), n)
        if (abs(approach) <= 1e-9) continue
        val face = if (approach > 0) half else -half

        val rate = dot(u, n)
        if (abs(rate) <= tJunctionParallelReject) continue
        val t = (across - face) / rate
        if (!(t > 0 && t < axisLen - 1)) continue
        return sub(end, scale(u, t))
    }
    return null
}

private fun project(
    wallIndex: Int,
    atStart: Boolean,
    walls: List<Wall>,
    toleranceCM: Double,
): Vec2? {
    val stem = walls[wallIndex]
    if (atStart && stem.atStart != null) return null
    if (!atStart && stem.atEnd != null) return null

    val end = if (atStart) startOf(stem) else endOf(stem)
    val other = if (atStart) endOf(stem) else startOf(stem)
    val axis = sub(end, other)
    val axisLen = length(axis)
    if (axisLen <= 1e-6) return null
    val u = scale(axis, 1.0 / axisLen)

    for (j in walls.indices) {
        if (j == wallIndex) continue
        val host = walls[j]
        if (host.level != stem.level) continue
        if (ArcWallGeometry.isCurved(host)) {
            projectToCurvedHost(end, u, host, toleranceCM)?.let { return it }
            continue
        }
        val ha = startOf(host)
        val hb = endOf(host)
        val hv = sub(hb, ha)
        val hLen = length(hv)
        if (hLen <= 1e-6) continue
        val hu = scale(hv, 1.0 / hLen)
        val n = vec(-hu.y, hu.x)

        val rel = sub(end, ha)
        val along = dot(rel, hu) / hLen
        val across = dot(rel, n)
        val half = host.thickness / 2.0

        if (along < -0.05 || along > 1.05) continue
        if (!(abs(across) > toleranceCM && abs(across) <= half + toleranceCM)) continue

        val rate = dot(u, n)
        if (abs(rate) <= tJunctionParallelReject) continue
        val t = -across / rate
        if (!(t > 0 && t <= half * 2 + toleranceCM)) continue
        return add(end, scale(u, t))
    }
    return null
}

private fun nextTee(walls: List<Wall>, toleranceCM: Double): Pair<String, Double>? {
    for (i in walls.indices) {
        val stem = walls[i]
        for (atStart in listOf(true, false)) {
            if (atStart && stem.atStart != null) continue
            if (!atStart && stem.atEnd != null) continue
            val tip = if (atStart) startOf(stem) else endOf(stem)
            for (j in walls.indices) {
                if (j == i) continue
                val host = walls[j]
                if (host.level != stem.level) continue
                if (ArcWallGeometry.isCurved(host)) continue
                val ha = startOf(host)
                val hb = endOf(host)
                val hv = sub(hb, ha)
                val hLen = length(hv)
                if (hLen <= 1e-6) continue
                val hu = scale(hv, 1.0 / hLen)
                val n = vec(-hu.y, hu.x)
                val rel = sub(tip, ha)
                val across = dot(rel, n)
                if (abs(across) > host.thickness / 2.0 + toleranceCM) continue
                val other = if (atStart) endOf(stem) else startOf(stem)
                val axis = sub(tip, other)
                val axisLen = length(axis)
                if (axisLen <= 1e-6) continue
                val u = scale(axis, 1.0 / axisLen)
                val rate = dot(u, n)
                var along = dot(rel, hu) / hLen
                if (abs(across) > 1e-9 && abs(rate) > tJunctionParallelReject) {
                    val onCentre = add(tip, scale(u, -across / rate))
                    along = dot(sub(onCentre, ha), hu) / hLen
                }
                if (!(along > 0.02 && along < 0.98)) continue
                return host.id to along
            }
        }
    }
    return null
}

object WallTJunction {
    fun heal(walls: List<Wall>, toleranceCM: Double = 1.0): HealResult {
        val out = walls.toMutableList()
        var trimmed = 0
        for (i in walls.indices) {
            for (atStart in listOf(true, false)) {
                val move = trim(i, atStart, out, toleranceCM) ?: continue
                out[i] = if (atStart) {
                    out[i].copy(startX = move.x, startY = move.y)
                } else {
                    out[i].copy(endX = move.x, endY = move.y)
                }
                trimmed += 1
            }
        }
        return HealResult(out, trimmed)
    }

    fun splitHostsAtTees(walls: List<Wall>, toleranceCM: Double = 1.0): SplitResult {
        var current = walls.toList()
        var splits = 0
        for (n in 0 until 64) {
            val cut = nextTee(current, toleranceCM) ?: break
            val before = current.size
            current = WallMutation.splitWall(cut.first, cut.second, current)
            if (current.size <= before) break
            splits += 1
        }
        return SplitResult(current, splits)
    }

    /** Inverse of heal, for room detection only. */
    fun centrelineProjected(walls: List<Wall>, toleranceCM: Double = 1.0): List<Wall> {
        val out = walls.toMutableList()
        for (i in walls.indices) {
            for (atStart in listOf(true, false)) {
                val p = project(i, atStart, walls, toleranceCM) ?: continue
                out[i] = if (atStart) {
                    out[i].copy(startX = p.x, startY = p.y)
                } else {
                    out[i].copy(endX = p.x, endY = p.y)
                }
            }
        }
        return out
    }
}
