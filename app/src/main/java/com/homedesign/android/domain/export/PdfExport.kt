package com.homedesign.android.domain.export

import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.round

/** A4 landscape points. */
const val PAGE_WIDTH = 842.0
const val PAGE_HEIGHT = 595.0

private const val MARGIN = 40.0
private const val TITLE_STRIP = 30.0

/** Parse `#AARRGGBB` / `#RRGGBB` plan-label colors to RGB 0..1. */
private fun parseLabelRgb(color: String?): Triple<Double, Double, Double>? {
    if (color.isNullOrBlank()) return null
    val hex = color.trim().removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return null
    val rgb = when (hex.length) {
        8 -> value and 0xFFFFFF
        6 -> value
        else -> return null
    }
    val r = ((rgb shr 16) and 0xFF) / 255.0
    val g = ((rgb shr 8) and 0xFF) / 255.0
    val b = (rgb and 0xFF) / 255.0
    return Triple(r, g, b)
}

private fun pdfEscape(text: String): String {
    val translit = text
        .replace('′', '\'')
        .replace('″', '"')
        .replace('×', 'x')
        .replace('–', '-')
        .replace('\u00A0', ' ')
    val out = StringBuilder()
    for (ch in translit) {
        val cp = ch.code
        when {
            ch == '\\' || ch == '(' || ch == ')' -> out.append('\\').append(ch)
            cp < 128 -> out.append(ch)
            cp < 256 -> out.append('\\').append(cp.toString(8).padStart(3, '0'))
            else -> out.append('?')
        }
    }
    return out.toString()
}

private fun n(v: Double): String {
    if (!v.isFinite()) return "0"
    val rounded = round(v * 1000.0) / 1000.0
    if (rounded == 0.0) return "0"
    return rounded.toString()
}

private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
    deflater.setInput(data)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buf = ByteArray(1024)
    while (!deflater.finished()) {
        val count = deflater.deflate(buf)
        if (count > 0) out.write(buf, 0, count)
    }
    deflater.end()
    return out.toByteArray()
}

/**
 * iOS `PlanPDFRenderer` parity: A4 landscape PDF 1.4 with title block, 1 m scale
 * bar, rooms, furniture footprints, mitered/curved solid walls, dimensions, and
 * plan labels (pitch≈0). Geometry sources match the editor canvas.
 */
fun exportPDF(
    home: Home,
    unitSystem: UnitSystem = UnitSystem.Metric,
): ExportFile? {
    val home = healForExport(home)
    val levelId = home.selectedLevelID
    val bounds = computePlanBounds(home, levelId) ?: return null
    val yRef = bounds.maxY
    val planW = maxOf(bounds.maxX - bounds.minX, 1.0)
    val planH = maxOf(bounds.maxY - bounds.minY, 1.0)
    val drawW = PAGE_WIDTH - MARGIN * 2
    val drawH = PAGE_HEIGHT - MARGIN * 2 - TITLE_STRIP
    val scale = minOf(drawW / planW, drawH / planH)
    val originX = MARGIN + (drawW - planW * scale) / 2.0
    val originY = MARGIN + (drawH - planH * scale) / 2.0

    fun fx(x: Double): Double = originX + (x - bounds.minX) * scale
    // PDF Y-up: flip about plan max-Y so the sheet matches on-screen plan orientation.
    fun fy(y: Double): Double = originY + (yRef - y) * scale

    val hairline = maxOf(0.25, 1.0 / scale)

    val ops = StringBuilder()
    fun op(s: String) { ops.append(s).append('\n') }

    fun setStrokeRGB(r: Double, g: Double, b: Double) = op("${n(r)} ${n(g)} ${n(b)} RG")
    fun setFillRGB(r: Double, g: Double, b: Double) = op("${n(r)} ${n(g)} ${n(b)} rg")
    fun setLineWidth(w: Double) = op("${n(w)} w")
    fun moveTo(x: Double, y: Double) = op("${n(x)} ${n(y)} m")
    fun lineTo(x: Double, y: Double) = op("${n(x)} ${n(y)} l")
    fun stroke() = op("S")
    fun fill() = op("f")
    fun closePath() = op("h")

    fun polyline(pts: List<Pair<Double, Double>>, closed: Boolean) {
        if (pts.isEmpty()) return
        moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second)
        if (closed) closePath()
    }

    fun text(str: String, x: Double, y: Double, size: Double, bold: Boolean) {
        if (str.isBlank()) return
        val font = if (bold) "F2" else "F1"
        op("BT")
        op("/$font ${n(size)} Tf")
        op("${n(x)} ${n(y)} Td")
        op("(${pdfEscape(str)}) Tj")
        op("ET")
    }

    // Title block (page space) — matches iOS PlanPDFRenderer
    setStrokeRGB(0.0, 0.0, 0.0)
    setFillRGB(0.0, 0.0, 0.0)
    setLineWidth(1.0)
    text(sheetTitle(home, levelId), MARGIN, PAGE_HEIGHT - 32, 14.0, true)

    val metre = 100.0 * scale
    val barY = 24.0
    setFillRGB(0.0, 0.0, 0.0)
    op("${n(MARGIN)} ${n(barY)} ${n(metre / 2)} ${n(4.0)} re")
    fill()
    setStrokeRGB(0.0, 0.0, 0.0)
    op("${n(MARGIN + metre / 2)} ${n(barY)} ${n(metre / 2)} ${n(4.0)} re")
    stroke()
    text("1 m", MARGIN + metre + 6, barY, 9.0, false)

    val walls = home.walls.filter { levelId == null || it.level == levelId }
    val wallsById = walls.associateBy { it.id }

    // Rooms (accent fill + stroke; room name/area stay on-canvas, not on iOS PDF)
    val rooms = home.rooms.filter { levelId == null || it.level == levelId }
    for (room in rooms) {
        if (room.points.size < 3) continue
        val pts = room.points.map { fx(it.x) to fy(it.y) }
        setFillRGB(0.0, 0.48, 1.0)
        op("q")
        op("/GS06 gs")
        polyline(pts, true)
        fill()
        op("Q")
        setStrokeRGB(0.0, 0.48, 1.0)
        setLineWidth(hairline)
        polyline(pts, true)
        stroke()
    }

    // Furniture footprints (no name labels — iOS PlanPDFRenderer)
    val furniture = home.furniture.filter {
        (levelId == null || it.level == levelId) && it.visible
    }
    setStrokeRGB(0.25, 0.25, 0.25)
    setLineWidth(hairline)
    for (piece in furniture) {
        val corners = FurnitureGeometry.cornerPoints(piece)
        if (corners.size < 3) continue
        polyline(corners.map { fx(it.x) to fy(it.y) }, true)
        stroke()
    }

    // Walls — solid ink; curved footprint or mitered join outline (iOS)
    setFillRGB(0.0, 0.0, 0.0)
    for (wall in walls) {
        val outline = if (ArcWallGeometry.isCurved(wall)) {
            ArcWallGeometry.footprint(wall)
        } else {
            WallGeometry.miteredPoints(wall, wallsById)
        }
        if (outline.size < 3) continue
        polyline(outline.map { fx(it.x) to fy(it.y) }, true)
        fill()
    }

    // Dimensions
    val dims = home.dimensionLines.filter { levelId == null || it.level == levelId }
    setStrokeRGB(0.25, 0.25, 0.28)
    setFillRGB(0.25, 0.25, 0.28)
    setLineWidth(hairline)
    for (dim in dims) {
        val pts = dimensionPoints(dim)
        if (pts.size != 4) continue
        moveTo(fx(pts[0].x), fy(pts[0].y)); lineTo(fx(pts[2].x), fy(pts[2].y)); stroke()
        moveTo(fx(pts[1].x), fy(pts[1].y)); lineTo(fx(pts[3].x), fy(pts[3].y)); stroke()
        moveTo(fx(pts[2].x), fy(pts[2].y)); lineTo(fx(pts[3].x), fy(pts[3].y)); stroke()
        val mx = (pts[2].x + pts[3].x) / 2.0
        val my = (pts[2].y + pts[3].y) / 2.0
        text(UnitFormat.length(dimLengthCM(dim), unitSystem), fx(mx), fy(my) + 10.0, 8.0, false)
    }

    // Plan labels (pitch≈0 only — same filter as iOS)
    val labels = home.labels.filter {
        (levelId == null || it.level == levelId) && abs(it.pitch ?: 0.0) < 0.01
    }
    for (label in labels) {
        if (label.text.isBlank()) continue
        val rgb = parseLabelRgb(label.color) ?: Triple(0.0, 0.0, 0.0)
        setFillRGB(rgb.first, rgb.second, rgb.third)
        text(label.text, fx(label.x), fy(label.y), 10.0, false)
    }

    val contentBytes = ops.toString().toByteArray(Charsets.ISO_8859_1)
    val compressed = deflate(contentBytes)

    val parts = ArrayList<ByteArray>(8)

    fun obj(body: ByteArray): Int {
        parts.add(body)
        return parts.size
    }

    fun obj(body: String): Int = obj(body.toByteArray(Charsets.ISO_8859_1))

    val font1 = obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    val font2 = obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")
    val gs = obj("<< /Type /ExtGState /ca 0.06 /CA 0.4 >>")

    val streamHeader = "<< /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n"
        .toByteArray(Charsets.ISO_8859_1)
    val streamFooter = "\nendstream".toByteArray(Charsets.ISO_8859_1)
    val contentId = obj(streamHeader + compressed + streamFooter)

    val pagesId = obj("PLACEHOLDER")
    val pageId = obj("PLACEHOLDER")
    parts[pagesId - 1] = "<< /Type /Pages /Kids [$pageId 0 R] /Count 1 >>"
        .toByteArray(Charsets.ISO_8859_1)
    parts[pageId - 1] = (
        "<< /Type /Page /Parent $pagesId 0 R " +
            "/MediaBox [0 0 ${n(PAGE_WIDTH)} ${n(PAGE_HEIGHT)}] " +
            "/Contents $contentId 0 R " +
            "/Resources << /Font << /F1 $font1 0 R /F2 $font2 0 R >> " +
            "/ExtGState << /GS06 $gs 0 R >> >> >>"
        ).toByteArray(Charsets.ISO_8859_1)

    val catalogId = obj("<< /Type /Catalog /Pages $pagesId 0 R >>")

    val baos = ByteArrayOutputStream()
    baos.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".toByteArray(Charsets.ISO_8859_1))
    val offsets = IntArray(parts.size)
    for (i in parts.indices) {
        offsets[i] = baos.size()
        baos.write("${i + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
        baos.write(parts[i])
        baos.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
    }
    val xrefPos = baos.size()
    baos.write("xref\n0 ${parts.size + 1}\n".toByteArray(Charsets.ISO_8859_1))
    baos.write("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
    for (off in offsets) {
        baos.write("%010d 00000 n \n".format(off).toByteArray(Charsets.ISO_8859_1))
    }
    baos.write(
        ("trailer\n<< /Size ${parts.size + 1} /Root $catalogId 0 R >>\n" +
            "startxref\n$xrefPos\n%%EOF\n").toByteArray(Charsets.ISO_8859_1),
    )

    return ExportFile(
        filename = "${sanitizeExportFilename(home.name)}.pdf",
        bytes = baos.toByteArray(),
    )
}
