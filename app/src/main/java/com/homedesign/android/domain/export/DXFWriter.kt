package com.homedesign.android.domain.export

import kotlin.math.atan2
import kotlin.math.hypot

typealias DxfXY = Pair<Double, Double>

/**
 * Tagged ASCII group-code stream wrapped by the R2018/AC1032 shell.
 * Port of web `DXFWriter.ts` / iOS `DXFWriter.swift`.
 */
class DXFWriter {
    private var s = StringBuilder()
    private var entities = StringBuilder()
    private var blocks = StringBuilder()
    private val layers = mutableListOf<LayerSpec>()
    private val blockRecords = mutableListOf<BlockRec>()

    private var extMinX = Double.POSITIVE_INFINITY
    private var extMinY = Double.POSITIVE_INFINITY
    private var extMaxX = Double.NEGATIVE_INFINITY
    private var extMaxY = Double.NEGATIVE_INFINITY
    private var hasExtents = false

    private var nextHandle = 0x200
    private val modelSpaceRecord = DXF2018Shell.modelSpaceRecord
    private val hLayerTable = DXF2018Shell.layerTable
    private val hStyleTable = DXF2018Shell.styleTable
    private val hDimStyleTable = DXF2018Shell.dimStyleTable
    private val hBlockRecTable = DXF2018Shell.blockRecordTable

    private val styleRecordHandles = mutableMapOf<String, String>()
    private var dimensionCount = 0
    private var ownerOverride: String? = null

    data class LayerSpec(val name: String, val aci: Int, val lineweight: Int)
    private data class BlockRec(val name: String, val handle: String)

    private fun grow(x: Double, y: Double) {
        if (ownerOverride != null) return
        extMinX = minOf(extMinX, x)
        extMaxX = maxOf(extMaxX, x)
        extMinY = minOf(extMinY, y)
        extMaxY = maxOf(extMaxY, y)
        hasExtents = true
    }

    private fun handle(): String {
        val h = nextHandle.toString(16).uppercase()
        nextHandle += 1
        return h
    }

    private val currentOwner: String
        get() = ownerOverride ?: modelSpaceRecord

    companion object {
        const val annotationStyle = "HD-ANNO"
        const val dimensionStyle = "HD-DIM"
        const val dimArrowSize = 125.0
        const val dimExtOffset = 60.0
        const val dimExtBeyond = 125.0
        const val dimTextHeight = 125.0
        const val dimTextGap = 60.0

        fun fmt(d: Double): String {
            if (!d.isFinite()) return "0.0000"
            return String.format(java.util.Locale.US, "%.4f", d)
        }

        fun escapeUnicode(t: String): String {
            val translit = t
                .replace('\u2032', '\'')
                .replace('\u2033', '"')
                .replace('\u00D7', 'x')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u00A0', ' ')
            if (translit.all { it.code <= 127 }) return translit
            val out = StringBuilder()
            var i = 0
            while (i < translit.length) {
                val cp = translit.codePointAt(i)
                if (cp <= 127) {
                    out.append(cp.toChar())
                } else if (cp <= 0xffff) {
                    out.append("\\U+").append(cp.toString(16).uppercase().padStart(4, '0'))
                } else {
                    val hi = 0xd800 + ((cp - 0x10000) shr 10)
                    val lo = 0xdc00 + ((cp - 0x10000) and 0x3ff)
                    out.append("\\U+").append(hi.toString(16).uppercase().padStart(4, '0'))
                    out.append("\\U+").append(lo.toString(16).uppercase().padStart(4, '0'))
                }
                i += Character.charCount(cp)
            }
            return out.toString()
        }

        fun bulgeForSweep(sweep: Double): Double = kotlin.math.tan(sweep / 4.0)

        /** Drop blank group-code slots while keeping empty VALUES. */
        fun compactDxf(src: String): String {
            val lines = src.replace("\r\n", "\n").replace("\r", "\n").split("\n")
            val out = ArrayList<String>(lines.size)
            var i = 0
            while (i < lines.size) {
                val raw = lines[i].trim()
                if (raw.isEmpty()) {
                    i += 1
                    continue
                }
                if (raw.toIntOrNull() != null) {
                    out.add(raw)
                    out.add(if (i + 1 < lines.size) lines[i + 1] else "")
                    i += 2
                    continue
                }
                i += 1
            }
            return out.joinToString("\n", postfix = "\n")
        }
    }

    private fun fmt(d: Double): String = Companion.fmt(d)

    private fun pair(code: Int, value: String) {
        s.append(code).append('\n').append(value).append('\n')
    }

    private fun pt(x: Double, y: Double, base: Int = 10) {
        grow(x, y)
        pair(base, fmt(x))
        pair(base + 10, fmt(y))
        pair(base + 20, "0.0")
    }

    private fun basePoint() {
        pair(10, "0.0")
        pair(20, "0.0")
        pair(30, "0.0")
    }

    private fun entityHead(type: String, layer: String, owner: String) {
        pair(0, type)
        pair(5, handle())
        pair(330, owner)
        pair(100, "AcDbEntity")
        pair(8, layer)
    }

    private fun render(body: () -> Unit): String {
        val saved = s
        s = StringBuilder()
        body()
        val captured = s.toString()
        s = saved
        return captured
    }

    fun addLayer(name: String, aci: Int, lineweight: Int = -3) {
        layers.add(LayerSpec(name, aci, lineweight))
    }

    fun addLine(a: DxfXY, b: DxfXY, layer: String) {
        entities.append(render { line(a, b, layer, currentOwner) })
    }

    private fun line(a: DxfXY, b: DxfXY, layer: String, owner: String) {
        entityHead("LINE", layer, owner)
        pair(100, "AcDbLine")
        pt(a.first, a.second)
        pt(b.first, b.second, 11)
    }

    fun addLWPolyline(
        pts: List<DxfXY>,
        layer: String,
        closed: Boolean,
        bulges: List<Double>? = null,
    ) {
        entities.append(render { polyline(pts, layer, closed, bulges, currentOwner) })
    }

    private fun polyline(
        pts: List<DxfXY>,
        layer: String,
        closed: Boolean,
        bulges: List<Double>?,
        owner: String,
    ) {
        entityHead("LWPOLYLINE", layer, owner)
        pair(100, "AcDbPolyline")
        pair(90, pts.size.toString())
        pair(70, if (closed) "1" else "0")
        for (i in pts.indices) {
            val p = pts[i]
            grow(p.first, p.second)
            pair(10, fmt(p.first))
            pair(20, fmt(p.second))
            val bulge = bulges?.getOrNull(i)
            if (bulge != null && bulge != 0.0) {
                pair(42, fmt(bulge))
            }
        }
    }

    fun addCircle(c: DxfXY, r: Double, layer: String) {
        entities.append(render { circle(c, r, layer, currentOwner) })
    }

    private fun circle(c: DxfXY, r: Double, layer: String, owner: String) {
        entityHead("CIRCLE", layer, owner)
        pair(100, "AcDbCircle")
        pt(c.first, c.second)
        pair(40, fmt(r))
    }

    fun addArc(c: DxfXY, r: Double, start: Double, end: Double, layer: String) {
        entities.append(render { arc(c, r, start, end, layer, currentOwner) })
    }

    private fun arc(c: DxfXY, r: Double, start: Double, end: Double, layer: String, owner: String) {
        entityHead("ARC", layer, owner)
        pair(100, "AcDbCircle")
        pt(c.first, c.second)
        pair(40, fmt(r))
        pair(100, "AcDbArc")
        pair(50, fmt(start))
        pair(51, fmt(end))
    }

    fun addText(
        t: String,
        at: DxfXY,
        height: Double,
        layer: String,
        rotation: Double = 0.0,
        style: String = annotationStyle,
    ) {
        val oneLine = escapeUnicode(t.replace("\n", " ").replace("\r", " "))
        entities.append(render { text(oneLine, at, height, layer, rotation, style, currentOwner) })
    }

    private fun text(
        t: String,
        at: DxfXY,
        height: Double,
        layer: String,
        rotation: Double,
        style: String,
        owner: String,
    ) {
        entityHead("TEXT", layer, owner)
        pair(100, "AcDbText")
        pt(at.first, at.second)
        pair(40, fmt(height))
        pair(1, t)
        if (rotation != 0.0) pair(50, fmt(rotation))
        pair(7, style)
        pair(72, "1")
        pt(at.first, at.second, 11)
        pair(100, "AcDbText")
        pair(73, "2")
    }

    fun addMText(
        t: String,
        at: DxfXY,
        height: Double,
        layer: String,
        width: Double = 0.0,
        rotation: Double = 0.0,
        style: String = annotationStyle,
    ) {
        val escaped = escapeUnicode(
            t.replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\P"),
        )
        entities.append(
            render { mtext(escaped, at, height, layer, width, rotation, style, currentOwner) },
        )
    }

    private fun mtext(
        escaped: String,
        at: DxfXY,
        height: Double,
        layer: String,
        width: Double,
        rotation: Double,
        style: String,
        owner: String,
    ) {
        entityHead("MTEXT", layer, owner)
        pair(100, "AcDbMText")
        pt(at.first, at.second)
        pair(40, fmt(height))
        if (width > 0) pair(41, fmt(width))
        pair(71, "5")
        pair(1, escaped)
        pair(7, style)
        if (rotation != 0.0) pair(50, fmt(rotation))
    }

    private fun solid(p1: DxfXY, p2: DxfXY, p3: DxfXY, layer: String, owner: String) {
        entityHead("SOLID", layer, owner)
        pair(100, "AcDbTrace")
        pt(p1.first, p1.second)
        pt(p2.first, p2.second, 11)
        pt(p3.first, p3.second, 12)
        pt(p3.first, p3.second, 13)
    }

    fun addHatch(pts: List<DxfXY>, layer: String, scale: Double = 25.0) {
        if (pts.size < 3) return
        entities.append(
            render {
                entityHead("HATCH", layer, currentOwner)
                pair(100, "AcDbHatch")
                pair(10, "0.0")
                pair(20, "0.0")
                pair(30, "0.0")
                pair(210, "0.0")
                pair(220, "0.0")
                pair(230, "1.0")
                pair(2, "ANSI31")
                pair(70, "0")
                pair(71, "0")
                pair(91, "1")
                pair(92, "3")
                pair(72, "0")
                pair(73, "1")
                pair(93, pts.size.toString())
                for (p in pts) {
                    grow(p.first, p.second)
                    pair(10, fmt(p.first))
                    pair(20, fmt(p.second))
                }
                pair(97, "0")
                pair(75, "1")
                pair(76, "1")
                pair(52, "0.0")
                pair(41, fmt(scale))
                pair(77, "0")
                pair(78, "1")
                pair(53, "45.0")
                pair(43, "0.0")
                pair(44, "0.0")
                val o = (3.175 * scale) / 1.4142135623730951
                pair(45, fmt(-o))
                pair(46, fmt(o))
                pair(79, "0")
                pair(98, "0")
            },
        )
    }

    fun addAlignedDimension(
        from: DxfXY,
        to: DxfXY,
        linePt: DxfXY,
        text: String?,
        layer: String,
    ) {
        val blockName = "*D$dimensionCount"
        dimensionCount += 1
        val recordHandle = handle()
        blockRecords.add(BlockRec(blockName, recordHandle))

        val dx = to.first - from.first
        val dy = to.second - from.second
        val len = hypot(dx, dy)
        val dir: DxfXY = if (len > 0) (dx / len) to (dy / len) else 1.0 to 0.0
        var n: DxfXY = (-dir.second) to dir.first
        var h = (linePt.first - from.first) * n.first + (linePt.second - from.second) * n.second
        if (h < 0) {
            n = (-n.first) to (-n.second)
            h = -h
        }

        val asz = dimArrowSize
        val pA: DxfXY = (from.first + n.first * h) to (from.second + n.second * h)
        val pB: DxfXY = (to.first + n.first * h) to (to.second + n.second * h)
        val displayText = escapeUnicode(text.orEmpty())

        var rotation = atan2(dir.second, dir.first) * 180.0 / Math.PI
        if (rotation > 90 || rotation <= -90) {
            rotation += if (rotation > 0) -180 else 180
        }
        val tOff = dimTextGap + dimTextHeight / 2
        val textAt: DxfXY =
            ((pA.first + pB.first) / 2 + n.first * tOff) to ((pA.second + pB.second) / 2 + n.second * tOff)

        blocks.append(
            render {
                pair(0, "BLOCK")
                pair(5, handle())
                pair(330, recordHandle)
                pair(100, "AcDbEntity")
                pair(8, layer)
                pair(100, "AcDbBlockBegin")
                pair(2, blockName)
                pair(70, "1")
                basePoint()
                pair(3, blockName)
                pair(1, "")
            },
        )
        blocks.append(
            render {
                val exo = dimExtOffset
                val exb = dimExtBeyond
                line(
                    (from.first + n.first * exo) to (from.second + n.second * exo),
                    (from.first + n.first * (h + exb)) to (from.second + n.second * (h + exb)),
                    layer,
                    recordHandle,
                )
                line(
                    (to.first + n.first * exo) to (to.second + n.second * exo),
                    (to.first + n.first * (h + exb)) to (to.second + n.second * (h + exb)),
                    layer,
                    recordHandle,
                )
                line(pA, pB, layer, recordHandle)
                if (len > asz * 2) {
                    solid(
                        pA,
                        (pA.first + dir.first * asz + n.first * (asz / 6)) to
                            (pA.second + dir.second * asz + n.second * (asz / 6)),
                        (pA.first + dir.first * asz - n.first * (asz / 6)) to
                            (pA.second + dir.second * asz - n.second * (asz / 6)),
                        layer,
                        recordHandle,
                    )
                    solid(
                        pB,
                        (pB.first - dir.first * asz + n.first * (asz / 6)) to
                            (pB.second - dir.second * asz + n.second * (asz / 6)),
                        (pB.first - dir.first * asz - n.first * (asz / 6)) to
                            (pB.second - dir.second * asz - n.second * (asz / 6)),
                        layer,
                        recordHandle,
                    )
                }
                if (displayText.isNotEmpty()) {
                    mtext(
                        displayText,
                        textAt,
                        dimTextHeight,
                        layer,
                        0.0,
                        rotation,
                        annotationStyle,
                        recordHandle,
                    )
                }
            },
        )
        blocks.append(
            render {
                pair(0, "ENDBLK")
                pair(5, handle())
                pair(330, recordHandle)
                pair(100, "AcDbEntity")
                pair(8, layer)
                pair(100, "AcDbBlockEnd")
            },
        )
        entities.append(
            render {
                entityHead("DIMENSION", layer, currentOwner)
                pair(100, "AcDbDimension")
                pair(280, "0")
                pair(2, blockName)
                pair(3, dimensionStyle)
                pt(linePt.first, linePt.second)
                pt(textAt.first, textAt.second, 11)
                pair(70, "33")
                pair(71, "5")
                pair(1, displayText)
                pair(100, "AcDbAlignedDimension")
                pt(from.first, from.second, 13)
                pt(to.first, to.second, 14)
            },
        )
    }

    fun addInsert(
        block: String,
        at: DxfXY,
        xscale: Double,
        yscale: Double,
        rotation: Double,
        layer: String,
    ) {
        entities.append(render { insert(block, at, xscale, yscale, rotation, layer, currentOwner) })
    }

    private fun insert(
        block: String,
        at: DxfXY,
        xscale: Double,
        yscale: Double,
        rotation: Double,
        layer: String,
        owner: String,
    ) {
        entityHead("INSERT", layer, owner)
        pair(100, "AcDbBlockReference")
        pair(2, block)
        pt(at.first, at.second)
        if (xscale != 1.0) pair(41, fmt(xscale))
        if (yscale != 1.0) pair(42, fmt(yscale))
        if (rotation != 0.0) pair(50, fmt(rotation))
    }

    fun addBlock(name: String, body: (DXFWriter) -> Unit) {
        val recordHandle = handle()
        blockRecords.add(BlockRec(name, recordHandle))

        val child = DXFWriter()
        child.nextHandle = nextHandle + 0x1000
        child.ownerOverride = recordHandle
        body(child)
        nextHandle = child.nextHandle

        blocks.append(
            render {
                pair(0, "BLOCK")
                pair(5, handle())
                pair(330, recordHandle)
                pair(100, "AcDbEntity")
                pair(8, "0")
                pair(100, "AcDbBlockBegin")
                pair(2, name)
                pair(70, "0")
                basePoint()
                pair(3, name)
                pair(1, "")
            },
        )
        blocks.append(child.entities)
        blocks.append(
            render {
                pair(0, "ENDBLK")
                pair(5, handle())
                pair(330, recordHandle)
                pair(100, "AcDbEntity")
                pair(8, "0")
                pair(100, "AcDbBlockEnd")
            },
        )
    }

    fun data(): ByteArray {
        val dynLayers = render {
            for (layer in layers) {
                pair(0, "LAYER")
                pair(5, handle())
                pair(330, hLayerTable)
                pair(100, "AcDbSymbolTableRecord")
                pair(100, "AcDbLayerTableRecord")
                pair(2, layer.name)
                pair(70, "0")
                pair(62, layer.aci.toString())
                pair(6, "Continuous")
                pair(370, layer.lineweight.toString())
                pair(390, DXF2018Shell.layerPlotStyle)
                pair(347, DXF2018Shell.layerMaterial)
            }
        }
        styleRecordHandles["Standard"] = DXF2018Shell.standardStyleRecord
        val dynStyles = render {
            val recordHandle = handle()
            styleRecordHandles[annotationStyle] = recordHandle
            pair(0, "STYLE")
            pair(5, recordHandle)
            pair(330, hStyleTable)
            pair(100, "AcDbSymbolTableRecord")
            pair(100, "AcDbTextStyleTableRecord")
            pair(2, annotationStyle)
            pair(70, "0")
            pair(40, "0.0")
            pair(41, "1.0")
            pair(50, "0.0")
            pair(71, "0")
            pair(42, "2.5")
            pair(3, "arial.ttf")
            pair(4, "")
        }
        val dynDimStyles = render {
            pair(0, "DIMSTYLE")
            pair(105, handle())
            pair(330, hDimStyleTable)
            pair(100, "AcDbSymbolTableRecord")
            pair(100, "AcDbDimStyleTableRecord")
            pair(2, dimensionStyle)
            pair(70, "0")
            pair(41, fmt(dimArrowSize))
            pair(42, fmt(dimExtOffset))
            pair(44, fmt(dimExtBeyond))
            pair(140, fmt(dimTextHeight))
            pair(147, fmt(dimTextGap))
            pair(271, "2")
            pair(340, styleRecordHandles[annotationStyle] ?: "0")
        }
        val dynBlockRecords = render {
            for (rec in blockRecords) {
                blockRecord(rec.name, rec.handle)
            }
        }

        val cx = if (hasExtents) (extMinX + extMaxX) / 2 else 0.0
        val cy = if (hasExtents) (extMinY + extMaxY) / 2 else 0.0
        val vh = if (hasExtents) maxOf(extMaxY - extMinY, 1.0) * 1.1 else 1000.0
        val vw = if (hasExtents) maxOf(extMaxX - extMinX, 1.0) * 1.1 else 1500.0

        var out = DXF2018Shell.header
            .replace("@HANDSEED@", (nextHandle + 0x1000).toString(16).uppercase())
            .replace("@EXTMINX@", fmt(if (hasExtents) extMinX else 0.0))
            .replace("@EXTMINY@", fmt(if (hasExtents) extMinY else 0.0))
            .replace("@EXTMAXX@", fmt(if (hasExtents) extMaxX else 1000.0))
            .replace("@EXTMAXY@", fmt(if (hasExtents) extMaxY else 1000.0))
        out += DXF2018Shell.classes
        out += DXF2018Shell.tablesPart1
            .replace("@VPCX@", fmt(cx))
            .replace("@VPCY@", fmt(cy))
            .replace("@VPH@", fmt(vh))
            .replace("@VPASPECT@", fmt(vw / vh))
            .replace("@LAYERCOUNT@", (DXF2018Shell.templateLayerCount + layers.size).toString())
        out += dynLayers
        out += DXF2018Shell.tablesPart2.replace(
            "@STYLECOUNT@",
            (DXF2018Shell.templateStyleCount + 1).toString(),
        )
        out += dynStyles
        out += DXF2018Shell.tablesPart3.replace(
            "@DIMSTYLECOUNT@",
            (DXF2018Shell.templateDimStyleCount + 1).toString(),
        )
        out += dynDimStyles
        out += DXF2018Shell.tablesPart4.replace(
            "@BLOCKRECCOUNT@",
            (DXF2018Shell.templateBlockRecordCount + blockRecords.size).toString(),
        )
        out += dynBlockRecords
        out += DXF2018Shell.tablesPart5
        out += DXF2018Shell.blocksPrefix
        out += blocks
        out += "0\nENDSEC\n"
        out += "0\nSECTION\n2\nENTITIES\n"
        out += entities
        out += "0\nENDSEC\n"
        out += DXF2018Shell.objects
        out += "0\nEOF\n"
        return compactDxf(out).toByteArray(Charsets.UTF_8)
    }

    private fun blockRecord(name: String, h: String) {
        pair(0, "BLOCK_RECORD")
        pair(5, h)
        pair(330, hBlockRecTable)
        pair(100, "AcDbSymbolTableRecord")
        pair(100, "AcDbBlockTableRecord")
        pair(2, name)
        pair(340, "0")
        pair(70, "0")
        pair(280, "1")
        pair(281, "0")
    }
}
