package com.homedesign.android.domain.export

import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
private const val DXF_MM_SCALE = 10.0

/**
 * Minimal but valid R12-style ASCII DXF: HEADER extents + TABLES layers +
 * ENTITIES (wall outlines, room polylines, room labels).
 * Plan cm → mm ×10; Y flipped about plan max-Y (Y-up DXF).
 */
fun exportDXF(
    home: Home,
    unitSystem: UnitSystem = UnitSystem.Millimetre,
): ExportFile? {
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

    for (wall in walls) {
        val outline = WallGeometry.unjoinedOutline(wall)
        val pts = outline.map { fx(it.x) to fy(it.y) }
        addLwPolyline(pts, "WALLS", closed = true)
    }

    for (room in rooms) {
        if (room.points.size < 3) continue
        val pts = room.points.map { fx(it.x) to fy(it.y) }
        addLwPolyline(pts, "ROOMS", closed = true)
        val c = RoomGeometry.centroid(room)
        val areaM2 = RoomGeometry.polygonArea(room) / 10_000.0
        val label = buildString {
            room.name?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (isNotEmpty()) append('\n')
            append(UnitFormat.area(areaM2, unitSystem))
        }
        addText(label.replace('\n', ' '), fx(c.x), fy(c.y), 220.0, "ROOM_LABELS")
    }

    if (minX.isInfinite()) {
        minX = 0.0; minY = 0.0; maxX = 1.0; maxY = 1.0
    }

    pair(0, "SECTION"); pair(2, "HEADER")
    pair(9, "\$ACADVER"); pair(1, "AC1009")
    pair(9, "\$EXTMIN"); pair(10, fmt(minX)); pair(20, fmt(minY)); pair(30, "0.0")
    pair(9, "\$EXTMAX"); pair(10, fmt(maxX)); pair(20, fmt(maxY)); pair(30, "0.0")
    pair(0, "ENDSEC")

    pair(0, "SECTION"); pair(2, "TABLES")
    pair(0, "TABLE"); pair(2, "LAYER"); pair(70, "4")
    for ((name, color) in listOf(
        "WALLS" to 7,
        "ROOMS" to 8,
        "ROOM_LABELS" to 7,
        "0" to 7,
    )) {
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
