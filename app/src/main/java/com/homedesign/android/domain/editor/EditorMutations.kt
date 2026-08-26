package com.homedesign.android.domain.editor

import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.catalog.StructureCatalog
import com.homedesign.android.domain.geom.FurnitureGroupMove
import com.homedesign.android.domain.geom.FurnitureReplace
import com.homedesign.android.domain.geom.FurnitureSnap
import com.homedesign.android.domain.geom.OpeningKind
import com.homedesign.android.domain.geom.OpeningMutation
import com.homedesign.android.domain.geom.PlacementDefaults
import com.homedesign.android.domain.geom.RectangleRoom
import com.homedesign.android.domain.geom.RoomContainment
import com.homedesign.android.domain.geom.RoomDetection
import com.homedesign.android.domain.geom.RoomMutation
import com.homedesign.android.domain.geom.RoomSizeMutation
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.BorderKind
import com.homedesign.android.domain.geom.RoomStyleMutation
import com.homedesign.android.domain.geom.WallClearance
import com.homedesign.android.domain.geom.WallJoinInference
import com.homedesign.android.domain.geom.WallMutation
import com.homedesign.android.domain.geom.WallStyleMutation
import com.homedesign.android.domain.geom.WallTJunction
import com.homedesign.android.domain.geom.ResizeSide
import com.homedesign.android.domain.geom.add
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.geom.dist
import com.homedesign.android.domain.geom.furnitureSnapToWallCM
import com.homedesign.android.domain.geom.minDrawnWallCM
import com.homedesign.android.domain.geom.normalize
import com.homedesign.android.domain.geom.projectTOnWall
import com.homedesign.android.domain.geom.scale
import com.homedesign.android.domain.geom.sub
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.geom.wallLeftNormal
import com.homedesign.android.domain.geom.wrapFurnitureAngle
import com.homedesign.android.domain.model.Baseboard
import com.homedesign.android.domain.model.CeilingStyle
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.PlanLabel
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.WallTexture
import com.homedesign.android.domain.textures.TexturePreset
import com.homedesign.android.domain.textures.textureFromPreset
import java.util.UUID
import kotlin.math.abs

/** Default SH3D-ish baseboard when the wall-sheet enable chip turns on. */
val DEFAULT_BASEBOARD = Baseboard(thickness = 1.6, height = 7.0, color = "FFF4F1EA")

const val ROOM_LIGHTING_PROP_PREFIX = "roomLighting:"
const val ROOM_LIGHTING_STAGED = "studio"

private fun bumpTopology(home: Home, walls: List<com.homedesign.android.domain.model.Wall>, rooms: List<com.homedesign.android.domain.model.Room>): Home =
    home.copy(walls = walls, rooms = rooms, topologyVersion = home.topologyVersion + 1)

private fun bumpStyle(
    home: Home,
    rooms: List<com.homedesign.android.domain.model.Room> = home.rooms,
    walls: List<com.homedesign.android.domain.model.Wall> = home.walls,
): Home = home.copy(rooms = rooms, walls = walls, styleVersion = home.styleVersion + 1)

private fun splitAndHealTees(walls: List<com.homedesign.android.domain.model.Wall>): List<com.homedesign.android.domain.model.Wall> {
    val split = WallTJunction.splitHostsAtTees(walls).walls
    return WallTJunction.heal(split).walls
}

private fun inferAndReconcile(
    home: Home,
    walls: List<com.homedesign.android.domain.model.Wall>,
    rooms: List<com.homedesign.android.domain.model.Room>,
): Home {
    val inferred = WallJoinInference.infer(walls).walls
    val healed = splitAndHealTees(inferred)
    val nextRooms = RoomDetection.reconcileRooms(healed, rooms, level = home.selectedLevelID)
    return bumpTopology(home, healed, nextRooms)
}

fun applyAddChainWall(home: Home, from: Vec2, to: Vec2, thickness: Double): Home {
    if (dist(from, to) < minDrawnWallCM) return home
    val added = WallMutation.addWall(
        home.walls,
        from,
        to,
        thickness,
        home.wallHeight,
        level = home.selectedLevelID,
    )
    return inferAndReconcile(home, added, home.rooms)
}

fun commitRectangleRoom(
    home: Home,
    from: Vec2,
    to: Vec2,
    thickness: Double = defaultWallThicknessCM,
    minSideCM: Double? = null,
    idPrefix: String? = null,
): Home {
    val added = RectangleRoom.walls(
        from,
        to,
        thickness,
        home.wallHeight,
        level = home.selectedLevelID,
        minSideCM = minSideCM ?: com.homedesign.android.domain.geom.minRectRoomSideCM,
        idPrefix = idPrefix ?: UUID.randomUUID().toString(),
    )
    if (added.isEmpty()) return home
    return inferAndReconcile(home, home.walls + added, home.rooms)
}

fun previewEndpointMove(
    home: Home,
    wallID: String,
    atStart: Boolean,
    newPosition: Vec2,
): Pair<List<com.homedesign.android.domain.model.Wall>, List<com.homedesign.android.domain.model.Room>> {
    val wall = home.walls.find { it.id == wallID }
        ?: return home.walls to home.rooms
    val old = if (atStart) vec(wall.startX, wall.startY) else vec(wall.endX, wall.endY)
    val walls = WallMutation.moveWallEndpoint(home.walls, wallID, atStart, newPosition)
    val rooms = RoomMutation.shiftCoincidentVertices(
        home.rooms,
        old,
        vec(newPosition.x - old.x, newPosition.y - old.y),
    )
    return walls to rooms
}

fun commitGeometryPreview(
    home: Home,
    walls: List<com.homedesign.android.domain.model.Wall>,
    rooms: List<com.homedesign.android.domain.model.Room>,
): Home = inferAndReconcile(home, walls, rooms)

data class DeleteResult(val home: Home, val toast: String?, val selection: Selection)

fun deleteSelection(home: Home, selection: Selection): DeleteResult {
    when (selection) {
        is Selection.Wall, is Selection.Endpoint -> {
            val id = when (selection) {
                is Selection.Wall -> selection.id
                is Selection.Endpoint -> selection.wallID
                else -> return DeleteResult(home, null, selection)
            }
            if (home.walls.none { it.id == id }) return DeleteResult(home, null, selection)
            val walls = WallMutation.removeWall(home.walls, id)
            return DeleteResult(
                inferAndReconcile(home, walls, home.rooms),
                "Wall deleted",
                Selection.None,
            )
        }
        is Selection.Room -> {
            if (home.rooms.none { it.id == selection.id }) return DeleteResult(home, null, selection)
            return DeleteResult(
                bumpTopology(home, home.walls, RoomMutation.remove(home.rooms, selection.id)),
                "Room deleted",
                Selection.None,
            )
        }
        is Selection.Furniture -> {
            if (home.furniture.none { it.id == selection.id }) return DeleteResult(home, null, selection)
            return DeleteResult(
                home.copy(
                    furniture = home.furniture.filter { it.id != selection.id },
                    topologyVersion = home.topologyVersion + 1,
                ),
                "Furniture deleted",
                Selection.None,
            )
        }
        is Selection.MultiFurniture -> {
            val drop = selection.ids.toSet()
            val furniture = home.furniture.filter { it.id !in drop }
            if (furniture.size == home.furniture.size) return DeleteResult(home, null, selection)
            val n = home.furniture.size - furniture.size
            return DeleteResult(
                home.copy(furniture = furniture, topologyVersion = home.topologyVersion + 1),
                if (n == 1) "Furniture deleted" else "$n pieces deleted",
                Selection.None,
            )
        }
        is Selection.Opening, is Selection.OpeningHandle -> {
            val id = when (selection) {
                is Selection.Opening -> selection.id
                is Selection.OpeningHandle -> selection.id
                else -> return DeleteResult(home, null, selection)
            }
            if (home.doorsAndWindows.none { it.piece.id == id }) return DeleteResult(home, null, selection)
            return DeleteResult(
                home.copy(
                    doorsAndWindows = OpeningMutation.remove(id, home.doorsAndWindows),
                    topologyVersion = home.topologyVersion + 1,
                ),
                "Opening deleted",
                Selection.None,
            )
        }
        is Selection.Annotation -> {
            return if (selection.isLabel) {
                DeleteResult(
                    home.copy(
                        labels = home.labels.filter { it.id != selection.id },
                        topologyVersion = home.topologyVersion + 1,
                    ),
                    "Label deleted",
                    Selection.None,
                )
            } else {
                DeleteResult(
                    home.copy(
                        dimensionLines = home.dimensionLines.filter { it.id != selection.id },
                        topologyVersion = home.topologyVersion + 1,
                    ),
                    "Dimension deleted",
                    Selection.None,
                )
            }
        }
        Selection.None -> return DeleteResult(home, null, selection)
    }
}

fun applyAddOpening(
    home: Home,
    type: OpeningKind,
    wallID: String,
    centerT: Double,
    id: String? = null,
): Home {
    val wall = home.walls.find { it.id == wallID } ?: return home
    val next = OpeningMutation.add(type, centerT, wall, home.doorsAndWindows, id ?: UUID.randomUUID().toString())
    if (next === home.doorsAndWindows || next == home.doorsAndWindows) return home
    return home.copy(doorsAndWindows = next, topologyVersion = home.topologyVersion + 1)
}

fun applyOpeningSlide(
    home: Home,
    openingID: String,
    wallID: String,
    newCenterT: Double,
): Home {
    val wall = home.walls.find { it.id == wallID } ?: return home
    val next = OpeningMutation.slide(openingID, newCenterT, wall, home.doorsAndWindows)
    if (next === home.doorsAndWindows || next == home.doorsAndWindows) return home
    return home.copy(doorsAndWindows = next, topologyVersion = home.topologyVersion + 1)
}

fun applyOpeningResize(
    home: Home,
    openingID: String,
    wallID: String,
    side: ResizeSide,
    newEdgeT: Double,
): Home {
    val wall = home.walls.find { it.id == wallID } ?: return home
    val next = OpeningMutation.resize(openingID, side, newEdgeT, wall, home.doorsAndWindows)
    if (next === home.doorsAndWindows || next == home.doorsAndWindows) return home
    return home.copy(doorsAndWindows = next, topologyVersion = home.topologyVersion + 1)
}

/** Flip hinge = mirroredX; flip swing = mirroredY (web OpeningSheet / applyOpeningFlip). */
fun applyOpeningFlip(
    home: Home,
    openingID: String,
    axis: Char,
): Home {
    if (home.doorsAndWindows.none { it.piece.id == openingID }) return home
    return home.copy(
        doorsAndWindows = home.doorsAndWindows.map { o ->
            if (o.piece.id != openingID) o
            else when (axis) {
                'x', 'X' -> o.copy(mirroredX = !o.mirroredX)
                'y', 'Y' -> o.copy(mirroredY = !o.mirroredY)
                else -> o
            }
        },
        styleVersion = home.styleVersion + 1,
    )
}

fun applyOpeningSlideToPoint(home: Home, openingID: String, wallID: String, x: Double, y: Double): Home {
    val wall = home.walls.find { it.id == wallID } ?: return home
    return applyOpeningSlide(home, openingID, wallID, projectTOnWall(wall, x, y))
}

fun applyPlaceFurniture(home: Home, entry: CatalogEntry, x: Double, y: Double): Home {
    val levelHeight = home.levels.firstOrNull { it.id == home.selectedLevelID }?.height
        ?: home.wallHeight
    var elevation = PlacementDefaults.defaultElevation(entry.name, entry.height, levelHeight)
    var height = entry.height
    var color: String? = null
    // MH-1 — structural semantics: pillars span floor→ceiling; beams hang
    // at the ceiling; mirrors mount on the wall; rugs ship terracotta.
    when {
        StructureCatalog.isFloorToCeiling(entry.id) -> {
            elevation = 0.0
            height = levelHeight
        }
        StructureCatalog.isCeilingHung(entry.id) -> {
            elevation = maxOf(0.0, levelHeight - entry.height)
        }
        StructureCatalog.isWallHung(entry.id) -> {
            elevation = PlacementDefaults.defaultElevation("mirror", entry.height, levelHeight)
        }
    }
    if (entry.id == StructureCatalog.rugID) color = StructureCatalog.rugColor
    var piece = HomePieceOfFurniture(
        id = UUID.randomUUID().toString(),
        catalogID = entry.id,
        name = entry.name,
        x = x,
        y = y,
        elevation = elevation,
        angle = 0.0,
        width = entry.width,
        depth = entry.depth,
        height = height,
        color = color,
        movable = entry.movable,
        visible = true,
        modelMirrored = false,
        level = home.selectedLevelID,
        lightPower = if (entry.category == "Lighting" || entry.category == "Lights") 0.5 else null,
        staircaseCutOut = if (entry.category == "Staircases") true else null,
    )
    FurnitureSnap.snapToWall(piece, wallsOnLevel(home))?.let { snapped ->
        piece = piece.copy(x = snapped.x, y = snapped.y, angle = snapped.angle)
    }
    val clamped = RoomContainment.clampFurnitureToRoom(piece, roomsOnLevel(home))
    piece = piece.copy(x = clamped.x, y = clamped.y)
    val withPiece = home.furniture + piece
    val cleared = WallClearance.resolve(withPiece, wallsOnLevel(home)).furniture
    return home.copy(
        furniture = cleared,
        furnitureRevision = home.furnitureRevision + 1,
    )
}

fun commitFurnitureMove(home: Home, pieceID: String, x: Double, y: Double): Home {
    val piece = home.furniture.find { it.id == pieceID } ?: return home
    var next = piece.copy(x = x, y = y)
    FurnitureSnap.snapToWallKeepAngle(next, wallsOnLevel(home), furnitureSnapToWallCM)?.let { snapped ->
        next = next.copy(x = snapped.x, y = snapped.y)
    }
    val clamped = RoomContainment.clampFurnitureToRoom(next, roomsOnLevel(home))
    next = next.copy(x = clamped.x, y = clamped.y)
    val delta = vec(next.x - piece.x, next.y - piece.y)
    val fan = FurnitureGroupMove.fanDeltaToGroup(home.furniture, pieceID, delta)
        .associateBy { it.id }
    val moved = home.furniture.map { p ->
        when {
            p.id == pieceID -> next
            fan[p.id] != null && p.movable -> {
                val m = fan.getValue(p.id)
                p.copy(x = m.x, y = m.y)
            }
            else -> p
        }
    }
    val cleared = WallClearance.resolve(moved, wallsOnLevel(home)).furniture
    return home.copy(
        furniture = cleared,
        furnitureRevision = home.furnitureRevision + 1,
    )
}

fun applyFurnitureRotate(home: Home, pieceID: String, angle: Double): Home {
    if (home.furniture.none { it.id == pieceID }) return home
    val wrapped = wrapFurnitureAngle(angle)
    return home.copy(
        furniture = home.furniture.map {
            if (it.id == pieceID) it.copy(angle = wrapped) else it
        },
        furnitureRevision = home.furnitureRevision + 1,
    )
}

fun applyWallLength(home: Home, wallID: String, lengthCM: Double): Home {
    val wall = home.walls.find { it.id == wallID } ?: return home
    if (com.homedesign.android.domain.geom.ArcWallGeometry.isCurved(wall)) return home
    val walls = splitAndHealTees(WallMutation.setLength(home.walls, wallID, lengthCM))
    val rooms = RoomDetection.reconcileRooms(walls, home.rooms, level = home.selectedLevelID)
    return bumpTopology(home, walls, rooms)
}

fun applyWallThickness(home: Home, wallID: String, thicknessCM: Double): Home {
    val walls = WallStyleMutation.setThickness(wallID, thicknessCM, home.walls)
    if (walls === home.walls || walls == home.walls) return home
    return bumpTopology(home, walls, home.rooms)
}

fun applyWallHeight(home: Home, wallID: String, heightCM: Double): Home {
    val walls = WallStyleMutation.setHeight(wallID, heightCM, home.walls)
    if (walls == home.walls) return home
    return bumpTopology(home, walls, home.rooms)
}

fun applyWallSideColor(home: Home, wallID: String, side: String, color: String?): Home {
    val walls = when (side) {
        "right" -> WallStyleMutation.setRightSideColor(wallID, color, home.walls)
        else -> WallStyleMutation.setLeftSideColor(wallID, color, home.walls)
    }
    if (walls == home.walls) return home
    return bumpStyle(home, walls = walls)
}

fun applyWallSideTexture(home: Home, wallID: String, side: String, preset: TexturePreset): Home {
    val texture = textureFromPreset(preset)
    val walls = when (side) {
        "right" -> WallStyleMutation.setRightSideTexture(wallID, texture, home.walls)
        else -> WallStyleMutation.setLeftSideTexture(wallID, texture, home.walls)
    }
    if (walls == home.walls) return home
    return bumpTopology(home, walls, home.rooms)
}

fun applyClearWallSideTexture(home: Home, wallID: String, side: String): Home {
    val walls = when (side) {
        "right" -> WallStyleMutation.clearRightSideTexture(wallID, home.walls)
        else -> WallStyleMutation.clearLeftSideTexture(wallID, home.walls)
    }
    if (walls == home.walls) return home
    return bumpTopology(home, walls, home.rooms)
}

fun applyWallPattern(home: Home, wallID: String, hatched: Boolean): Home {
    val walls = WallStyleMutation.setPattern(
        wallID,
        if (hatched) "hatchUp" else null,
        home.walls,
    )
    if (walls == home.walls) return home
    return bumpStyle(home, walls = walls)
}

fun applyWallGlass(home: Home, wallID: String, glass: Boolean): Home {
    val walls = WallStyleMutation.setGlass(wallID, glass, home.walls)
    if (walls == home.walls) return home
    return bumpStyle(home, walls = walls)
}

fun applyWallBaseboard(home: Home, wallID: String, side: String, baseboard: Baseboard?): Home {
    val walls = when (side) {
        "right" -> WallStyleMutation.setRightSideBaseboard(wallID, baseboard, home.walls)
        else -> WallStyleMutation.setLeftSideBaseboard(wallID, baseboard, home.walls)
    }
    if (walls == home.walls) return home
    return bumpTopology(home, walls, home.rooms)
}

fun applyMatchWallProperties(home: Home, sourceWallID: String, targetWallID: String): Home {
    if (sourceWallID == targetWallID) return home
    val walls = WallStyleMutation.matchAttributes(
        sourceWallID,
        listOf(targetWallID),
        home.walls,
        copyLength = true,
    )
    if (walls == home.walls) return home
    return bumpTopology(home, walls, home.rooms).copy(styleVersion = home.styleVersion + 1)
}

fun applyWallSideTextureValue(home: Home, wallID: String, side: String, texture: WallTexture): Home {
    val walls = when (side) {
        "right" -> WallStyleMutation.setRightSideTexture(wallID, texture, home.walls)
        else -> WallStyleMutation.setLeftSideTexture(wallID, texture, home.walls)
    }
    if (walls == home.walls) return home
    val urls = home.extractedAssetURLs.toMutableMap()
    texture.image?.let { path ->
        texture.catalogID?.let { handle -> urls[handle] = path }
        urls[path] = path
    }
    return bumpTopology(home, walls, home.rooms).copy(extractedAssetURLs = urls)
}

fun applyFloorTextureValue(home: Home, roomID: String, texture: WallTexture): Home {
    val rooms = RoomStyleMutation.setFloorTexture(roomID, texture, home.rooms)
    if (rooms == home.rooms) return home
    val urls = home.extractedAssetURLs.toMutableMap()
    texture.image?.let { path ->
        texture.catalogID?.let { handle -> urls[handle] = path }
        urls[path] = path
    }
    return bumpTopology(home, home.walls, rooms).copy(extractedAssetURLs = urls)
}

/** Marks room as staged with a lighting preset key on [Home.properties]. */
fun applyStageRoomLighting(home: Home, roomID: String, preset: String = ROOM_LIGHTING_STAGED): Home {
    if (home.rooms.none { it.id == roomID }) return home
    val key = "$ROOM_LIGHTING_PROP_PREFIX$roomID"
    val props = home.properties.toMutableMap()
    props[key] = preset
    return home.copy(properties = props, styleVersion = home.styleVersion + 1)
}

fun applyFloorColor(home: Home, roomID: String, color: String?): Home {
    val rooms = RoomStyleMutation.setFloorColor(roomID, color, home.rooms)
    if (rooms == home.rooms) return home
    return bumpStyle(home, rooms = rooms)
}

fun applyFloorTexture(home: Home, roomID: String, preset: TexturePreset): Home {
    val rooms = RoomStyleMutation.setFloorTexture(roomID, textureFromPreset(preset), home.rooms)
    if (rooms == home.rooms) return home
    return bumpTopology(home, home.walls, rooms)
}

fun applyClearFloorTexture(home: Home, roomID: String): Home {
    val rooms = RoomStyleMutation.clearFloorTexture(roomID, home.rooms)
    if (rooms == home.rooms) return home
    return bumpTopology(home, home.walls, rooms)
}

fun applyCeilingColor(home: Home, roomID: String, color: String?): Home {
    val rooms = RoomStyleMutation.setCeilingColor(roomID, color, home.rooms)
    if (rooms == home.rooms) return home
    return bumpStyle(home, rooms = rooms)
}

fun applyCeilingTexture(home: Home, roomID: String, preset: TexturePreset): Home {
    val rooms = RoomStyleMutation.setCeilingTexture(roomID, textureFromPreset(preset), home.rooms)
    if (rooms == home.rooms) return home
    return bumpTopology(home, home.walls, rooms)
}

fun applyClearCeilingTexture(home: Home, roomID: String): Home {
    val rooms = RoomStyleMutation.clearCeilingTexture(roomID, home.rooms)
    if (rooms == home.rooms) return home
    return bumpTopology(home, home.walls, rooms)
}

fun applyRoomBorder(home: Home, roomID: String, kind: BorderKind): Home {
    val rooms = RoomStyleMutation.setBorder(roomID, kind, home.rooms)
    if (rooms == home.rooms) return home
    return bumpTopology(home, home.walls, rooms)
}

fun applyCeilingVisible(home: Home, roomID: String, visible: Boolean): Home {
    val rooms = RoomStyleMutation.setCeilingVisible(roomID, visible, home.rooms)
    if (rooms == home.rooms) return home
    return bumpStyle(home, rooms = rooms)
}

fun applyCeilingStyle(home: Home, roomID: String, style: CeilingStyle?): Home {
    val rooms = RoomStyleMutation.setCeilingStyle(roomID, style, home.rooms)
    if (rooms == home.rooms) return home
    return bumpStyle(home, rooms = rooms)
}

fun applyRenameRoom(home: Home, roomID: String, name: String): Home {
    if (home.rooms.none { it.id == roomID }) return home
    val rooms = home.rooms.map {
        if (it.id == roomID) it.copy(name = name.ifBlank { null }) else it
    }
    return bumpTopology(home, home.walls, rooms)
}

fun applyRenameFurniture(home: Home, pieceID: String, name: String): Home {
    if (home.furniture.none { it.id == pieceID }) return home
    return home.copy(
        furniture = home.furniture.map {
            if (it.id == pieceID) it.copy(name = name.ifBlank { it.name }) else it
        },
        furnitureRevision = home.furnitureRevision + 1,
    )
}

/** Swap catalog identity / size; preserve centre (x,y) and angle. */
fun applyReplaceFurniture(
    home: Home,
    pieceID: String,
    entry: CatalogEntry,
    also: List<String> = emptyList(),
): Home {
    val ids = (listOf(pieceID) + also).toSet()
    var changed = false
    val furniture = home.furniture.map { p ->
        if (p.id !in ids) p
        else {
            changed = true
            FurnitureReplace.replace(p, entry)
        }
    }
    if (!changed) return home
    return home.copy(furniture = furniture, furnitureRevision = home.furnitureRevision + 1)
}

fun applyFurnitureSize(home: Home, pieceID: String, widthCM: Double? = null, depthCM: Double? = null): Home {
    if (home.furniture.none { it.id == pieceID }) return home
    val w = widthCM?.takeIf { it >= 1.0 }
    val d = depthCM?.takeIf { it >= 1.0 }
    if (w == null && d == null) return home
    return home.copy(
        furniture = home.furniture.map {
            if (it.id != pieceID) it
            else it.copy(
                width = w ?: it.width,
                depth = d ?: it.depth,
            )
        },
        furnitureRevision = home.furnitureRevision + 1,
        topologyVersion = home.topologyVersion + 1,
    )
}

fun applyOpeningWidth(home: Home, openingID: String, widthCM: Double): Home {
    if (widthCM < 1.0) return home
    if (home.doorsAndWindows.none { it.piece.id == openingID }) return home
    return home.copy(
        doorsAndWindows = home.doorsAndWindows.map { o ->
            if (o.piece.id == openingID) o.copy(piece = o.piece.copy(width = widthCM)) else o
        },
        topologyVersion = home.topologyVersion + 1,
    )
}

fun applyPlaceLabel(home: Home, x: Double, y: Double, text: String): Home {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return home
    val label = PlanLabel(
        id = UUID.randomUUID().toString(),
        x = x,
        y = y,
        text = trimmed,
        angle = 0.0,
        level = home.selectedLevelID,
    )
    return home.copy(
        labels = home.labels + label,
        topologyVersion = home.topologyVersion + 1,
    )
}

fun applyAddDimension(home: Home, from: Vec2, to: Vec2): Home {
    if (dist(from, to) < minDrawnWallCM) return home
    val dim = com.homedesign.android.domain.model.DimensionLine(
        id = UUID.randomUUID().toString(),
        xStart = from.x,
        yStart = from.y,
        xEnd = to.x,
        yEnd = to.y,
        offset = com.homedesign.android.domain.geom.dimensionFaceGapCM,
        level = home.selectedLevelID,
    )
    return home.copy(
        dimensionLines = home.dimensionLines + dim,
        topologyVersion = home.topologyVersion + 1,
    )
}

/**
 * Typed dimension length: keep start fixed, place end at [lengthCM]
 * along the existing chord direction.
 */
fun applyDimensionLength(home: Home, dimID: String, lengthCM: Double): Home {
    if (lengthCM < minDrawnWallCM) return home
    val dim = home.dimensionLines.find { it.id == dimID } ?: return home
    val start = vec(dim.xStart, dim.yStart)
    val end = vec(dim.xEnd, dim.yEnd)
    val current = dist(start, end)
    if (current < 1e-12) return home
    if (abs(current - lengthCM) < 1e-9) return home
    val dir = normalize(sub(end, start))
    val newEnd = add(start, scale(dir, lengthCM))
    return home.copy(
        dimensionLines = home.dimensionLines.map {
            if (it.id != dimID) it
            else it.copy(xEnd = newEnd.x, yEnd = newEnd.y)
        },
        topologyVersion = home.topologyVersion + 1,
    )
}

fun applyDimensionEndpoint(
    home: Home,
    dimID: String,
    atStart: Boolean,
    x: Double,
    y: Double,
): Home {
    if (home.dimensionLines.none { it.id == dimID }) return home
    return home.copy(
        dimensionLines = home.dimensionLines.map { d ->
            if (d.id != dimID) d
            else if (atStart) d.copy(xStart = x, yStart = y)
            else d.copy(xEnd = x, yEnd = y)
        },
        topologyVersion = home.topologyVersion + 1,
    )
}

fun applyDimensionOffset(home: Home, dimID: String, offset: Double): Home {
    if (home.dimensionLines.none { it.id == dimID }) return home
    return home.copy(
        dimensionLines = home.dimensionLines.map { d ->
            if (d.id == dimID) d.copy(offset = offset) else d
        },
        topologyVersion = home.topologyVersion + 1,
    )
}

/** Signed perpendicular distance of [point] from start→end; left is positive. */
fun signedDimensionOffset(from: Vec2, to: Vec2, point: Vec2): Double {
    val n = wallLeftNormal(to.x - from.x, to.y - from.y) ?: return 0.0
    return (point.x - from.x) * n.x + (point.y - from.y) * n.y
}

/**
 * Typed Width / Depth from the room sheet. Resize through RoomSizeMutation,
 * then reconcile rooms against moved walls (scoped to the resized room's level).
 */
fun applyRoomSize(
    home: Home,
    roomID: String,
    width: Double?,
    depth: Double?,
): Home {
    val room = home.rooms.find { it.id == roomID } ?: return home
    val next = RoomSizeMutation.resize(
        roomID,
        width,
        depth,
        home.rooms,
        home.walls,
        home.doorsAndWindows,
    )
    val rooms = RoomDetection.reconcileRooms(next.walls, next.rooms, level = room.level)
    return home.copy(
        rooms = rooms,
        walls = next.walls,
        doorsAndWindows = next.openings,
        topologyVersion = home.topologyVersion + 1,
    )
}

const val AUTO_EXT_DIM_PREFIX = "auto-ext-"

fun applyExteriorDimensionChain(
    home: Home,
    faceMode: com.homedesign.android.domain.geom.DimensionFaceMode =
        com.homedesign.android.domain.geom.DimensionFaceMode.Outer,
): Home {
    val level = home.selectedLevelID
    val levelWalls = wallsOnLevel(home)
    val fresh = com.homedesign.android.domain.geom.DimensionMutation.exteriorChain(
        walls = levelWalls,
        level = level,
        openings = home.doorsAndWindows,
        spacingCM = com.homedesign.android.domain.geom.dimensionFaceGapCM,
        idPrefix = AUTO_EXT_DIM_PREFIX,
        faceMode = faceMode,
    )
    if (fresh.isEmpty()) return home
    val kept = home.dimensionLines.filter { !it.id.startsWith(AUTO_EXT_DIM_PREFIX) }
    return home.copy(
        dimensionLines = kept + fresh,
        topologyVersion = home.topologyVersion + 1,
    )
}

fun previewWallBow(
    home: Home,
    wallID: String,
    extent: Double,
): Pair<List<com.homedesign.android.domain.model.Wall>, List<com.homedesign.android.domain.model.Room>> {
    val walls = home.walls.map { w ->
        if (w.id == wallID) w.copy(arcExtent = extent, curveProfile = null) else w
    }
    val rooms = RoomDetection.reconcileRooms(walls, home.rooms, level = home.selectedLevelID)
    return walls to rooms
}

fun applyWallBow(home: Home, wallID: String, extent: Double): Home {
    if (home.walls.none { it.id == wallID }) return home
    val nextExtent =
        if (kotlin.math.abs(extent) < com.homedesign.android.domain.geom.ArcWallGeometry.straightEpsilon) {
            null
        } else {
            extent
        }
    val walls = home.walls.map { w ->
        if (w.id == wallID) w.copy(arcExtent = nextExtent, curveProfile = null) else w
    }
    return inferAndReconcile(home, walls, home.rooms)
}

fun previewSpanBow(
    home: Home,
    wallID: String,
    spanIndex: Int,
    bow: Double,
): Pair<List<com.homedesign.android.domain.model.Wall>, List<com.homedesign.android.domain.model.Room>> {
    val walls = com.homedesign.android.domain.geom.WallCurveMutation.setSpanBow(
        home.walls,
        wallID,
        spanIndex,
        bow,
    )
    val rooms = RoomDetection.reconcileRooms(walls, home.rooms, level = home.selectedLevelID)
    return walls to rooms
}

fun applySpanBow(home: Home, wallID: String, spanIndex: Int, bow: Double): Home {
    val walls = com.homedesign.android.domain.geom.WallCurveMutation.setSpanBow(
        home.walls,
        wallID,
        spanIndex,
        bow,
    )
    if (walls === home.walls) return home
    return inferAndReconcile(home, walls, home.rooms)
}

fun applyAddCurvePoint(home: Home, wallID: String, t: Double): Home {
    val walls = com.homedesign.android.domain.geom.WallCurveMutation.insertBreakpoint(
        home.walls,
        wallID,
        t,
    )
    if (walls === home.walls) return home
    return inferAndReconcile(home, walls, home.rooms)
}

fun applyMoveCurveBreakpoint(home: Home, wallID: String, breakIndex: Int, t: Double): Home {
    val wall = home.walls.find { it.id == wallID } ?: return home
    if (com.homedesign.android.domain.geom.WallCurveMutation.shouldMergeBreakpoint(wall, breakIndex, t)) {
        val walls = com.homedesign.android.domain.geom.WallCurveMutation.deleteBreakpoint(
            home.walls,
            wallID,
            breakIndex,
        )
        if (walls === home.walls) return home
        return inferAndReconcile(home, walls, home.rooms)
    }
    val walls = com.homedesign.android.domain.geom.WallCurveMutation.moveBreakpoint(
        home.walls,
        wallID,
        breakIndex,
        t,
    )
    if (walls === home.walls) return home
    return inferAndReconcile(home, walls, home.rooms)
}

fun previewMoveCurveBreakpoint(
    home: Home,
    wallID: String,
    breakIndex: Int,
    t: Double,
): Pair<List<com.homedesign.android.domain.model.Wall>, List<com.homedesign.android.domain.model.Room>> {
    val walls = com.homedesign.android.domain.geom.WallCurveMutation.moveBreakpoint(
        home.walls,
        wallID,
        breakIndex,
        t,
    )
    val rooms = RoomDetection.reconcileRooms(walls, home.rooms, level = home.selectedLevelID)
    return walls to rooms
}

fun applyRemoveCurvePoint(home: Home, wallID: String, breakIndex: Int): Home {
    val walls = com.homedesign.android.domain.geom.WallCurveMutation.deleteBreakpoint(
        home.walls,
        wallID,
        breakIndex,
    )
    if (walls === home.walls) return home
    return inferAndReconcile(home, walls, home.rooms)
}
