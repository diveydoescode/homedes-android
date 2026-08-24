package com.homedesign.android.domain.export

import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import kotlin.math.PI

private const val DXF_MM_SCALE = 10.0

/**
 * R12-style ASCII DXF with web-parity layer names: walls, rooms, room labels,
 * furniture footprints, openings (OpeningSymbol), dimensions, title.
 * Plan cm → mm ×10; Y flipped about plan max-Y.
 */
fun exportDXF(
    home: Home,
    unitSystem: UnitSystem = UnitSystem.Millimetre,
): ExportFile? {
    val home = healForExport(home)
    val levelId = home.selectedLevelID
    val bounds = computePlanBounds(home, levelId) ?: return null
    val yRef = bounds.maxY
    fun fx(x: Double): Double = x * DXF_MM_SCALE
    fun fy(y: Double): Double = (yRef - y) * DXF_MM_SCALE

    val sb = StringBuilder()
    fun pair(code: Int, value: String) {
        sb.append(code).append('\n').append(value).append('\n')
    }

    val walls = home.walls.filter { levelId == null || it.level == levelId }
    val rooms = home.rooms.filter { levelId == null || it.level == levelId }
    val furniture = home.furniture.filter {
        (levelId == null || it.level == levelId) && it.visible
    }
    val dws = home.doorsAndWindows.filter { levelId == null || it.piece.level == levelId }
    val dims = home.dimensionLines.filter { levelId == null || it.level == levelId }

    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    fun grow(x: Double, y: Double) {
        minX = minOf(minX, x)
        minY = minOf(minY, y)
        maxX = maxOf(maxX, x)
        maxY = maxOf(maxY, y)
    }

    val entityBuf = StringBuilder()
    fun ePair(code: Int, value: String) {
        entityBuf.append(code).append('\n').append(value).append('\n')
    }

    fun addLine(x1: Double, y1: Double, x2: Double, y2: Double, layer: String) {
        grow(x1, y1); grow(x2, y2)
        ePair(0, "LINE")
        ePair(8, layer)
        ePair(10, fmt(x1)); ePair(20, fmt(y1)); ePair(30, "0.0")
        ePair(11, fmt(x2)); ePair(21, fmt(y2)); ePair(31, "0.0")
    }

    fun addLwPolyline(pts: List<Pair<Double, Double>>, layer: String, closed: Boolean) {
        if (pts.size < 2) return
        for ((x, y) in pts) grow(x, y)
        ePair(0, "LWPOLYLINE")
        ePair(8, layer)
        ePair(90, pts.size.toString())
        ePair(70, if (closed) "1" else "0")
        for ((x, y) in pts) {
            ePair(10, fmt(x))
            ePair(20, fmt(y))
        }
    }

    fun addText(text: String, x: Double, y: Double, height: Double, layer: String) {
        if (text.isBlank()) return
        grow(x, y)
        ePair(0, "TEXT")
        ePair(8, layer)
        ePair(10, fmt(x)); ePair(20, fmt(y)); ePair(30, "0.0")
        ePair(40, fmt(height))
        ePair(1, text)
    }

    fun addArc(
        cx: Double,
        cy: Double,
        radius: Double,
        startDeg: Double,
        endDeg: Double,
        layer: String,
    ) {
        if (radius <= 0) return
        grow(cx - radius, cy - radius)
        grow(cx + radius, cy + radius)
        ePair(0, "ARC")
        ePair(8, layer)
        ePair(10, fmt(cx)); ePair(20, fmt(cy)); ePair(30, "0.0")
        ePair(40, fmt(radius))
        ePair(50, fmt(startDeg))
        ePair(51, fmt(endDeg))
    }

    for (wall in walls) {
        val outline = WallGeometry.unjoinedOutline(wall)
        val pts = outline.map { fx(it.x) to fy(it.y) }
        addLwPolyline(pts, "WALLS", closed = true)
    }

    for (room in rooms) {
        if (room.points.size < 3) continue
        val pts = room.points.map { fx(it.x) to fy(it.y) }
        addLwPolyline(pts, "ROOMS", closed = true)
        val lines = roomLabelLines(room, unitSystem)
        if (lines.isEmpty()) continue
        val c = RoomGeometry.centroid(room)
        addText(lines.joinToString(" "), fx(c.x), fy(c.y), 220.0, "ROOM_LABELS")
    }

    for (piece in furniture) {
        val pts = FurnitureGeometry.cornerPoints(piece).map { fx(it.x) to fy(it.y) }
        addLwPolyline(pts, "FURNITURE", closed = true)
        addText(piece.name ?: "", fx(piece.x), fy(piece.y), 150.0, "FURN_LABELS")
    }

    for (item in collectOpeningDrawItems(dws, walls)) {
        for (line in item.lines) {
            addLine(
                fx(line.start.x), fy(line.start.y),
                fx(line.end.x), fy(line.end.y),
                "DOORS_WINDOWS",
            )
        }
        for (arc in item.arcs) {
            // Y-flip reverses arc direction; negate angles and swap.
            val startDeg = -arc.endAngle * 180.0 / PI
            val endDeg = -arc.startAngle * 180.0 / PI
            addArc(
                fx(arc.center.x),
                fy(arc.center.y),
                arc.radius * DXF_MM_SCALE,
                startDeg,
                endDeg,
                "DOORS_WINDOWS",
            )
        }
    }

    for (dim in dims) {
        val pts = dimensionPoints(dim)
        if (pts.size != 4) continue
        addLine(fx(pts[0].x), fy(pts[0].y), fx(pts[2].x), fy(pts[2].y), "A-DIMS")
        addLine(fx(pts[1].x), fy(pts[1].y), fx(pts[3].x), fy(pts[3].y), "A-DIMS")
        addLine(fx(pts[2].x), fy(pts[2].y), fx(pts[3].x), fy(pts[3].y), "A-DIMS")
        val mx = (pts[2].x + pts[3].x) / 2.0
        val my = (pts[2].y + pts[3].y) / 2.0
        addText(UnitFormat.length(dimLengthCM(dim), unitSystem), fx(mx), fy(my), 150.0, "A-DIMS")
    }

    addText(
        sheetTitle(home, levelId),
        fx(bounds.minX),
        fy(bounds.minY) + 700.0,
        350.0,
        "TITLE",
    )

    if (minX.isInfinite()) {
        minX = 0.0; minY = 0.0; maxX = 1.0; maxY = 1.0
    }

    pair(0, "SECTION"); pair(2, "HEADER")
    pair(9, "\$ACADVER"); pair(1, "AC1009")
    pair(9, "\$EXTMIN"); pair(10, fmt(minX)); pair(20, fmt(minY)); pair(30, "0.0")
    pair(9, "\$EXTMAX"); pair(10, fmt(maxX)); pair(20, fmt(maxY)); pair(30, "0.0")
    pair(0, "ENDSEC")

    pair(0, "SECTION"); pair(2, "TABLES")
    pair(0, "TABLE"); pair(2, "LAYER"); pair(70, DXF_LAYER_SPECS.size.toString())
    for ((name, color) in DXF_LAYER_SPECS) {
        pair(0, "LAYER"); pair(2, name); pair(70, "0"); pair(62, color.toString()); pair(6, "CONTINUOUS")
    }
    pair(0, "ENDTAB")
    pair(0, "ENDSEC")

    pair(0, "SECTION"); pair(2, "ENTITIES")
    sb.append(entityBuf)
    pair(0, "ENDSEC")
    pair(0, "EOF")

    return ExportFile(
        filename = "${sanitizeExportFilename(home.name)}.dxf",
        bytes = sb.toString().toByteArray(Charsets.UTF_8),
    )
}

private fun fmt(d: Double): String {
    if (!d.isFinite()) return "0.0000"
    return "%.4f".format(d)
}
