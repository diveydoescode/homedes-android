package com.homedesign.android.domain.export

import com.homedesign.android.domain.catalog.catalogById
import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.FurnitureSymbolClassifier
import com.homedesign.android.domain.geom.FurnitureSymbols
import com.homedesign.android.domain.geom.OpeningBind
import com.homedesign.android.domain.geom.OpeningBinding
import com.homedesign.android.domain.geom.OpeningSymbol
import com.homedesign.android.domain.geom.OpeningType
import com.homedesign.android.domain.geom.RoomContainment
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.WallCutout
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.geom.WallSegmentation
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Sash
import com.homedesign.android.domain.model.Wall
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

const val DXF_MM_SCALE = 10.0

sealed class WallDrawOp {
    data class Line(val a: Vec2, val b: Vec2) : WallDrawOp()
    data class Hatch(val pts: List<Vec2>) : WallDrawOp()
    data class Polyline(val pts: List<Vec2>, val closed: Boolean, val bulges: List<Double>? = null) : WallDrawOp()
}

data class SymbolPolyline(val points: List<Vec2>, val closed: Boolean)

data class SymbolArt(val key: String, val polylines: List<SymbolPolyline>)

data class OpeningSymbolGeometry(
    val polylines: MutableList<List<DxfXY>> = mutableListOf(),
    val arcs: MutableList<OpeningArc> = mutableListOf(),
    val lines: MutableList<Pair<DxfXY, DxfXY>> = mutableListOf(),
)

data class OpeningArc(val c: DxfXY, val r: Double, val start: Double, val end: Double)

data class BoundOpeningDraw(
    val piece: HomePieceOfFurniture,
    val symbol: OpeningSymbolGeometry,
    val blockName: String,
)

data class OpeningsDraw(
    val bound: List<BoundOpeningDraw>,
    val unbound: List<HomePieceOfFurniture>,
)

data class CompassPose(
    val x: Double,
    val y: Double,
    val diameter: Double,
    val northDirection: Double,
)

fun degrees(radians: Double): Double = (radians * 180.0) / PI

fun asCompass(value: JsonElement?): CompassPose? {
    if (value == null || value !is JsonObject) return null
    val x = value["x"]?.jsonPrimitive?.doubleOrNull ?: return null
    val y = value["y"]?.jsonPrimitive?.doubleOrNull ?: return null
    val diameter = value["diameter"]?.jsonPrimitive?.doubleOrNull ?: return null
    val north = value["northDirection"]?.jsonPrimitive?.doubleOrNull ?: return null
    return CompassPose(x, y, diameter, north)
}

/** Best-effort parse of JsonElement shelfUnits; skip unparsed rather than crash. */
fun asShelfPiece(value: JsonElement): HomePieceOfFurniture? {
    return try {
        val obj = value as? JsonObject ?: return null
        val pieceEl = obj["piece"] as? JsonObject ?: return null
        val x = pieceEl["x"]?.jsonPrimitive?.doubleOrNull ?: return null
        val y = pieceEl["y"]?.jsonPrimitive?.doubleOrNull ?: return null
        val id = pieceEl["id"]?.jsonPrimitive?.contentOrNull ?: "shelf"
        val width = pieceEl["width"]?.jsonPrimitive?.doubleOrNull ?: 60.0
        val depth = pieceEl["depth"]?.jsonPrimitive?.doubleOrNull ?: 30.0
        val height = pieceEl["height"]?.jsonPrimitive?.doubleOrNull ?: 200.0
        val visible = pieceEl["visible"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!visible) return null
        HomePieceOfFurniture(
            id = id,
            name = pieceEl["name"]?.jsonPrimitive?.contentOrNull,
            x = x,
            y = y,
            elevation = pieceEl["elevation"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            angle = pieceEl["angle"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            width = width,
            depth = depth,
            height = height,
            widthInPlan = pieceEl["widthInPlan"]?.jsonPrimitive?.doubleOrNull,
            depthInPlan = pieceEl["depthInPlan"]?.jsonPrimitive?.doubleOrNull,
            visible = true,
            level = pieceEl["level"]?.jsonPrimitive?.contentOrNull,
        )
    } catch (_: Exception) {
        null
    }
}

fun canvasSymbolArt(piece: HomePieceOfFurniture): SymbolArt? {
    val entry = piece.catalogID?.let { catalogById(it) }
    val kind = FurnitureSymbolClassifier.classify(piece, entry)
    val w = piece.widthInPlan ?: piece.width
    val d = piece.depthInPlan ?: piece.depth
    val art = FurnitureSymbols.paths(kind, w, d)
    if (art.paths.isEmpty()) return null
    val transform = if (art.quarterTurn) "rotate(90)" else null
    val polylines = ArrayList<SymbolPolyline>()
    for (stroke in art.paths) {
        for (flat in PathFlatten.flattenPathData(stroke.d, transform)) {
            if (flat.points.size < 2) continue
            polylines.add(
                SymbolPolyline(
                    points = flat.points.map { vec(it.first, it.second) },
                    closed = flat.closed,
                ),
            )
        }
    }
    if (polylines.isEmpty()) return null
    return SymbolArt(
        key = "${kind}_${kotlin.math.round(w * 10).toInt()}x${kotlin.math.round(d * 10).toInt()}",
        polylines = polylines,
    )
}

fun normalizedOpeningSpans(cutouts: List<WallCutout>): List<Pair<Double, Double>> {
    val raw = cutouts.map { cut ->
        val a = cut.tStart.coerceIn(0.0, 1.0)
        val b = cut.tEnd.coerceIn(0.0, 1.0)
        if (a <= b) a to b else b to a
    }.sortedBy { it.first }
    val spans = ArrayList<Pair<Double, Double>>()
    for (span in raw) {
        val last = spans.lastOrNull()
        if (last != null && span.first <= last.second) {
            spans[spans.lastIndex] = last.first to maxOf(last.second, span.second)
        } else {
            spans.add(span)
        }
    }
    return spans
}

private fun curvedWallDrawOps(wall: Wall, cutouts: List<WallCutout>): List<WallDrawOp> {
    val outline = ArcWallGeometry.footprint(wall)
    if (outline.size < 4 || outline.size % 2 != 0) return emptyList()
    val half = outline.size / 2
    val left = outline.subList(0, half)
    val right = outline.subList(half, outline.size).asReversed()
    if (left.size != right.size || left.size < 2) return emptyList()

    val bow = ArcWallGeometry.singleBow(wall)
    val ops = ArrayList<WallDrawOp>()
    fun cap(a: Vec2, b: Vec2) {
        ops.add(WallDrawOp.Line(a, b))
    }

    if (cutouts.isEmpty() && bow != null) {
        val l0 = left.first()
        val l1 = left.last()
        val r0 = right.first()
        val r1 = right.last()
        ops.add(WallDrawOp.Polyline(listOf(l0, l1), closed = false, bulges = listOf(bow, 0.0)))
        ops.add(WallDrawOp.Polyline(listOf(r0, r1), closed = false, bulges = listOf(bow, 0.0)))
        cap(l0, r0)
        cap(l1, r1)
        ops.add(WallDrawOp.Hatch(left + right.asReversed()))
        return ops
    }

    val a = vec(wall.startX, wall.startY)
    val spanX = wall.endX - a.x
    val spanY = wall.endY - a.y
    val chordLen2 = spanX * spanX + spanY * spanY
    if (chordLen2 <= 1e-18) return ops
    fun chordT(p: Vec2): Double =
        ((p.x - a.x) * spanX + (p.y - a.y) * spanY) / chordLen2
    val ts = left.mapIndexed { i, lp ->
        val rp = right[i]
        chordT(vec((lp.x + rp.x) / 2, (lp.y + rp.y) / 2))
    }

    fun interpolate(face: List<Vec2>, t: Double): Vec2 {
        if (t <= (ts.firstOrNull() ?: 0.0)) return face.first()
        if (t >= (ts.lastOrNull() ?: 1.0)) return face.last()
        for (i in 1 until ts.size) {
            if (ts[i] >= t) {
                val span = ts[i] - ts[i - 1]
                if (span <= 1e-12) return face[i]
                val f = (t - ts[i - 1]) / span
                val p0 = face[i - 1]
                val p1 = face[i]
                return vec(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f)
            }
        }
        return face.last()
    }

    fun facePoints(face: List<Vec2>, t0: Double, t1: Double): List<Vec2> {
        val out = ArrayList<Vec2>()
        out.add(interpolate(face, t0))
        for (i in ts.indices) {
            val t = ts[i]
            if (t > t0 && t < t1) out.add(face[i])
        }
        out.add(interpolate(face, t1))
        return out
    }

    val spans = normalizedOpeningSpans(cutouts)
    val runs = ArrayList<Pair<Double, Double>>()
    var cursor = 0.0
    for ((t0, t1) in spans) {
        if (t0 > cursor) runs.add(cursor to t0)
        cursor = t1
    }
    if (cursor < 1) runs.add(cursor to 1.0)

    for ((from, to) in runs) {
        val l = facePoints(left, from, to)
        val r = facePoints(right, from, to)
        if (l.size >= 2) ops.add(WallDrawOp.Polyline(l, closed = false))
        if (r.size >= 2) ops.add(WallDrawOp.Polyline(r, closed = false))
        if (l.size >= 2 && r.size >= 2) {
            ops.add(WallDrawOp.Hatch(l + r.asReversed()))
        }
    }
    for ((t0, t1) in spans) {
        for (t in listOf(t0, t1)) cap(interpolate(left, t), interpolate(right, t))
    }
    cap(interpolate(left, 0.0), interpolate(right, 0.0))
    cap(interpolate(left, 1.0), interpolate(right, 1.0))
    return ops
}

fun wallDrawOps(
    wall: Wall,
    wallsByID: Map<String, Wall>,
    cutouts: List<WallCutout>,
): List<WallDrawOp> {
    if (ArcWallGeometry.isCurved(wall)) return curvedWallDrawOps(wall, cutouts)
    val pts = WallGeometry.miteredPoints(wall, wallsByID)
    if (pts.size < 4) return emptyList()
    val leftStart = pts[0]
    val leftEnd = pts[1]
    val rightEnd = pts[2]
    val rightStart = pts[3]

    val ax = wall.startX
    val ay = wall.startY
    val spanX = wall.endX - ax
    val spanY = wall.endY - ay
    val length = hypot(spanX, spanY)
    if (length <= 1e-9) return emptyList()
    val dirx = spanX / length
    val diry = spanY / length
    var nx = -diry * (wall.thickness / 2)
    var ny = dirx * (wall.thickness / 2)
    val dPlus = hypot(ax + nx - leftStart.x, ay + ny - leftStart.y)
    val dMinus = hypot(ax - nx - leftStart.x, ay - ny - leftStart.y)
    if (dPlus > dMinus) {
        nx = -nx
        ny = -ny
    }

    fun leftAt(t: Double) = vec(ax + spanX * t + nx, ay + spanY * t + ny)
    fun rightAt(t: Double) = vec(ax + spanX * t - nx, ay + spanY * t - ny)

    val ops = ArrayList<WallDrawOp>()
    val spans = normalizedOpeningSpans(cutouts)
    var cursor = 0.0
    var leftFrom = leftStart
    var rightFrom = rightStart
    for ((t0, t1) in spans) {
        if (t0 > cursor) {
            val l1 = leftAt(t0)
            val r1 = rightAt(t0)
            ops.add(WallDrawOp.Line(leftFrom, l1))
            ops.add(WallDrawOp.Line(rightFrom, r1))
            ops.add(WallDrawOp.Hatch(listOf(leftFrom, l1, r1, rightFrom)))
        }
        ops.add(WallDrawOp.Line(leftAt(t0), rightAt(t0)))
        ops.add(WallDrawOp.Line(leftAt(t1), rightAt(t1)))
        cursor = t1
        leftFrom = leftAt(t1)
        rightFrom = rightAt(t1)
    }
    if (cursor < 1) {
        ops.add(WallDrawOp.Line(leftFrom, leftEnd))
        ops.add(WallDrawOp.Line(rightFrom, rightEnd))
        ops.add(WallDrawOp.Hatch(listOf(leftFrom, leftEnd, rightEnd, rightFrom)))
    }
    if (wall.atStart == null || wallsByID[wall.atStart] == null) {
        ops.add(WallDrawOp.Line(leftStart, rightStart))
    }
    if (wall.atEnd == null || wallsByID[wall.atEnd] == null) {
        ops.add(WallDrawOp.Line(leftEnd, rightEnd))
    }
    return ops
}

fun symbolIsEmpty(geo: OpeningSymbolGeometry): Boolean =
    geo.polylines.isEmpty() && geo.arcs.isEmpty() && geo.lines.isEmpty()

fun openingBlockName(geo: OpeningSymbolGeometry): String {
    val s = StringBuilder()
    fun q(v: Double) = kotlin.math.round(v * 10).toInt().toString()
    for (p in geo.polylines) {
        s.append('P')
        for (v in p) s.append("${q(v.first)},${q(v.second)};")
    }
    for (a in geo.arcs) {
        s.append("A${q(a.c.first)},${q(a.c.second)},${q(a.r)},${q(a.start)},${q(a.end)};")
    }
    for (l in geo.lines) {
        s.append("L${q(l.first.first)},${q(l.first.second)},${q(l.second.first)},${q(l.second.second)};")
    }
    val kind = if (geo.arcs.isEmpty()) "WIN" else "DOOR"
    return "HD-$kind-${Fnv1a.hash64Base36(s.toString())}"
}

fun isDoorPiece(piece: HomePieceOfFurniture): Boolean {
    val text = (piece.name ?: "").lowercase()
    if (text.contains("door") || text.contains("porte") ||
        text.contains("gate") || text.contains("entrance")
    ) {
        return true
    }
    if (text.contains("window") || text.contains("fenetre") ||
        text.contains("fenêtre") || text.contains("glass")
    ) {
        return false
    }
    return piece.height >= 170
}

private fun isSliding(dw: HomeDoorOrWindow): Boolean {
    val name = (dw.piece.name ?: "").lowercase()
    if (name.contains("sliding") || name.contains("slider")) return true
    val width = dw.piece.widthInPlan ?: dw.piece.width
    return name.contains("glass") && name.contains("door") && width >= 150
}

private fun swingLandsOutsideEveryRoom(dw: HomeDoorOrWindow, rooms: List<Room>): Boolean {
    if (rooms.isEmpty()) return false
    for (sash in dw.sashes) {
        val arc = OpeningSymbol.sashArc(sash, dw)
        if (arc.radius <= 1e-9) continue
        val mid = (arc.startAngle + arc.endAngle) / 2
        val p = vec(
            arc.center.x + cos(mid) * (arc.radius * 0.6),
            arc.center.y + sin(mid) * (arc.radius * 0.6),
        )
        if (rooms.any { RoomContainment.pointInRoom(it, p) }) return false
    }
    return true
}

private fun cutoutBind(cutout: WallCutout) =
    OpeningBind(openingID = "", wallID = "", tStart = cutout.tStart, tEnd = cutout.tEnd)

fun openingSymbol(
    dw: HomeDoorOrWindow,
    cutout: WallCutout,
    wall: Wall,
    rooms: List<Room>,
): OpeningSymbolGeometry {
    val geo = OpeningSymbolGeometry()
    val piece = dw.piece
    val cx = piece.x
    val cy = piece.y
    val ct = cos(piece.angle)
    val st = sin(piece.angle)

    fun local(px: Double, py: Double): DxfXY {
        val rx = px - cx
        val ry = py - cy
        return ((rx * ct + ry * st) * DXF_MM_SCALE) to ((rx * st - ry * ct) * DXF_MM_SCALE)
    }
    fun line(ax: Double, ay: Double, bx: Double, by: Double) {
        geo.lines.add(local(ax, ay) to local(bx, by))
    }

    val sliding = isSliding(dw)
    val isDoor = isDoorPiece(dw.piece) && !sliding
    val sashes: List<Sash> = when {
        sliding -> emptyList()
        dw.sashes.isEmpty() && isDoor -> OpeningType.door.defaultSashes.map { it.copy() }
        else -> dw.sashes
    }

    val bind = cutoutBind(cutout)
    if (!isDoor) {
        for (g in OpeningSymbol.glassLines(bind, wall)) {
            line(g.start.x, g.start.y, g.end.x, g.end.y)
        }
        OpeningSymbol.thresholdLine(bind, wall)?.let { thr ->
            line(thr.start.x, thr.start.y, thr.end.x, thr.end.y)
        }
    }

    var resolved = dw.copy(sashes = sashes)
    if (isDoor && swingLandsOutsideEveryRoom(resolved, rooms)) {
        resolved = resolved.copy(mirroredY = !resolved.mirroredY)
    }

    val leafT = minOf(4.5, wall.thickness * 0.35) * DXF_MM_SCALE
    val swingExtent = ArrayList<Double>()

    for (sash in sashes) {
        val arc = OpeningSymbol.sashArc(sash, resolved)
        if (arc.radius <= 1e-9) continue
        val hinge = local(arc.center.x, arc.center.y)
        val radius = arc.radius * DXF_MM_SCALE
        val open = arc.endAngle - piece.angle
        val dx = cos(open)
        val dy = sin(open) * -1
        val px = -dy * leafT
        val py = dx * leafT
        geo.polylines.add(
            listOf(
                hinge.first to hinge.second,
                (hinge.first + dx * radius) to (hinge.second + dy * radius),
                (hinge.first + dx * radius + px) to (hinge.second + dy * radius + py),
                (hinge.first + px) to (hinge.second + py),
            ),
        )
        val s = -degrees(arc.startAngle - piece.angle)
        val e = -degrees(arc.endAngle - piece.angle)
        val rising = arc.endAngle > arc.startAngle
        geo.arcs.add(
            OpeningArc(
                c = hinge,
                r = radius,
                start = if (rising) e else s,
                end = if (rising) s else e,
            ),
        )
        val shut = arc.startAngle - piece.angle
        swingExtent.add(hinge.first)
        swingExtent.add(hinge.first + cos(shut) * radius)
    }

    if (isDoor && swingExtent.isNotEmpty()) {
        val inner = swingExtent.minOrNull()!!
        val outer = swingExtent.maxOrNull()!!
        val wStartX = wall.startX
        val wStartY = wall.startY
        val spanX = wall.endX - wStartX
        val spanY = wall.endY - wStartY
        val xs = listOf(
            local(wStartX + spanX * cutout.tStart, wStartY + spanY * cutout.tStart).first,
            local(wStartX + spanX * cutout.tEnd, wStartY + spanY * cutout.tEnd).first,
        )
        val halfT = (wall.thickness / 2) * DXF_MM_SCALE
        val webPairs = listOf(
            minOf(xs[0], xs[1]) to inner,
            outer to maxOf(xs[0], xs[1]),
        )
        for ((from, to) in webPairs) {
            if (to - from > 0.5) {
                geo.polylines.add(
                    listOf(
                        from to -halfT,
                        to to -halfT,
                        to to halfT,
                        from to halfT,
                    ),
                )
            }
        }
    }
    return geo
}

fun collectOpenings(
    dws: List<HomeDoorOrWindow>,
    walls: List<Wall>,
    rooms: List<Room>,
): OpeningsDraw {
    val bindings = OpeningBinding.bind(walls, dws)
    val wallByID = walls.associateBy { it.id }
    val openingByID = dws.associateBy { it.piece.id }
    val placed = HashSet<String>()
    val bound = ArrayList<BoundOpeningDraw>()

    val byWall = LinkedHashMap<String, MutableList<OpeningBind>>()
    for (b in bindings) {
        byWall.getOrPut(b.wallID) { mutableListOf() }.add(b)
    }

    for (wallID in byWall.keys.sorted()) {
        val wall = wallByID[wallID] ?: continue
        val list = byWall[wallID] ?: continue
        for (binding in list) {
            val dw = openingByID[binding.openingID] ?: continue
            placed.add(binding.openingID)
            val cutout = WallSegmentation.makeCutout(binding, dw)
            val symbol = openingSymbol(dw, cutout, wall, rooms)
            if (symbolIsEmpty(symbol)) continue
            bound.add(BoundOpeningDraw(dw.piece, symbol, openingBlockName(symbol)))
        }
    }
    val unbound = dws.map { it.piece }.filter { it.id !in placed }
    return OpeningsDraw(bound, unbound)
}

data class NorthArrowGeom(
    val center: Vec2,
    val radius: Double,
    val triangle: List<Vec2>,
    val letter: Vec2,
)

fun northArrowPoints(compass: CompassPose): NorthArrowGeom {
    val r = maxOf(compass.diameter, 40.0) / 2
    val nx = cos(compass.northDirection)
    val ny = sin(compass.northDirection)
    val sx = -ny
    val sy = nx
    return NorthArrowGeom(
        center = vec(compass.x, compass.y),
        radius = r,
        triangle = listOf(
            vec(compass.x + nx * r, compass.y + ny * r),
            vec(
                compass.x - nx * (r * 0.35) + sx * (r * 0.35),
                compass.y - ny * (r * 0.35) + sy * (r * 0.35),
            ),
            vec(
                compass.x - nx * (r * 0.35) - sx * (r * 0.35),
                compass.y - ny * (r * 0.35) - sy * (r * 0.35),
            ),
        ),
        letter = vec(compass.x + nx * (r * 1.45), compass.y + ny * (r * 1.45)),
    )
}
