package com.homedesign.android.domain.export

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class FlattenedPolyline(
    val points: List<Pair<Double, Double>>,
    val closed: Boolean,
)

/**
 * Flatten SVG path `d` for furniture symbol art (web `pathFlatten.ts`).
 */
object PathFlatten {
    private data class Affine(
        val a: Double = 1.0,
        val b: Double = 0.0,
        val c: Double = 0.0,
        val d: Double = 1.0,
        val e: Double = 0.0,
        val f: Double = 0.0,
    )

    private val IDENTITY = Affine()

    private fun mul(m: Affine, n: Affine) = Affine(
        a = m.a * n.a + m.c * n.b,
        b = m.b * n.a + m.d * n.b,
        c = m.a * n.c + m.c * n.d,
        d = m.b * n.c + m.d * n.d,
        e = m.a * n.e + m.c * n.f + m.e,
        f = m.b * n.e + m.d * n.f + m.f,
    )

    private fun apply(m: Affine, x: Double, y: Double) =
        (m.a * x + m.c * y + m.e) to (m.b * x + m.d * y + m.f)

    private fun parseNumbers(s: String): List<Double> {
        val out = ArrayList<Double>()
        val re = Regex("""[+-]?(?:\d*\.\d+|\d+\.?)(?:[eE][+-]?\d+)?""")
        for (m in re.findAll(s)) out.add(m.value.toDouble())
        return out
    }

    private fun parseTransform(spec: String?): Affine {
        if (spec.isNullOrBlank()) return IDENTITY
        var m = IDENTITY
        val re = Regex("""(matrix|translate|scale|rotate|skewX|skewY)\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
        for (match in re.findAll(spec)) {
            val kind = match.groupValues[1].lowercase()
            val args = parseNumbers(match.groupValues[2])
            val t = when (kind) {
                "matrix" -> Affine(
                    args.getOrElse(0) { 1.0 },
                    args.getOrElse(1) { 0.0 },
                    args.getOrElse(2) { 0.0 },
                    args.getOrElse(3) { 1.0 },
                    args.getOrElse(4) { 0.0 },
                    args.getOrElse(5) { 0.0 },
                )
                "translate" -> Affine(e = args.getOrElse(0) { 0.0 }, f = args.getOrElse(1) { 0.0 })
                "scale" -> {
                    val sx = args.getOrElse(0) { 1.0 }
                    val sy = args.getOrElse(1) { sx }
                    Affine(a = sx, d = sy)
                }
                "rotate" -> {
                    val deg = (args.getOrElse(0) { 0.0 } * PI) / 180.0
                    val c = cos(deg)
                    val s = sin(deg)
                    val cx = args.getOrElse(1) { 0.0 }
                    val cy = args.getOrElse(2) { 0.0 }
                    val toOrigin = Affine(e = -cx, f = -cy)
                    val rot = Affine(a = c, b = s, c = -s, d = c)
                    val fromOrigin = Affine(e = cx, f = cy)
                    mul(mul(fromOrigin, rot), toOrigin)
                }
                "skewx" -> Affine(c = tan((args.getOrElse(0) { 0.0 } * PI) / 180.0))
                "skewy" -> Affine(b = tan((args.getOrElse(0) { 0.0 } * PI) / 180.0))
                else -> IDENTITY
            }
            m = mul(m, t)
        }
        return m
    }

    private data class Cmd(val cmd: String, val args: List<Double>)

    private fun parseCommands(d: String): List<Cmd> {
        val cmds = ArrayList<Cmd>()
        var i = 0
        var cmd = ""
        val args = ArrayList<Double>()
        fun flush() {
            if (cmd.isNotEmpty()) cmds.add(Cmd(cmd, args.toList()))
            cmd = ""
            args.clear()
        }
        val numRe = Regex("""[+-]?(?:\d*\.\d+|\d+\.?)(?:[eE][+-]?\d+)?""")
        while (i < d.length) {
            val ch = d[i]
            when {
                ch in "MmLlHhVvCcSsQqTtAaZz" -> {
                    flush()
                    cmd = ch.toString()
                    i += 1
                }
                ch == ',' || ch.isWhitespace() -> i += 1
                else -> {
                    val m = numRe.find(d, i)
                    if (m == null || m.range.first != i) {
                        i += 1
                    } else {
                        args.add(m.value.toDouble())
                        i = m.range.last + 1
                    }
                }
            }
        }
        flush()
        return cmds
    }

    private fun quadPoint(
        t: Double,
        from: Pair<Double, Double>,
        c: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): Pair<Double, Double> {
        val u = 1 - t
        return (u * u * from.first + 2 * u * t * c.first + t * t * to.first) to
            (u * u * from.second + 2 * u * t * c.second + t * t * to.second)
    }

    private fun cubicPoint(
        t: Double,
        from: Pair<Double, Double>,
        c1: Pair<Double, Double>,
        c2: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): Pair<Double, Double> {
        val u = 1 - t
        return (
            u * u * u * from.first + 3 * u * u * t * c1.first + 3 * u * t * t * c2.first + t * t * t * to.first
            ) to (
            u * u * u * from.second + 3 * u * u * t * c1.second + 3 * u * t * t * c2.second + t * t * t * to.second
            )
    }

    private fun sampleArc(
        from: Pair<Double, Double>,
        rxIn: Double,
        ryIn: Double,
        phiDeg: Double,
        large: Boolean,
        sweep: Boolean,
        to: Pair<Double, Double>,
    ): List<Pair<Double, Double>> {
        if (from.first == to.first && from.second == to.second) return emptyList()
        var rx = kotlin.math.abs(rxIn)
        var ry = kotlin.math.abs(ryIn)
        if (rx < 1e-12 || ry < 1e-12) return listOf(to)
        val phi = (phiDeg * PI) / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (from.first - to.first) / 2
        val dy = (from.second - to.second) / 2
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) {
            val s = sqrt(lambda)
            rx *= s
            ry *= s
        }
        val rx2 = rx * rx
        val ry2 = ry * ry
        val x1p2 = x1p * x1p
        val y1p2 = y1p * y1p
        var radicand = (rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2) / (rx2 * y1p2 + ry2 * x1p2)
        if (radicand < 0) radicand = 0.0
        val sign = if (large == sweep) -1.0 else 1.0
        val coef = sign * sqrt(radicand)
        val cxp = (coef * rx * y1p) / ry
        val cyp = (coef * -ry * x1p) / rx
        val cx = cosPhi * cxp - sinPhi * cyp + (from.first + to.first) / 2
        val cy = sinPhi * cxp + cosPhi * cyp + (from.second + to.second) / 2

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = hypot(ux, uy) * hypot(vx, vy)
            var a = acos(max(-1.0, min(1.0, dot / (if (len == 0.0) 1.0 else len))))
            if (ux * vy - uy * vx < 0) a = -a
            return a
        }
        val start = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        val steps = max(8, min(64, ceil((kotlin.math.abs(delta) / PI) * 10).toInt()))
        val out = ArrayList<Pair<Double, Double>>(steps)
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            val theta = start + delta * t
            out.add(
                (cosPhi * rx * cos(theta) - sinPhi * ry * sin(theta) + cx) to
                    (sinPhi * rx * cos(theta) + cosPhi * ry * sin(theta) + cy),
            )
        }
        return out
    }

    fun flattenPathData(d: String, transform: String? = null): List<FlattenedPolyline> {
        val xf = parseTransform(transform)
        val cmds = parseCommands(d)
        val out = ArrayList<FlattenedPolyline>()
        var current = ArrayList<Pair<Double, Double>>()
        var start = 0.0 to 0.0
        var pen = 0.0 to 0.0
        var lastC1: Pair<Double, Double>? = null
        var lastQ: Pair<Double, Double>? = null
        var prevCmd = ""

        fun map(p: Pair<Double, Double>) = apply(xf, p.first, p.second)

        fun finish(closed: Boolean) {
            if (current.size >= 2) out.add(FlattenedPolyline(current.toList(), closed))
            current = ArrayList()
        }

        fun lineTo(p: Pair<Double, Double>) {
            current.add(map(p))
            pen = p
            lastC1 = null
            lastQ = null
        }

        for ((cmd, args) in cmds) {
            val rel = cmd[0].isLowerCase()
            val c = cmd.uppercase()
            var i = 0
            fun take(n: Int): List<Double> {
                val slice = args.subList(i, min(args.size, i + n))
                i += n
                return slice
            }
            if (c == "Z") {
                finish(true)
                pen = start
                current = arrayListOf(map(start))
                lastC1 = null
                lastQ = null
                prevCmd = c
                continue
            }
            while (i < args.size) {
                when (c) {
                    "M" -> {
                        val xy = take(2)
                        if (xy.size < 2) break
                        finish(false)
                        val p = if (rel) (pen.first + xy[0]) to (pen.second + xy[1]) else xy[0] to xy[1]
                        start = p
                        pen = p
                        current = arrayListOf(map(p))
                        lastC1 = null
                        lastQ = null
                        while (i + 1 < args.size) {
                            val lxy = take(2)
                            if (lxy.size < 2) break
                            val lp = if (rel) (pen.first + lxy[0]) to (pen.second + lxy[1]) else lxy[0] to lxy[1]
                            lineTo(lp)
                        }
                    }
                    "L" -> {
                        val xy = take(2)
                        if (xy.size < 2) break
                        val p = if (rel) (pen.first + xy[0]) to (pen.second + xy[1]) else xy[0] to xy[1]
                        lineTo(p)
                    }
                    "H" -> {
                        val xs = take(1)
                        if (xs.isEmpty()) break
                        lineTo((if (rel) pen.first + xs[0] else xs[0]) to pen.second)
                    }
                    "V" -> {
                        val ys = take(1)
                        if (ys.isEmpty()) break
                        lineTo(pen.first to (if (rel) pen.second + ys[0] else ys[0]))
                    }
                    "C" -> {
                        val a = take(6)
                        if (a.size < 6) break
                        val c1 = if (rel) (pen.first + a[0]) to (pen.second + a[1]) else a[0] to a[1]
                        val c2 = if (rel) (pen.first + a[2]) to (pen.second + a[3]) else a[2] to a[3]
                        val to = if (rel) (pen.first + a[4]) to (pen.second + a[5]) else a[4] to a[5]
                        val from = pen
                        for (s in 1..10) current.add(map(cubicPoint(s / 10.0, from, c1, c2, to)))
                        pen = to
                        lastC1 = c2
                        lastQ = null
                    }
                    "S" -> {
                        val a = take(4)
                        if (a.size < 4) break
                        val c1 = if (prevCmd == "C" || prevCmd == "S") {
                            (2 * pen.first - (lastC1?.first ?: pen.first)) to
                                (2 * pen.second - (lastC1?.second ?: pen.second))
                        } else {
                            pen
                        }
                        val c2 = if (rel) (pen.first + a[0]) to (pen.second + a[1]) else a[0] to a[1]
                        val to = if (rel) (pen.first + a[2]) to (pen.second + a[3]) else a[2] to a[3]
                        val from = pen
                        for (s in 1..10) current.add(map(cubicPoint(s / 10.0, from, c1, c2, to)))
                        pen = to
                        lastC1 = c2
                        lastQ = null
                    }
                    "Q" -> {
                        val a = take(4)
                        if (a.size < 4) break
                        val cpt = if (rel) (pen.first + a[0]) to (pen.second + a[1]) else a[0] to a[1]
                        val to = if (rel) (pen.first + a[2]) to (pen.second + a[3]) else a[2] to a[3]
                        val from = pen
                        for (s in 1..8) current.add(map(quadPoint(s / 8.0, from, cpt, to)))
                        pen = to
                        lastQ = cpt
                        lastC1 = null
                    }
                    "T" -> {
                        val a = take(2)
                        if (a.size < 2) break
                        val cpt = if (prevCmd == "Q" || prevCmd == "T") {
                            (2 * pen.first - (lastQ?.first ?: pen.first)) to
                                (2 * pen.second - (lastQ?.second ?: pen.second))
                        } else {
                            pen
                        }
                        val to = if (rel) (pen.first + a[0]) to (pen.second + a[1]) else a[0] to a[1]
                        val from = pen
                        for (s in 1..8) current.add(map(quadPoint(s / 8.0, from, cpt, to)))
                        pen = to
                        lastQ = cpt
                        lastC1 = null
                    }
                    "A" -> {
                        val a = take(7)
                        if (a.size < 7) break
                        val to = if (rel) (pen.first + a[5]) to (pen.second + a[6]) else a[5] to a[6]
                        val samples = sampleArc(pen, a[0], a[1], a[2], a[3] != 0.0, a[4] != 0.0, to)
                        for (p in samples) current.add(map(p))
                        pen = to
                        lastC1 = null
                        lastQ = null
                    }
                    else -> break
                }
                prevCmd = c
            }
            prevCmd = c
        }
        finish(false)
        return out
    }
}
