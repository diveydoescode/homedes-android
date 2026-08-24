package com.homedesign.android.domain.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Port of web `FurnitureSymbols.ts` procedural path art for kinds without an
 * SVG asset. Local frame: −w/2…w/2 × −d/2…d/2, front +y.
 */
data class FurnitureSymbolStroke(val d: String)

data class FurnitureSymbolArt(
    val kind: FurnitureSymbolKind,
    val paths: List<FurnitureSymbolStroke>,
    val overhead: Boolean = false,
    /** True when art was authored at swapped w/d — apply rotate(90) when drawing. */
    val quarterTurn: Boolean = false,
)

object FurnitureSymbols {
    private const val PATH_CACHE_LIMIT = 512
    private val pathCache = LinkedHashMap<String, FurnitureSymbolArt>(64, 0.75f, true)

    fun isOverhead(kind: FurnitureSymbolKind): Boolean = kind == FurnitureSymbolKind.Chandelier

    /** Empty paths ⇒ caller draws the labelled outline / kind tint. */
    fun paths(kind: FurnitureSymbolKind, width: Double, depth: Double): FurnitureSymbolArt {
        if (kind == FurnitureSymbolKind.Generic ||
            width <= 1.0 ||
            depth <= 1.0 ||
            !width.isFinite() ||
            !depth.isFinite()
        ) {
            return FurnitureSymbolArt(kind, emptyList())
        }
        val key = "$kind|${round(width * 10)}|${round(depth * 10)}"
        pathCache[key]?.let { return it }
        var w = width
        var d = depth
        var quarterTurn = false
        if (FurnitureSymbolClassifier.needsQuarterTurn(kind, width, depth)) {
            w = depth
            d = width
            quarterTurn = true
        }
        val built = buildProcedural(kind, w, d).copy(quarterTurn = quarterTurn)
        if (pathCache.size >= PATH_CACHE_LIMIT) pathCache.clear()
        pathCache[key] = built
        return built
    }

    private fun buildProcedural(kind: FurnitureSymbolKind, w: Double, d: Double): FurnitureSymbolArt {
        val ds = when (kind) {
            FurnitureSymbolKind.Nightstand -> nightstand(w, d)
            FurnitureSymbolKind.Dresser -> dresser(w, d)
            FurnitureSymbolKind.Fridge -> fridge(w, d)
            FurnitureSymbolKind.Tv -> tv(w, d)
            FurnitureSymbolKind.Rug -> rug(w, d)
            FurnitureSymbolKind.Lamp -> lamp(w, d)
            FurnitureSymbolKind.Stairs -> stairs(w, d)
            FurnitureSymbolKind.SofaL -> sofaL(w, d)
            FurnitureSymbolKind.Chandelier -> chandelier(w, d)
            FurnitureSymbolKind.Mirror -> mirror(w, d)
            else -> emptyList()
        }
        return FurnitureSymbolArt(
            kind = kind,
            paths = ds.map { FurnitureSymbolStroke(it) },
            overhead = kind == FurnitureSymbolKind.Chandelier,
        )
    }

    private fun repeatCount(value: Double, minCount: Int, maxCount: Int = 64): Int {
        if (!value.isFinite()) return minCount
        return min(max(value.toInt().coerceAtLeast(minCount), minCount), maxCount)
    }

    private fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double): String {
        val rr = max(0.0, min(r, min(w, h) / 2.0))
        if (rr <= 0.05) return "M $x $y h $w v $h h ${-w} Z"
        return listOf(
            "M ${x + rr} $y",
            "H ${x + w - rr}",
            "A $rr $rr 0 0 1 ${x + w} ${y + rr}",
            "V ${y + h - rr}",
            "A $rr $rr 0 0 1 ${x + w - rr} ${y + h}",
            "H ${x + rr}",
            "A $rr $rr 0 0 1 $x ${y + h - rr}",
            "V ${y + rr}",
            "A $rr $rr 0 0 1 ${x + rr} $y",
            "Z",
        ).joinToString(" ")
    }

    private fun line(x0: Double, y0: Double, x1: Double, y1: Double): String =
        "M $x0 $y0 L $x1 $y1"

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): String =
        "M ${cx - rx} $cy a $rx $ry 0 1 0 ${rx * 2} 0 a $rx $ry 0 1 0 ${-rx * 2} 0"

    private fun nightstand(w: Double, d: Double): List<String> {
        val m = min(w, d)
        val pad = m * 0.16
        return listOf(
            roundRect(-w / 2, -d / 2, w, d, m * 0.14),
            roundRect(-w / 2 + pad, -d / 2 + pad, max(1.0, w - 2 * pad), max(1.0, d - 2 * pad), m * 0.06),
        )
    }

    private fun dresser(w: Double, d: Double): List<String> {
        val m = min(w, d)
        val out = mutableListOf(roundRect(-w / 2, -d / 2, w, d, m * 0.1))
        val pad = m * 0.14
        val cols = repeatCount(w / 55.0, 2)
        val g = m * 0.05
        val dw = (w - 2 * pad - (cols - 1) * g) / cols
        val dh = max(2.0, d - 2 * pad)
        if (dw <= 0) return out
        for (i in 0 until cols) {
            val dx = -w / 2 + pad + i * (dw + g)
            out.add(roundRect(dx, -d / 2 + pad, dw, dh, m * 0.04))
            val hr = max(1.0, m * 0.035)
            val hy = -d / 2 + pad + dh / 2
            out.add(ellipse(dx + dw / 2, hy, hr, hr))
        }
        return out
    }

    private fun fridge(w: Double, d: Double): List<String> {
        val m = min(w, d)
        val inset = w * 0.05
        val doorY = d / 2 - max(2.0, m * 0.1)
        val r = min(m * 0.22, w * 0.3)
        val hx = -w / 2 + inset
        return listOf(
            roundRect(-w / 2, -d / 2, w, d, m * 0.06),
            line(-w / 2 + inset, doorY, w / 2 - inset, doorY),
            line(
                -w / 2 + inset,
                doorY - max(1.5, m * 0.05),
                w / 2 - inset,
                doorY - max(1.5, m * 0.05),
            ),
            "M $hx ${doorY - r} Q ${hx + r} ${doorY - r} ${hx + r} $doorY",
        )
    }

    private fun tv(w: Double, d: Double): List<String> {
        val m = min(w, d)
        val screenDepth = max(2.0, min(d * 0.35, max(6.0, m * 0.16)))
        return listOf(
            roundRect(-w / 2, -d / 2, w, d - screenDepth, m * 0.06),
            roundRect(-w / 2 + w * 0.04, d / 2 - screenDepth, w * 0.92, screenDepth, screenDepth * 0.3),
            line(0.0, d / 2 - screenDepth, 0.0, d / 2 - screenDepth * 0.35),
        )
    }

    private fun rug(w: Double, d: Double): List<String> {
        val m = min(w, d)
        if (abs(w - d) < m * 0.08) {
            val r = m / 2
            return listOf(ellipse(0.0, 0.0, r, r), ellipse(0.0, 0.0, r - m * 0.1, r - m * 0.1))
        }
        val outerInset = m * 0.02
        val border = max(outerInset + 1, m * 0.1)
        val ticks = repeatCount((if (w >= d) d else w) / 22.0, 3, 20)
        val len = m * 0.05
        val fringe = ArrayList<String>()
        for (i in 0 until ticks) {
            val t = (i + 0.5) / ticks
            if (w >= d) {
                val y = -d / 2 + t * d
                fringe.add(line(-w / 2 + outerInset, y, -w / 2 + outerInset + len, y))
                fringe.add(line(w / 2 - outerInset, y, w / 2 - outerInset - len, y))
            } else {
                val x = -w / 2 + t * w
                fringe.add(line(x, -d / 2 + outerInset, x, -d / 2 + outerInset + len))
                fringe.add(line(x, d / 2 - outerInset, x, d / 2 - outerInset - len))
            }
        }
        return listOf(
            roundRect(
                -w / 2 + outerInset,
                -d / 2 + outerInset,
                w - 2 * outerInset,
                d - 2 * outerInset,
                m * 0.06,
            ),
            roundRect(
                -w / 2 + border,
                -d / 2 + border,
                max(1.0, w - 2 * border),
                max(1.0, d - 2 * border),
                m * 0.04,
            ),
        ) + fringe
    }

    private fun lamp(w: Double, d: Double): List<String> {
        val shadeR = min(w, d) * 0.5
        if (shadeR <= 1) return emptyList()
        val bulbR = max(1.0, shadeR * 0.28)
        val spokes = ArrayList<String>()
        for (i in 0 until 4) {
            val a = (i * Math.PI) / 2 + Math.PI / 4
            val c = kotlin.math.cos(a)
            val s = kotlin.math.sin(a)
            spokes.add(line(c * bulbR, s * bulbR, c * shadeR, s * shadeR))
        }
        return listOf(ellipse(0.0, 0.0, shadeR, shadeR), ellipse(0.0, 0.0, bulbR, bulbR)) + spokes
    }

    private fun stairs(w: Double, d: Double): List<String> {
        val out = mutableListOf(roundRect(-w / 2, -d / 2, w, d, 0.0))
        val runAlongX = w >= d
        val runLen = if (runAlongX) w else d
        val span = if (runAlongX) d else w
        val treadCount = repeatCount(runLen / max(1.0, span * 0.55), 2, 12)
        val breakAt = 0.78
        for (i in 1 until treadCount) {
            val t = i.toDouble() / treadCount
            if (t >= breakAt) break
            if (runAlongX) {
                val x = -w / 2 + t * w
                out.add(line(x, -d / 2, x, d / 2))
            } else {
                val y = -d / 2 + t * d
                out.add(line(-w / 2, y, w / 2, y))
            }
        }
        val zig = span * 0.22
        if (runAlongX) {
            val x = -w / 2 + breakAt * w
            out.add(
                "M ${x - zig * 0.5} ${-d / 2} L ${x + zig * 0.5} ${-d / 6} L ${x - zig * 0.5} ${d / 6} L ${x + zig * 0.5} ${d / 2}",
            )
        } else {
            val y = -d / 2 + breakAt * d
            out.add(
                "M ${-w / 2} ${y - zig * 0.5} L ${-w / 6} ${y + zig * 0.5} L ${w / 6} ${y - zig * 0.5} L ${w / 2} ${y + zig * 0.5}",
            )
        }
        return out
    }

    private fun sofaL(w: Double, d: Double): List<String> {
        val m = min(w, d)
        val back = m * 0.26
        val legW = min(w * 0.42, d * 0.92)
        val body = listOf(
            "M ${-w / 2} ${-d / 2}",
            "L ${w / 2} ${-d / 2}",
            "L ${w / 2} ${-d / 2 + d * 0.55}",
            "L ${-w / 2 + legW} ${-d / 2 + d * 0.55}",
            "L ${-w / 2 + legW} ${d / 2}",
            "L ${-w / 2} ${d / 2}",
            "Z",
        ).joinToString(" ")
        val out = mutableListOf(
            body,
            line(-w / 2 + back, -d / 2 + back, w / 2, -d / 2 + back),
            line(-w / 2 + back, -d / 2 + back, -w / 2 + back, d / 2),
        )
        val seatTop = -d / 2 + back
        val seatBot = -d / 2 + d * 0.55
        val runX0 = -w / 2 + back
        val runX1 = w / 2
        val seats = repeatCount((runX1 - runX0) / 70.0, 2)
        val cw = (runX1 - runX0) / seats
        val pad = m * 0.03
        for (i in 0 until seats) {
            out.add(
                roundRect(
                    runX0 + i * cw + pad,
                    seatTop + pad,
                    cw - 2 * pad,
                    seatBot - seatTop - 2 * pad,
                    m * 0.05,
                ),
            )
        }
        if (d / 2 - seatBot > m * 0.12) {
            out.add(
                roundRect(
                    runX0 + pad,
                    seatBot + pad,
                    legW - back - 2 * pad,
                    d / 2 - seatBot - 2 * pad,
                    m * 0.05,
                ),
            )
        }
        return out
    }

    private fun chandelier(w: Double, d: Double): List<String> {
        val r = min(w, d) * 0.5
        if (r <= 2) return emptyList()
        val hubR = r * 0.32
        val lampR = r * 0.12
        val out = mutableListOf(ellipse(0.0, 0.0, r, r), ellipse(0.0, 0.0, hubR, hubR))
        val arms = 8
        for (i in 0 until arms) {
            val a = (i * Math.PI * 2) / arms
            val c = kotlin.math.cos(a)
            val s = kotlin.math.sin(a)
            out.add(line(c * hubR, s * hubR, c * (r - lampR), s * (r - lampR)))
        }
        for (i in 0 until arms step 2) {
            val a = (i * Math.PI * 2) / arms
            out.add(ellipse(kotlin.math.cos(a) * (r - lampR), kotlin.math.sin(a) * (r - lampR), lampR, lampR))
        }
        return out
    }

    private fun mirror(w: Double, d: Double): List<String> {
        val long = max(w, d)
        val thin = min(w, d)
        val t = max(thin, long * 0.1)
        val horizontal = w >= d
        fun pt(a: Double, b: Double) = if (horizontal) a to b else b to a
        val halfL = long / 2
        val halfT = t / 2
        val p0 = pt(-halfL, -halfT)
        val p1 = pt(halfL, -halfT)
        val p2 = pt(halfL, halfT)
        val p3 = pt(-halfL, halfT)
        val slab = "M ${p0.first} ${p0.second} L ${p1.first} ${p1.second} L ${p2.first} ${p2.second} L ${p3.first} ${p3.second} Z"
        val f0 = pt(-halfL, halfT * 0.25)
        val f1 = pt(halfL, halfT * 0.25)
        val face = line(f0.first, f0.second, f1.first, f1.second)
        val ticks = repeatCount(long / 26.0, 3, 24)
        val hatch = ArrayList<String>()
        for (i in 0 until ticks) {
            val x = -halfL + (i + 0.5) * (long / ticks)
            val a = pt(x, -halfT)
            val b = pt(x + t * 0.55, halfT * 0.25)
            hatch.add(line(a.first, a.second, b.first, b.second))
        }
        return listOf(slab, face) + hatch
    }
}
