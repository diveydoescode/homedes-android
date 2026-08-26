package com.homedesign.android.domain.export

import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.geom.WallSegmentation
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import kotlin.math.abs

/**
 * Plan DXF export — R2018/AC1032 shell + web/iOS [PlanDXFRenderer] geometry walk.
 * cm → mm ×10; Y flipped about plan max-Y.
 */
fun exportDXF(
    home: Home,
    unitSystem: UnitSystem = UnitSystem.Millimetre,
): ExportFile? {
    val home = healForExport(home)
    val levelId = home.selectedLevelID
    val walls = home.walls.filter { levelId == null || it.level == levelId }
    val rooms = home.rooms.filter { levelId == null || it.level == levelId }
    val furniture = home.furniture.filter {
        (levelId == null || it.level == levelId) && it.visible
    }
    val dws = home.doorsAndWindows.filter { levelId == null || it.piece.level == levelId }
    val shelves = home.shelfUnits.mapNotNull { asShelfPiece(it) }.filter {
        (levelId == null || it.level == levelId) && it.visible
    }
    val dims = home.dimensionLines.filter { levelId == null || it.level == levelId }
    val labels = home.labels.filter {
        (levelId == null || it.level == levelId) && abs(it.pitch ?: 0.0) < 0.01
    }
    val wallsByID = walls.associateBy { it.id }

    val bounds = computePlanBounds(home, levelId) ?: return null
    val yRef = bounds.maxY
    fun fx(x: Double): Double = x * DXF_MM_SCALE
    fun fy(y: Double): Double = (yRef - y) * DXF_MM_SCALE

    val w = DXFWriter()
    for ((name, aci, lw) in DXF_LAYER_SPECS) {
        w.addLayer(name, aci, lw)
    }

    val cutoutsByWall = WallSegmentation.cutoutsByWallID(walls, dws)
    for (wall in walls) {
        val ops = wallDrawOps(wall, wallsByID, cutoutsByWall[wall.id].orEmpty())
        for (op in ops) {
            when (op) {
                is WallDrawOp.Line -> w.addLine(
                    fx(op.a.x) to fy(op.a.y),
                    fx(op.b.x) to fy(op.b.y),
                    "WALLS",
                )
                is WallDrawOp.Polyline -> {
                    val bulges = op.bulges?.mapIndexed { i, b -> if (i == 0) -b else b }
                    w.addLWPolyline(
                        op.pts.map { fx(it.x) to fy(it.y) },
                        "WALLS",
                        op.closed,
                        bulges,
                    )
                }
                is WallDrawOp.Hatch -> w.addHatch(
                    op.pts.map { fx(it.x) to fy(it.y) },
                    "WALL_HATCH",
                )
            }
        }
    }

    for (room in rooms) {
        if (room.points.size < 3) continue
        w.addLWPolyline(
            room.points.map { fx(it.x) to fy(it.y) },
            "ROOMS",
            closed = true,
        )
        val lines = roomLabelLines(room, unitSystem)
        if (lines.isEmpty()) continue
        val c = RoomGeometry.centroid(room)
        w.addMText(lines.joinToString("\n"), fx(c.x) to fy(c.y), 220.0, "ROOM_LABELS")
    }

    for (piece in furniture) {
        emitFurniture(piece, w, ::fx, ::fy)
    }

    val openings = collectOpenings(dws, walls, rooms)
    val defined = HashSet<String>()
    for (item in openings.bound) {
        if (defined.add(item.blockName)) {
            w.addBlock(item.blockName) { bw -> emitOpeningSymbol(item.symbol, bw) }
        }
        w.addInsert(
            item.blockName,
            fx(item.piece.x) to fy(item.piece.y),
            1.0,
            1.0,
            -degrees(item.piece.angle),
            "DOORS_WINDOWS",
        )
    }
    for (piece in openings.unbound) {
        for ((a, b) in unboundOpeningLines(piece)) {
            w.addLine(fx(a.x) to fy(a.y), fx(b.x) to fy(b.y), "DOORS_WINDOWS")
        }
    }

    for (piece in shelves) {
        emitRectangle(piece, w, "SHELVES", ::fx, ::fy)
        w.addText(piece.name.orEmpty(), fx(piece.x) to fy(piece.y), 150.0, "FURN_LABELS")
    }

    for (dim in dims) {
        val pts = dimensionPoints(dim)
        if (pts.size != 4) continue
        w.addAlignedDimension(
            fx(pts[0].x) to fy(pts[0].y),
            fx(pts[1].x) to fy(pts[1].y),
            fx(pts[2].x) to fy(pts[2].y),
            UnitFormat.length(dimLengthCM(dim), unitSystem),
            "A-DIMS",
        )
    }

    for (label in labels) {
        w.addText(
            label.text,
            fx(label.x) to fy(label.y),
            180.0,
            "A-NOTES",
            -degrees(label.angle),
        )
    }

    asCompass(home.compass)?.let { compass ->
        val arrow = northArrowPoints(compass)
        w.addCircle(fx(arrow.center.x) to fy(arrow.center.y), arrow.radius * DXF_MM_SCALE, "A-NOTES")
        w.addLWPolyline(
            arrow.triangle.map { fx(it.x) to fy(it.y) },
            "A-NOTES",
            closed = true,
        )
        w.addText("N", fx(arrow.letter.x) to fy(arrow.letter.y), 200.0, "A-NOTES")
    }

    val titleParts = ArrayList<String>()
    home.name?.takeIf { it.isNotBlank() }?.let { titleParts.add(it) }
    home.levels.find { it.id == levelId }?.name?.takeIf { it.isNotBlank() }?.let { titleParts.add(it) }
    if (titleParts.isNotEmpty()) {
        w.addText(
            titleParts.joinToString(" — "),
            fx(bounds.minX) to (fy(bounds.minY) + 700.0),
            350.0,
            "TITLE",
        )
    }

    return ExportFile(
        filename = "${sanitizeExportFilename(home.name)}.dxf",
        bytes = w.data(),
    )
}

private fun emitOpeningSymbol(geo: OpeningSymbolGeometry, w: DXFWriter) {
    for (p in geo.polylines) w.addLWPolyline(p, "0", true)
    for (a in geo.arcs) w.addArc(a.c, a.r, a.start, a.end, "0")
    for ((a, b) in geo.lines) w.addLine(a, b, "0")
}

private fun emitRectangle(
    piece: HomePieceOfFurniture,
    w: DXFWriter,
    layer: String,
    fx: (Double) -> Double,
    fy: (Double) -> Double,
) {
    val pts = FurnitureGeometry.cornerPoints(piece).map { fx(it.x) to fy(it.y) }
    w.addLWPolyline(pts, layer, true)
}

private fun emitFurniture(
    piece: HomePieceOfFurniture,
    w: DXFWriter,
    fx: (Double) -> Double,
    fy: (Double) -> Double,
) {
    val art = canvasSymbolArt(piece)
    if (art != null && art.polylines.isNotEmpty()) {
        val ca = kotlin.math.cos(piece.angle)
        val sa = kotlin.math.sin(piece.angle)
        for (line in art.polylines) {
            if (line.points.size < 2) continue
            val pts = line.points.map { p ->
                val wx = p.x * ca - p.y * sa + piece.x
                val wy = p.x * sa + p.y * ca + piece.y
                fx(wx) to fy(wy)
            }
            w.addLWPolyline(pts, "FURNITURE", line.closed)
        }
    } else {
        emitRectangle(piece, w, "FURNITURE", fx, fy)
    }
    w.addText(piece.name.orEmpty(), fx(piece.x) to fy(piece.y), 150.0, "FURN_LABELS")
}
