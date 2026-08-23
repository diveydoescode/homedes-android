package com.homedesign.android.domain.geom

import kotlin.math.hypot
import kotlin.math.round

/** Plan-cm 2D helpers. Origin top-left, +Y down. */
data class Vec2(val x: Double, val y: Double)

fun vec(x: Double, y: Double): Vec2 = Vec2(x, y)

fun add(a: Vec2, b: Vec2): Vec2 = Vec2(a.x + b.x, a.y + b.y)

fun sub(a: Vec2, b: Vec2): Vec2 = Vec2(a.x - b.x, a.y - b.y)

fun scale(a: Vec2, s: Double): Vec2 = Vec2(a.x * s, a.y * s)

fun dot(a: Vec2, b: Vec2): Double = a.x * b.x + a.y * b.y

fun lengthSq(a: Vec2): Double = a.x * a.x + a.y * a.y

fun length(a: Vec2): Double = hypot(a.x, a.y)

fun dist(a: Vec2, b: Vec2): Double = hypot(a.x - b.x, a.y - b.y)

fun distSq(a: Vec2, b: Vec2): Double {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

fun normalize(a: Vec2): Vec2 {
    val len = length(a)
    if (len < 1e-12) return Vec2(0.0, 0.0)
    return Vec2(a.x / len, a.y / len)
}

/** Swift `Double.rounded()` — nearest, ties away from zero. */
fun roundTiesAway(x: Double): Double =
    if (x >= 0) round(x) else -round(-x)
