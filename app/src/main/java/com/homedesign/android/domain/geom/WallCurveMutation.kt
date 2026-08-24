package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallSpan
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.min
import kotlin.math.tan

private const val INSERT_EPSILON = 1e-4
private const val BREAK_GAP = 0.01

object WallCurveMutation {
    private fun updateWall(
        walls: List<Wall>,
        wallID: String,
        transform: (Wall) -> Wall,
    ): List<Wall> {
        var changed = false
        val next = walls.map { w ->
            if (w.id != wallID) w
            else {
                val out = transform(w)
                if (out !== w) changed = true
                out
            }
        }
        return if (changed) next else walls
    }

    private fun clampedBow(
        bow: Double,
        spanIndex: Int,
        profile: com.homedesign.android.domain.model.WallCurveProfile,
        wall: Wall,
    ): Double {
        val chordLen = dist(vec(wall.startX, wall.startY), vec(wall.endX, wall.endY))
        val params = spanParams(profile)
        val spanFrac = (params.getOrElse(spanIndex + 1) { 1.0 }) - (params.getOrElse(spanIndex) { 0.0 })
        val c = chordLen * spanFrac
        var maxBow = MAX_ABS_BOW
        if (c > 1e-6) {
            val rMin = (wall.thickness / 2.0) * 1.05
            val ratio = c / 2.0 / maxOf(rMin, 1e-6)
            if (ratio < 1.0) {
                val maxExtent = 2.0 * asin(min(1.0, ratio))
                maxBow = min(maxBow, abs(tan(maxExtent / 4.0)))
            }
        }
        return bow.coerceIn(-maxBow, maxBow)
    }

    private fun arcSpan(bow: Double): WallSpan =
        if (abs(bow) <= BOW_STRAIGHT_EPSILON) WallSpan.Straight else WallSpan.Arc(bow = bow)

    fun insertBreakpoint(
        walls: List<Wall>,
        wallID: String,
        t: Double,
        epsilon: Double = INSERT_EPSILON,
    ): List<Wall> = updateWall(walls, wallID) { wall ->
        if (!(t > epsilon && t < 1.0 - epsilon)) return@updateWall wall
        val profile = effectiveProfile(wall)
        val breaks = (profile.breaks ?: emptyList()).toMutableList()
        if (breaks.any { abs(it - t) <= epsilon }) return@updateWall wall
        val params = spanParams(profile)
        var spanIndex = profile.spans.lastIndex
        for (i in profile.spans.indices) {
            if (t < (params.getOrElse(i + 1) { 1.0 })) {
                spanIndex = i
                break
            }
        }
        val kind = profile.spans[spanIndex]
        breaks.add(spanIndex, t)
        val spans = profile.spans.toMutableList()
        spans.add(spanIndex, kind)
        materialiseProfile(wall, profile.copy(breaks = breaks, spans = spans))
    }

    fun deleteBreakpoint(walls: List<Wall>, wallID: String, breakIndex: Int): List<Wall> =
        updateWall(walls, wallID) { wall ->
            val profile = effectiveProfile(wall)
            val breaks = (profile.breaks ?: emptyList()).toMutableList()
            if (breakIndex < 0 || breakIndex >= breaks.size) return@updateWall wall
            breaks.removeAt(breakIndex)
            val spans = profile.spans.toMutableList()
            spans.removeAt(breakIndex + 1)
            materialiseProfile(wall, profile.copy(breaks = breaks, spans = spans))
        }

    fun setSpanBow(walls: List<Wall>, wallID: String, spanIndex: Int, bow: Double): List<Wall> =
        updateWall(walls, wallID) { wall ->
            val profile = effectiveProfile(wall)
            if (spanIndex < 0 || spanIndex >= profile.spans.size) return@updateWall wall
            val clamped = clampedBow(bow, spanIndex, profile, wall)
            val spans = profile.spans.toMutableList()
            spans[spanIndex] = arcSpan(clamped)
            materialiseProfile(wall, profile.copy(breaks = profile.breaks ?: emptyList(), spans = spans))
        }

    fun moveBreakpoint(walls: List<Wall>, wallID: String, breakIndex: Int, t: Double): List<Wall> =
        updateWall(walls, wallID) { wall ->
            val profile = effectiveProfile(wall)
            val breaks = (profile.breaks ?: emptyList()).toMutableList()
            if (breakIndex < 0 || breakIndex >= breaks.size) return@updateWall wall
            val lower = if (breakIndex > 0) breaks[breakIndex - 1] + BREAK_GAP else BREAK_GAP
            val upper =
                if (breakIndex < breaks.size - 1) breaks[breakIndex + 1] - BREAK_GAP else 1.0 - BREAK_GAP
            if (!(lower < upper)) return@updateWall wall
            breaks[breakIndex] = t.coerceIn(lower, upper)
            materialiseProfile(wall, profile.copy(breaks = breaks, spans = profile.spans))
        }

    fun chordT(wall: Wall, point: Vec2): Double {
        val dx = wall.endX - wall.startX
        val dy = wall.endY - wall.startY
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-18) return 0.0
        return ((point.x - wall.startX) * dx + (point.y - wall.startY) * dy) / len2
    }

    fun shouldMergeBreakpoint(wall: Wall, breakIndex: Int, rawT: Double): Boolean {
        val profile = effectiveProfile(wall)
        val breaks = profile.breaks ?: emptyList()
        if (breakIndex < 0 || breakIndex >= breaks.size) return false
        val left = if (breakIndex > 0) breaks[breakIndex - 1] else 0.0
        val right = if (breakIndex < breaks.size - 1) breaks[breakIndex + 1] else 1.0
        return rawT <= left + 0.02 || rawT >= right - 0.02
    }
}
