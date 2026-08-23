package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallCurveProfile
import com.homedesign.android.domain.model.WallSpan
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

const val BOW_STRAIGHT_EPSILON = 0.00125
val MAX_ABS_EXTENT = PI * 1.65
val MAX_ABS_BOW = tan(MAX_ABS_EXTENT / 4.0)

fun clampExtent(extent: Double): Double =
    extent.coerceIn(-MAX_ABS_EXTENT, MAX_ABS_EXTENT)

fun bowFromExtent(extent: Double): Double {
    if (abs(extent) <= curveStraightEpsilonRad) return 0.0
    return tan(clampExtent(extent) / 4.0)
}

fun extentFromBow(bow: Double): Double {
    if (abs(bow) <= BOW_STRAIGHT_EPSILON) return 0.0
    return clampExtent(4.0 * atan(bow))
}

fun spanBow(span: WallSpan): Double = when (span) {
    is WallSpan.Straight -> 0.0
    is WallSpan.Arc -> when {
        span.bow != null -> span.bow
        span.extent != null -> bowFromExtent(span.extent)
        else -> 0.0
    }
}

fun isStraightSpan(span: WallSpan): Boolean =
    abs(spanBow(span)) <= BOW_STRAIGHT_EPSILON

fun isAllStraight(profile: WallCurveProfile): Boolean =
    profile.spans.all(::isStraightSpan)

fun spanParams(profile: WallCurveProfile): List<Double> =
    listOf(0.0) + (profile.breaks ?: emptyList()) + listOf(1.0)

fun parseProfile(raw: WallCurveProfile?): WallCurveProfile? {
    if (raw == null) return null
    val spans = raw.spans.toMutableList()
    if (spans.isEmpty()) return null
    val breaks = (raw.breaks ?: emptyList())
        .filter { it > 0.0 && it < 1.0 }
        .sorted()
        .toMutableList()
    while (spans.size < breaks.size + 1) spans.add(WallSpan.Straight)
    if (spans.size > breaks.size + 1) {
        while (spans.size > breaks.size + 1) spans.removeAt(spans.lastIndex)
    }
    return WallCurveProfile(breaks = breaks, spans = spans)
}

fun effectiveProfile(wall: Wall): WallCurveProfile {
    val parsed = parseProfile(wall.curveProfile)
    if (parsed != null && parsed.spans.isNotEmpty()) return parsed
    val extent = wall.arcExtent
    if (extent != null && abs(extent) > curveStraightEpsilonRad) {
        return WallCurveProfile(
            breaks = emptyList(),
            spans = listOf(WallSpan.Arc(bow = bowFromExtent(extent))),
        )
    }
    return WallCurveProfile(breaks = emptyList(), spans = listOf(WallSpan.Straight))
}

fun materialiseProfile(wall: Wall, profile: WallCurveProfile): Wall {
    if ((profile.breaks?.size ?: 0) == 0 && isAllStraight(profile)) {
        return wall.copy(arcExtent = null, curveProfile = null)
    }
    return wall.copy(
        arcExtent = null,
        curveProfile = WallCurveProfile(breaks = profile.breaks ?: emptyList(), spans = profile.spans),
    )
}
