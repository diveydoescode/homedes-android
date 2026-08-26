package com.homedesign.android.domain.export

import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.OpeningBinding
import com.homedesign.android.domain.geom.OpeningSymbol
import com.homedesign.android.domain.geom.OpeningSymbolKind
import com.homedesign.android.domain.geom.ParallelLine
import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.geom.SashArc
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.WallClearance
import com.homedesign.android.domain.geom.WallJoinInference
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.model.Wall
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class ExportFile(val filename: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportFile) return false
        return filename == other.filename && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * filename.hashCode() + bytes.contentHashCode()
}

fun sanitizeExportFilename(name: String?): String {
    val base = (name ?: "plan").trim().ifEmpty { "plan" }
    return base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
}

/** Web `healForExport`: WallJoinInference + WallClearance before draw. */
fun healForExport(home: Home): Home {
    val inferred = WallJoinInference.infer(home.walls)
    val cleared = WallClearance.resolve(home.furniture, inferred.walls)
    return home.copy(walls = inferred.walls, furniture = cleared.furniture)
}

data class PlanBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

fun computePlanBounds(home: Home, levelId: String?): PlanBounds? {
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var any = false
    fun grow(x: Double, y: Double) {
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
        any = true
    }
    for (w in home.walls) {
        if (levelId != null && w.level != levelId) continue
        grow(w.startX, w.startY)
        grow(w.endX, w.endY)
    }
    for (r in home.rooms) {
        if (levelId != null && r.level != levelId) continue
        for (p in r.points) grow(p.x, p.y)
    }
    for (p in home.furniture) {
        if (levelId != null && p.level != levelId) continue
        if (!p.visible) continue
        for (c in FurnitureGeometry.cornerPoints(p)) grow(c.x, c.y)
    }
    for (dw in home.doorsAndWindows) {
        if (levelId != null && dw.piece.level != levelId) continue
        for (c in FurnitureGeometry.cornerPoints(dw.piece)) grow(c.x, c.y)
    }
    for (dim in home.dimensionLines) {
        if (levelId != null && dim.level != levelId) continue
        for (pt in dimensionPoints(dim)) grow(pt.x, pt.y)
    }
    for (label in home.labels) {
        if (levelId != null && label.level != levelId) continue
        grow(label.x, label.y)
    }
    if (!any) return null
    return PlanBounds(minX, minY, maxX, maxY)
}

/** Web `sheetTitle` — home name + optional level. */
fun sheetTitle(home: Home, levelId: String?): String {
    val name = home.name?.takeIf { it.isNotBlank() } ?: "Home plan"
    val levelName = home.levels.find { it.id == levelId }?.name?.takeIf { it.isNotBlank() }
    return if (levelName != null) "$name — $levelName" else name
}

/** Web `roomLabelLines` — name + area when visible and > 0.2 m². */
fun roomLabelLines(room: Room, unitSystem: UnitSystem): List<String> {
    val lines = mutableListOf<String>()
    room.name?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
    if (room.areaVisible) {
        val m2 = RoomGeometry.polygonArea(room) / 10_000.0
        if (m2 > 0.2) lines.add(UnitFormat.area(m2, unitSystem))
    }
    return lines
}

fun dimensionPoints(dim: DimensionLine): List<Vec2> {
    val ax = dim.xStart
    val ay = dim.yStart
    val bx = dim.xEnd
    val by = dim.yEnd
    val dx = bx - ax
    val dy = by - ay
    val len = hypot(dx, dy)
    if (len <= 1e-9) return emptyList()
    val dirx = dx / len
    val diry = dy / len
    val nx = diry
    val ny = -dirx
    return listOf(
        vec(ax, ay),
        vec(bx, by),
        vec(ax + nx * dim.offset, ay + ny * dim.offset),
        vec(bx + nx * dim.offset, by + ny * dim.offset),
    )
}

fun dimLengthCM(dim: DimensionLine): Double =
    hypot(dim.xEnd - dim.xStart, dim.yEnd - dim.yStart)

/** Web `unboundOpeningLines` — four parallel ticks across the opening body. */
fun unboundOpeningLines(piece: HomePieceOfFurniture): List<Pair<Vec2, Vec2>> {
    val halfW = (piece.widthInPlan ?: piece.width) / 2.0
    val halfD = (piece.depthInPlan ?: piece.depth) / 2.0
    if (halfW <= 0 || halfD <= 0) return emptyList()
    val dirx = cos(piece.angle)
    val diry = sin(piece.angle)
    val nx = -diry
    val ny = dirx
    val lines = mutableListOf<Pair<Vec2, Vec2>>()
    for (offset in listOf(-halfD, -halfD / 3, halfD / 3, halfD)) {
        val mx = piece.x + nx * offset
        val my = piece.y + ny * offset
        lines.add(
            vec(mx - dirx * halfW, my - diry * halfW) to
                vec(mx + dirx * halfW, my + diry * halfW),
        )
    }
    return lines
}

data class OpeningDrawItem(
    val piece: HomePieceOfFurniture,
    val lines: List<ParallelLine>,
    val arcs: List<SashArc>,
)

/**
 * Cheap opening art for PDF/DXF using [OpeningSymbol] (bound glass/jamb/threshold
 * or sash arcs; unbound parallel ticks). Not full DXF BLOCK art.
 */
fun collectOpeningDrawItems(
    dws: List<HomeDoorOrWindow>,
    walls: List<Wall>,
): List<OpeningDrawItem> {
    if (dws.isEmpty()) return emptyList()
    val binds = OpeningBinding.bind(walls, dws)
    val bindByOpening = binds.associateBy { it.openingID }
    val wallsById = walls.associateBy { it.id }
    return dws.map { dw ->
        val bind = bindByOpening[dw.piece.id]
        val wall = bind?.let { wallsById[it.wallID] }
        when (OpeningSymbol.classify(dw)) {
            OpeningSymbolKind.Operable -> {
                val arcs = dw.sashes.map { OpeningSymbol.sashArc(it, dw) }
                val jambs = if (bind != null && wall != null) {
                    OpeningSymbol.jambLines(bind, wall) +
                        listOfNotNull(OpeningSymbol.thresholdLine(bind, wall))
                } else {
                    unboundOpeningLines(dw.piece).map { ParallelLine(it.first, it.second) }
                }
                OpeningDrawItem(dw.piece, jambs, arcs)
            }
            OpeningSymbolKind.FixedWindow -> {
                val glass = if (bind != null && wall != null) {
                    OpeningSymbol.glassLines(bind, wall) +
                        OpeningSymbol.jambLines(bind, wall)
                } else {
                    OpeningSymbol.glassLinesUnbound(dw)
                }
                OpeningDrawItem(dw.piece, glass, emptyList())
            }
        }
    }
}

/** Sample a sash arc into polyline points (plan-cm). */
fun sampleSashArc(arc: SashArc, segments: Int = 16): List<Vec2> {
    if (segments < 2 || arc.radius <= 0) return emptyList()
    val pts = ArrayList<Vec2>(segments + 1)
    for (i in 0..segments) {
        val t = i.toDouble() / segments
        val a = arc.startAngle + (arc.endAngle - arc.startAngle) * t
        pts.add(vec(arc.center.x + cos(a) * arc.radius, arc.center.y + sin(a) * arc.radius))
    }
    return pts
}

/** Web/iOS DXF layer table: name, ACI colour, lineweight (1/100 mm). */
data class DxfLayerSpec(val name: String, val aci: Int, val lineweight: Int)

val DXF_LAYER_SPECS: List<DxfLayerSpec> = listOf(
    DxfLayerSpec("0", 7, -3),
    DxfLayerSpec("WALLS", 7, 50),
    DxfLayerSpec("WALL_HATCH", 8, 9),
    DxfLayerSpec("DOORS_WINDOWS", 1, 25),
    DxfLayerSpec("ROOMS", 8, 9),
    DxfLayerSpec("ROOM_LABELS", 7, 18),
    DxfLayerSpec("FURNITURE", 3, 25),
    DxfLayerSpec("FURN_LABELS", 7, 13),
    DxfLayerSpec("SHELVES", 6, 18),
    DxfLayerSpec("A-DIMS", 7, 13),
    DxfLayerSpec("A-NOTES", 7, 13),
    DxfLayerSpec("TITLE", 7, 35),
)
