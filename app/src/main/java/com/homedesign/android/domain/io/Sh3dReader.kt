package com.homedesign.android.domain.io

import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Level
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Sash
import com.homedesign.android.domain.model.Wall
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `.sh3d` reader — ZIP + `Home.xml` into [Home], then optional mesh/icon
 * extraction into a cache dir (iOS `SH3DReader` + `SH3DMeshExtraction`).
 * Ported from iOS / Python (walls, rooms, furniture, openings, levels).
 */
sealed class SH3DReaderException(message: String) : Exception(message) {
    class CorruptedArchive(message: String) : SH3DReaderException(message)
    class MissingHomeXml(message: String = "archive is missing Home.xml") : SH3DReaderException(message)
    class MalformedXml(message: String) : SH3DReaderException(message)
    class MissingHomeRoot(found: String) : SH3DReaderException("wrong root <$found>; expected <home>")
    class MissingRequiredAttribute(element: String, attribute: String) :
        SH3DReaderException("<$element> is missing required attribute \"$attribute\"")
}

object SH3DReader {

    /**
     * @param cacheDirectory destination for extracted meshes/icons; when null,
     *   a fresh dir under `java.io.tmpdir/HomeMeshes/` is created (OS-cleaned).
     *   Extraction failures are swallowed — plan geometry still loads and the
     *   3D path falls back to procedural furniture.
     */
    fun read(archiveBytes: ByteArray, cacheDirectory: File? = null): Home {
        val entries = try {
            unwrapSingleRootFolder(readZip(archiveBytes))
        } catch (e: InvalidArchiveException) {
            throw SH3DReaderException.CorruptedArchive(e.message ?: "not a ZIP archive")
        }
        val xmlBytes = entries["Home.xml"]
            ?: throw SH3DReaderException.MissingHomeXml()
        var home = parseHomeXml(xmlBytes)
        val cache = cacheDirectory ?: File(
            System.getProperty("java.io.tmpdir"),
            "HomeMeshes/${UUID.randomUUID()}",
        )
        val extracted = runCatching {
            SH3DMeshExtraction.extractAll(archiveBytes, cache)
        }.getOrDefault(emptyMap())
        if (extracted.isNotEmpty()) {
            home = attachModelURLs(home, extracted)
        }
        return home
    }

    /** Wire `modelRef` → extracted absolute path onto each piece's [HomePieceOfFurniture.modelURL]. */
    fun attachModelURLs(home: Home, extracted: Map<String, String>): Home {
        fun resolve(piece: HomePieceOfFurniture): HomePieceOfFurniture {
            val ref = piece.modelRef ?: return piece
            val path = extracted[ref] ?: return piece
            return piece.copy(modelURL = path)
        }
        return home.copy(
            extractedAssetURLs = extracted,
            furniture = home.furniture.map(::resolve),
            doorsAndWindows = home.doorsAndWindows.map { it.copy(piece = resolve(it.piece)) },
        )
    }

    fun parseHomeXml(xmlBytes: ByteArray): Home {
        val root = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
                isExpandEntityReferences = false
            }
            // Harden against XXE where the implementation supports the feature.
            runCatching {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            doc.documentElement
        } catch (e: SH3DReaderException) {
            throw e
        } catch (e: Exception) {
            throw SH3DReaderException.MalformedXml(e.message ?: "XML parse failed")
        }
        if (root == null || root.tagName != "home") {
            throw SH3DReaderException.MissingHomeRoot(root?.tagName ?: "empty")
        }

        val wallHeight = root.attrDouble("wallHeight")
            ?: throw SH3DReaderException.MissingRequiredAttribute("home", "wallHeight")

        val properties = linkedMapOf<String, String>()
        val furnitureVisible = mutableListOf<String>()
        val levels = mutableListOf<Level>()
        val walls = mutableListOf<Wall>()
        val rooms = mutableListOf<Room>()
        val furniture = mutableListOf<HomePieceOfFurniture>()
        val doorsAndWindows = mutableListOf<HomeDoorOrWindow>()

        fun walk(parent: Element, groupId: String?) {
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val el = node as Element
                when (el.tagName) {
                    "property" -> {
                        val key = el.attr("name")
                        if (key != null) properties[key] = el.attr("value").orEmpty()
                    }
                    "furnitureVisibleProperty" -> {
                        el.attr("name")?.let { furnitureVisible.add(it) }
                    }
                    "level" -> parseLevel(el)?.let { levels.add(it) }
                    "wall" -> parseWall(el, wallHeight)?.let { walls.add(it) }
                    "room" -> rooms.add(parseRoom(el))
                    "pieceOfFurniture", "light" -> {
                        parsePiece(el)?.let { piece ->
                            furniture.add(
                                if (groupId.isNullOrEmpty()) piece else piece.copy(groupID = groupId),
                            )
                        }
                    }
                    "doorOrWindow" -> parseDoorOrWindow(el)?.let { doorsAndWindows.add(it) }
                    "furnitureGroup" -> walk(el, el.attr("id") ?: groupId)
                    else -> {
                        // environment / compass / cameras / shelfUnit / labels / … ignored
                    }
                }
            }
        }
        walk(root, null)

        val home = Home(
            version = root.attr("version"),
            name = root.attr("name"),
            wallHeight = wallHeight,
            activeCamera = root.attr("camera"),
            basePlanLocked = root.attrBool("basePlanLocked", false),
            properties = properties,
            furnitureVisibleProperties = furnitureVisible,
            selectedLevelID = root.attr("selectedLevel"),
            levels = levels,
            walls = walls,
            rooms = rooms,
            furniture = furniture,
            doorsAndWindows = doorsAndWindows,
        )
        return normalizeLevels(home)
    }

    private fun normalizeLevels(home: Home): Home {
        val levels = if (home.levels.isEmpty()) {
            listOf(
                Level(
                    id = HomeFactory.SYNTH_LEVEL_ID,
                    name = "Ground floor",
                    elevation = 0.0,
                    floorThickness = HomeFactory.DEFAULT_FLOOR_THICKNESS_CM,
                    height = home.wallHeight,
                    elevationIndex = 0,
                    visible = true,
                    viewable = true,
                ),
            )
        } else {
            home.levels
        }
        val defaultId = if (levels.any { it.id == HomeFactory.SYNTH_LEVEL_ID }) {
            HomeFactory.SYNTH_LEVEL_ID
        } else {
            levels.minWith(compareBy({ it.elevationIndex }, { it.elevation })).id
        }
        fun String?.orDefault(): String = this ?: defaultId
        return home.copy(
            levels = levels,
            selectedLevelID = home.selectedLevelID ?: defaultId,
            walls = home.walls.map { it.copy(level = it.level.orDefault()) },
            rooms = home.rooms.map { it.copy(level = it.level.orDefault()) },
            furniture = home.furniture.map { it.copy(level = it.level.orDefault()) },
            doorsAndWindows = home.doorsAndWindows.map {
                it.copy(piece = it.piece.copy(level = it.piece.level.orDefault()))
            },
        )
    }

    private fun parseLevel(el: Element): Level? {
        val id = el.attr("id") ?: return null
        return Level(
            id = id,
            name = el.attr("name"),
            elevation = el.attrDouble("elevation") ?: 0.0,
            floorThickness = el.attrDouble("floorThickness") ?: HomeFactory.DEFAULT_FLOOR_THICKNESS_CM,
            height = el.attrDouble("height") ?: HomeFactory.DEFAULT_WALL_HEIGHT_CM,
            elevationIndex = el.attrInt("elevationIndex") ?: 0,
            visible = el.attrBool("visible", true),
            viewable = el.attrBool("viewable", true),
        )
    }

    private fun parseWall(el: Element, defaultHeight: Double): Wall? {
        val id = el.attr("id") ?: return null
        val sx = el.attrDouble("xStart") ?: return null
        val sy = el.attrDouble("yStart") ?: return null
        val ex = el.attrDouble("xEnd") ?: return null
        val ey = el.attrDouble("yEnd") ?: return null
        val thickness = el.attrDouble("thickness") ?: return null
        return Wall(
            id = id,
            startX = sx,
            startY = sy,
            endX = ex,
            endY = ey,
            thickness = thickness,
            height = el.attrDouble("height") ?: defaultHeight,
            atStart = el.attr("wallAtStart"),
            atEnd = el.attr("wallAtEnd"),
            level = el.attr("level"),
            arcExtent = el.attrDouble("arcExtent"),
            heightAtEnd = el.attrDouble("heightAtEnd"),
            pattern = el.attr("pattern"),
            leftSideColor = el.attr("leftSideColor"),
            rightSideColor = el.attr("rightSideColor"),
            topColor = el.attr("topColor"),
            leftSideShininess = el.attrDouble("leftSideShininess"),
            rightSideShininess = el.attrDouble("rightSideShininess"),
            leftSidePattern = el.attr("leftSidePattern"),
            rightSidePattern = el.attr("rightSidePattern"),
        )
    }

    private fun parseRoom(el: Element): Room {
        val points = mutableListOf<Point>()
        val children = el.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            if (child.tagName != "point") continue
            val x = child.attrDouble("x") ?: continue
            val y = child.attrDouble("y") ?: continue
            points.add(Point(x, y))
        }
        val id = el.attr("id").orEmpty().ifEmpty { "room-${points.hashCode()}" }
        return Room(
            id = id,
            points = points,
            name = el.attr("name"),
            areaVisible = el.attrBool("areaVisible", true),
            floorVisible = el.attrBool("floorVisible", true),
            ceilingVisible = el.attrBool("ceilingVisible", true),
            ceilingFlat = el.attrBool("ceilingFlat", false),
            level = el.attr("level"),
            floorColor = el.attr("floorColor"),
            ceilingColor = el.attr("ceilingColor"),
        )
    }

    private fun parsePiece(el: Element): HomePieceOfFurniture? {
        val id = el.attr("id") ?: return null
        val x = el.attrDouble("x") ?: return null
        val y = el.attrDouble("y") ?: return null
        val width = el.attrDouble("width") ?: return null
        val depth = el.attrDouble("depth") ?: return null
        val height = el.attrDouble("height") ?: return null
        return HomePieceOfFurniture(
            id = id,
            catalogID = el.attr("catalogId"),
            name = el.attr("name"),
            creator = el.attr("creator"),
            license = el.attr("license"),
            modelRef = el.attr("model"),
            iconRef = el.attr("icon"),
            x = x,
            y = y,
            elevation = el.attrDouble("elevation") ?: 0.0,
            angle = el.attrDouble("angle") ?: 0.0,
            pitch = el.attrDouble("pitch") ?: 0.0,
            roll = el.attrDouble("roll") ?: 0.0,
            width = width,
            depth = depth,
            height = height,
            widthInPlan = el.attrDouble("widthInPlan"),
            depthInPlan = el.attrDouble("depthInPlan"),
            heightInPlan = el.attrDouble("heightInPlan"),
            color = el.attr("color"),
            movable = el.attrBool("movable", true),
            visible = el.attrBool("visible", true),
            level = el.attr("level"),
            staircaseCutOut = if (el.attr("staircaseCutOutShape") != null) true else null,
            modelMirrored = el.attrBool("modelMirrored", false),
            modelRotation = parseModelRotation(el.attr("modelRotation")),
            lightPower = el.attrDouble("power"),
        )
    }

    /** Home.xml `modelRotation` — 9 space-separated floats, row-major 3×3. */
    private fun parseModelRotation(raw: String?): List<Double>? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size != 9) return null
        val floats = parts.mapNotNull { it.toDoubleOrNull() }
        return if (floats.size == 9) floats else null
    }

    private fun parseDoorOrWindow(el: Element): HomeDoorOrWindow? {
        val piece = parsePiece(el) ?: return null
        val sashes = mutableListOf<Sash>()
        val children = el.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            if (child.tagName != "sash") continue
            val xAxis = child.attrDouble("xAxis") ?: continue
            val yAxis = child.attrDouble("yAxis") ?: continue
            val width = child.attrDouble("width") ?: continue
            val start = child.attrDouble("startAngle") ?: continue
            val end = child.attrDouble("endAngle") ?: continue
            sashes.add(Sash(xAxis, yAxis, width, start, end))
        }
        return HomeDoorOrWindow(
            piece = piece,
            wallThickness = el.attrDouble("wallThickness"),
            wallDistance = el.attrDouble("wallDistance"),
            wallWidth = el.attrDouble("wallWidth"),
            wallLeft = el.attrDouble("wallLeft"),
            wallHeight = el.attrDouble("wallHeight"),
            wallTop = el.attrDouble("wallTop"),
            wallCutOutOnBothSides = el.attrBool("wallCutOutOnBothSides", false),
            widthDepthDeformable = el.attrBool("widthDepthDeformable", true),
            cutoutShape = el.attr("cutOutShape"),
            sashes = sashes,
        )
    }
}

private fun Element.attr(name: String): String? =
    if (hasAttribute(name)) getAttribute(name) else null

private fun Element.attrDouble(name: String): Double? =
    attr(name)?.toDoubleOrNull()

private fun Element.attrInt(name: String): Int? =
    attr(name)?.toIntOrNull()

private fun Element.attrBool(name: String, default: Boolean): Boolean {
    val raw = attr(name) ?: return default
    return raw.equals("true", ignoreCase = true)
}
