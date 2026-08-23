package com.homedesign.android.domain.editor

import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.geom.FurnitureSnap
import com.homedesign.android.domain.geom.OpeningKind
import com.homedesign.android.domain.geom.OpeningMutation
import com.homedesign.android.domain.geom.RectangleRoom
import com.homedesign.android.domain.geom.RoomDetection
import com.homedesign.android.domain.geom.RoomMutation
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.WallMutation
import com.homedesign.android.domain.geom.WallTJunction
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.geom.dist
import com.homedesign.android.domain.geom.furnitureSnapToWallCM
import com.homedesign.android.domain.geom.minDrawnWallCM
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection
import java.util.UUID

private fun bumpTopology(home: Home, walls: List<com.homedesign.android.domain.model.Wall>, rooms: List<com.homedesign.android.domain.model.Room>): Home =
    home.copy(walls = walls, rooms = rooms, topologyVersion = home.topologyVersion + 1)

private fun splitAndHealTees(walls: List<com.homedesign.android.domain.model.Wall>): List<com.homedesign.android.domain.model.Wall> {
    val split = WallTJunction.splitHostsAtTees(walls).walls
    return WallTJunction.heal(split).walls
}

/**
 * Minimal join inference: wire atStart/atEnd when endpoints coincide within 1 cm
 * and the slot is empty. Full WallJoinInference deferred.
 */
private fun inferJoins(walls: List<com.homedesign.android.domain.model.Wall>): List<com.homedesign.android.domain.model.Wall> {
    val result = walls.toMutableList()
    for (i in result.indices) {
        val w = result[i]
        var atStart = w.atStart
        var atEnd = w.atEnd
        if (atStart == null) {
            for (j in result.indices) {
                if (i == j) continue
                val o = result[j]
                if (dist(vec(w.startX, w.startY), vec(o.startX, o.startY)) < 1.0 ||
                    dist(vec(w.startX, w.startY), vec(o.endX, o.endY)) < 1.0
                ) {
                    atStart = o.id
                    break
                }
            }
        }
        if (atEnd == null) {
            for (j in result.indices) {
                if (i == j) continue
                val o = result[j]
                if (dist(vec(w.endX, w.endY), vec(o.startX, o.startY)) < 1.0 ||
                    dist(vec(w.endX, w.endY), vec(o.endX, o.endY)) < 1.0
                ) {
                    atEnd = o.id
                    break
                }
            }
        }
        if (atStart != w.atStart || atEnd != w.atEnd) {
            result[i] = w.copy(atStart = atStart, atEnd = atEnd)
        }
    }
    return result
}

private fun inferAndReconcile(
    home: Home,
    walls: List<com.homedesign.android.domain.model.Wall>,
    rooms: List<com.homedesign.android.domain.model.Room>,
): Home {
    val inferred = inferJoins(walls)
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

fun applyPlaceFurniture(home: Home, entry: CatalogEntry, x: Double, y: Double): Home {
    var piece = HomePieceOfFurniture(
        id = UUID.randomUUID().toString(),
        catalogID = entry.id,
        name = entry.name,
        x = x,
        y = y,
        elevation = 0.0,
        angle = 0.0,
        width = entry.width,
        depth = entry.depth,
        height = entry.height,
        movable = entry.movable,
        visible = true,
        modelMirrored = false,
        level = home.selectedLevelID,
        lightPower = if (entry.category == "Lighting" || entry.category == "Lights") 0.5 else null,
    )
    FurnitureSnap.snapToWall(piece, wallsOnLevel(home))?.let { snapped ->
        piece = piece.copy(x = snapped.x, y = snapped.y, angle = snapped.angle)
    }
    return home.copy(
        furniture = home.furniture + piece,
        furnitureRevision = home.furnitureRevision + 1,
    )
}

fun commitFurnitureMove(home: Home, pieceID: String, x: Double, y: Double): Home {
    val piece = home.furniture.find { it.id == pieceID } ?: return home
    var next = piece.copy(x = x, y = y)
    FurnitureSnap.snapToWallKeepAngle(next, wallsOnLevel(home), furnitureSnapToWallCM)?.let { snapped ->
        next = next.copy(x = snapped.x, y = snapped.y)
    }
    return home.copy(
        furniture = home.furniture.map { if (it.id == pieceID) next else it },
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
    if (home.walls.none { it.id == wallID }) return home
    val walls = home.walls.map {
        if (it.id == wallID) it.copy(thickness = thicknessCM) else it
    }
    return bumpTopology(home, walls, home.rooms)
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
