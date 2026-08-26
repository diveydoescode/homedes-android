package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val BOW_STRAIGHT = 0.00125
private val MAX_ABS_EXTENT_LOCAL = PI * 1.65
private val SAMPLE_RAD = (curveSampleDeg * PI) / 180.0

private fun clampExtentLocal(extent: Double): Double =
    extent.coerceIn(-MAX_ABS_EXTENT_LOCAL, MAX_ABS_EXTENT_LOCAL)

private fun chordOf(wall: Wall): Pair<Vec2, Vec2> =
    vec(wall.startX, wall.startY) to vec(wall.endX, wall.endY)

private fun signedExtent(wall: Wall): Double {
    val extent = wall.arcExtent ?: return 0.0
    if (abs(extent) <= curveStraightEpsilonRad) return 0.0
    return clampExtentLocal(extent)
}

private fun bowFromExtentLocal(extent: Double): Double {
    if (abs(extent) <= curveStraightEpsilonRad) return 0.0
    return kotlin.math.tan(clampExtentLocal(extent) / 4.0)
}

/** Sample one circular arc span. Inclusive of both endpoints. */
fun arcSpanSamples(start: Vec2, end: Vec2, bow: Double): List<Vec2> {
    if (abs(bow) <= BOW_STRAIGHT) return listOf(start, end)
    val chord = sub(end, start)
    val chordLen = length(chord)
    if (chordLen <= 1e-9) return listOf(start, end)

    val extent = clampExtentLocal(4.0 * kotlin.math.atan(bow))
    val radius = chordLen / 2.0 / sin(abs(extent) / 2.0)
    val mid = scale(add(start, end), 0.5)
    val dir = scale(chord, 1.0 / chordLen)
    val perp = vec(dir.y, -dir.x)
    val centreOffset = radius * cos(abs(extent) / 2.0)
    val centre = if (extent > 0) {
        add(mid, scale(perp, centreOffset))
    } else {
        sub(mid, scale(perp, centreOffset))
    }

    val a0 = atan2(start.y - centre.y, start.x - centre.x)
    val a1 = atan2(end.y - centre.y, end.x - centre.x)
    var sweep = a1 - a0
    while (sweep > PI * 2) sweep -= PI * 2
    while (sweep < -PI * 2) sweep += PI * 2
    val alt = if (sweep > 0) sweep - 2 * PI else sweep + 2 * PI
    val chosen =
        if (abs(abs(sweep) - abs(extent)) < abs(abs(alt) - abs(extent))) sweep else alt

    val segments = max(4, ceil(abs(chosen) / SAMPLE_RAD - 1e-9).toInt())
    val points = MutableList(segments + 1) { i ->
        val angle = a0 + (chosen * i) / segments
        vec(centre.x + radius * cos(angle), centre.y + radius * sin(angle))
    }
    points[0] = start
    points[segments] = end
    return points
}

private fun bellyPoint(start: Vec2, end: Vec2, bow: Double): Vec2 {
    val chord = sub(end, start)
    val len = length(chord)
    val mid = scale(add(start, end), 0.5)
    if (len <= 1e-9 || abs(bow) <= BOW_STRAIGHT) return mid
    val dir = scale(chord, 1.0 / len)
    val perp = vec(dir.y, -dir.x)
    return sub(mid, scale(perp, (len / 2.0) * bow))
}

object ArcWallGeometry {
    val straightEpsilon: Double = curveStraightEpsilonRad
    val maxAbsExtent: Double = MAX_ABS_EXTENT_LOCAL
    val bowStraightEpsilon: Double = BOW_STRAIGHT

    fun isCurved(wall: Wall): Boolean = !isAllStraight(effectiveProfile(wall))

    /**
     * Single-span circular bulge for DXF polylines when [Wall.curveProfile] is null.
     * Multi-span profiles return null so exporters sample the footprint instead.
     */
    fun singleBow(wall: Wall): Double? {
        if (wall.curveProfile != null) return null
        val extent = signedExtent(wall)
        if (extent == 0.0) return null
        return bowFromExtentLocal(extent)
    }

    /** Inclusive sampled centreline. Straight → [start, end]. */
    fun centerline(wall: Wall): List<Vec2> {
        val (start, end) = chordOf(wall)
        val parsed = parseProfile(wall.curveProfile)
        if (parsed == null || parsed.spans.isEmpty()) {
            val extent = signedExtent(wall)
            if (extent == 0.0) return listOf(start, end)
            return arcSpanSamples(start, end, bowFromExtentLocal(extent))
        }
        val profile = parsed
        if (profile.spans.size <= 1 && isAllStraight(profile)) return listOf(start, end)
        val params = spanParams(profile)
        val line = mutableListOf(start)
        for (i in profile.spans.indices) {
            val t0 = params[i]
            val t1 = params[i + 1]
            val p0 = add(start, scale(sub(end, start), t0))
            val p1 = add(start, scale(sub(end, start), t1))
            val span = profile.spans[i]
            val samples = if (isStraightSpan(span)) {
                listOf(p0, p1)
            } else {
                arcSpanSamples(p0, p1, spanBow(span))
            }
            line.addAll(samples.drop(1))
        }
        return line
    }

    fun arcPoints(wall: Wall, @Suppress("UNUSED_PARAMETER") maxStepDeg: Double = curveSampleDeg): List<Vec2> =
        centerline(wall)

    fun footprint(wall: Wall): List<Vec2> {
        val line = centerline(wall)
        if (line.size < 2) return emptyList()
        val half = wall.thickness / 2.0
        val left = mutableListOf<Vec2>()
        val right = mutableListOf<Vec2>()
        for (i in line.indices) {
            val prev = line[max(0, i - 1)]
            val next = line[min(line.lastIndex, i + 1)]
            val t = sub(next, prev)
            val len = length(t)
            if (len <= 1e-12) continue
            val dir = scale(t, 1.0 / len)
            val leftN = vec(dir.y, -dir.x)
            val p = line[i]
            left.add(add(p, scale(leftN, half)))
            right.add(sub(p, scale(leftN, half)))
        }
        return left + right.asReversed()
    }

    fun arcLength(wall: Wall): Double {
        val line = centerline(wall)
        var total = 0.0
        for (i in 1 until line.size) total += dist(line[i], line[i - 1])
        return total
    }

    fun extent(start: Vec2, end: Vec2, through: Vec2): Double {
        val chord = sub(end, start)
        val len = length(chord)
        if (len <= 1e-9) return 0.0
        val dir = scale(chord, 1.0 / len)
        val perp = vec(dir.y, -dir.x)
        val mid = scale(add(start, end), 0.5)
        val s = dot(sub(through, mid), perp)
        val raw = 4.0 * atan2(-s, len / 2.0)
        if (abs(raw) < curveStraightEpsilonRad) return 0.0
        return clampExtentLocal(raw)
    }

    fun bow(start: Vec2, end: Vec2, through: Vec2): Double {
        val e = extent(start, end, through)
        if (abs(e) <= curveStraightEpsilonRad) return 0.0
        return kotlin.math.tan(e / 4.0)
    }

    fun bowFromHandle(wall: Wall, handlePoint: Vec2): Double =
        extent(vec(wall.startX, wall.startY), vec(wall.endX, wall.endY), handlePoint)

    fun handlePosition(wall: Wall): Vec2 {
        val (start, end) = chordOf(wall)
        return bellyPoint(start, end, bowFromExtentLocal(signedExtent(wall)))
    }

    /**
     * Where the canvas draws / hit-tests the bow handle.
     * Straight walls offset the rest position off the body so a
     * midpoint body-drag stays wallMove.
     */
    fun handleHitPosition(wall: Wall, totalScale: Double): Vec2 {
        val belly = handlePosition(wall)
        if (isCurved(wall)) return belly
        val (start, end) = chordOf(wall)
        val chord = sub(end, start)
        val len = length(chord)
        if (len <= 1e-9) return belly
        val dir = scale(chord, 1.0 / len)
        val leftN = vec(dir.y, -dir.x)
        val pad = hitCurveHandlePx / max(totalScale, 0.001)
        return add(belly, scale(leftN, wall.thickness / 2.0 + pad))
    }

    fun spanHandlePosition(wall: Wall, spanIndex: Int): Vec2 {
        val (start, end) = chordOf(wall)
        val profile = effectiveProfile(wall)
        if (spanIndex < 0 || spanIndex >= profile.spans.size) {
            return scale(add(start, end), 0.5)
        }
        val params = spanParams(profile)
        val p0 = add(start, scale(sub(end, start), params[spanIndex]))
        val p1 = add(start, scale(sub(end, start), params[spanIndex + 1]))
        val span = profile.spans[spanIndex]
        if (isStraightSpan(span)) return scale(add(p0, p1), 0.5)
        return bellyPoint(p0, p1, spanBow(span))
    }

    data class BreakpointPos(val t: Double, val point: Vec2)

    fun breakpointPositions(wall: Wall): List<BreakpointPos> {
        val (start, end) = chordOf(wall)
        val profile = effectiveProfile(wall)
        return (profile.breaks ?: emptyList()).map { t ->
            BreakpointPos(t = t, point = add(start, scale(sub(end, start), t)))
        }
    }

    fun spanBowFromHandle(wall: Wall, spanIndex: Int, handlePoint: Vec2): Double {
        val (start, end) = chordOf(wall)
        val profile = effectiveProfile(wall)
        val params = spanParams(profile)
        val p0 = add(start, scale(sub(end, start), params.getOrElse(spanIndex) { 0.0 }))
        val p1 = add(start, scale(sub(end, start), params.getOrElse(spanIndex + 1) { 1.0 }))
        return bow(p0, p1, handlePoint)
    }

    fun distance(point: Vec2, wall: Wall): Double {
        val line = centerline(wall)
        var best = Double.POSITIVE_INFINITY
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val ab = sub(b, a)
            val lenSq = dot(ab, ab)
            val t = if (lenSq < 1e-12) 0.0 else max(0.0, min(1.0, dot(sub(point, a), ab) / lenSq))
            val proj = add(a, scale(ab, t))
            best = min(best, dist(point, proj))
        }
        return best
    }

    fun distanceToArc(point: Vec2, wall: Wall): Double = distance(point, wall)

    data class ClosestPoint(
        val t: Double,
        val point: Vec2,
        val distance: Double,
        val signed: Double,
        val tangent: Vec2,
    )

    fun closestPoint(point: Vec2, wall: Wall): ClosestPoint {
        val line = centerline(wall)
        val start = vec(wall.startX, wall.startY)
        val end = vec(wall.endX, wall.endY)
        val chord = sub(end, start)
        val fallbackT = if (length(chord) > 1e-9) scale(chord, 1.0 / length(chord)) else vec(1.0, 0.0)
        var best = ClosestPoint(
            t = 0.0,
            point = start,
            distance = Double.POSITIVE_INFINITY,
            signed = 0.0,
            tangent = fallbackT,
        )
        var total = 0.0
        for (i in 1 until line.size) total += dist(line[i], line[i - 1])
        if (total <= 1e-12) return best
        var cumulative = 0.0
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val ab = sub(b, a)
            val lenSq = dot(ab, ab)
            val segLen = length(ab)
            val u = if (lenSq < 1e-12) 0.0 else max(0.0, min(1.0, dot(sub(point, a), ab) / lenSq))
            val proj = add(a, scale(ab, u))
            val d = dist(point, proj)
            if (d < best.distance) {
                val tangent = if (segLen > 1e-12) scale(ab, 1.0 / segLen) else fallbackT
                val leftN = vec(tangent.y, -tangent.x)
                best = ClosestPoint(
                    t = (cumulative + segLen * u) / total,
                    point = proj,
                    distance = d,
                    signed = dot(sub(point, proj), leftN),
                    tangent = tangent,
                )
            }
            cumulative += segLen
        }
        return best
    }

    fun pointAt(wall: Wall, t: Double): Vec2 {
        val line = centerline(wall)
        if (line.isEmpty()) return vec(wall.startX, wall.startY)
        if (line.size == 1) return line[0]
        val target = t.coerceIn(0.0, 1.0) * arcLength(wall)
        var acc = 0.0
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val seg = dist(a, b)
            if (acc + seg >= target) {
                val u = if (seg <= 1e-12) 0.0 else (target - acc) / seg
                return add(a, scale(sub(b, a), u))
            }
            acc += seg
        }
        return line.last()
    }
}
