package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.add
import com.homedesign.android.domain.geom.dist
import com.homedesign.android.domain.geom.dot
import com.homedesign.android.domain.geom.length
import com.homedesign.android.domain.geom.normalize
import com.homedesign.android.domain.geom.pasteOffsetCM
import com.homedesign.android.domain.geom.scale
import com.homedesign.android.domain.geom.sub
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.Wall
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Plan clipboard (iOS `PlanClipboard`): furniture + openings.
 * Paste openings re-bind onto a wall; without a tap target they offset
 * by [pasteOffsetCM] then seat on the closest active-level wall that fits.
 */
data class PlanClipboardPayload(
    val pieces: List<HomePieceOfFurniture> = emptyList(),
    val openings: List<HomeDoorOrWindow> = emptyList(),
) {
    val isEmpty: Boolean get() = pieces.isEmpty() && openings.isEmpty()
}

data class PlanClipboardPasteResult(
    val home: Home,
    val pieceIds: List<String>,
    val openingIds: List<String>,
) {
    val isEmpty: Boolean get() = pieceIds.isEmpty() && openingIds.isEmpty()
}

object PlanClipboard {
    fun encodeSelection(home: Home, selection: Selection): PlanClipboardPayload? {
        return when (selection) {
            is Selection.Furniture -> {
                val pieces = fanInPieces(home, listOf(selection.id))
                if (pieces.isEmpty()) null
                else PlanClipboardPayload(pieces = pieces.map { deepCopyPiece(it) })
            }
            is Selection.MultiFurniture -> {
                val pieces = fanInPieces(home, selection.ids)
                if (pieces.isEmpty()) null
                else PlanClipboardPayload(pieces = pieces.map { deepCopyPiece(it) })
            }
            is Selection.Opening -> payloadCopyingOpening(selection.id, home.doorsAndWindows)
            is Selection.OpeningHandle -> payloadCopyingOpening(selection.id, home.doorsAndWindows)
            else -> null
        }
    }

    fun payloadCopyingOpening(
        openingID: String,
        openings: List<HomeDoorOrWindow>,
    ): PlanClipboardPayload? {
        val opening = openings.find { it.piece.id == openingID } ?: return null
        return PlanClipboardPayload(openings = listOf(deepCopyOpening(opening)))
    }

    fun centroid(payload: PlanClipboardPayload): Vec2? {
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (p in payload.pieces) {
            sx += p.x
            sy += p.y
            n++
        }
        for (o in payload.openings) {
            sx += o.piece.x
            sy += o.piece.y
            n++
        }
        if (n == 0) return null
        return vec(sx / n, sy / n)
    }

    fun materialize(
        payload: PlanClipboardPayload,
        atX: Double?,
        atY: Double?,
        level: String?,
        fallbackOffsetCM: Double = pasteOffsetCM,
    ): PlanClipboardPayload {
        val anchor = centroid(payload)
        val dx: Double
        val dy: Double
        if (atX != null && atY != null && anchor != null) {
            dx = atX - anchor.x
            dy = atY - anchor.y
        } else {
            dx = fallbackOffsetCM
            dy = fallbackOffsetCM
        }
        val groupMap = mutableMapOf<String, String>()
        fun remapGroup(gid: String?): String? {
            if (gid == null) return null
            return groupMap.getOrPut(gid) { UUID.randomUUID().toString() }
        }
        val pieces = payload.pieces.map { source ->
            source.copy(
                id = UUID.randomUUID().toString(),
                groupID = remapGroup(source.groupID),
                x = source.x + dx,
                y = source.y + dy,
                level = level,
                modelRotation = source.modelRotation?.toList(),
                materialOverrides = source.materialOverrides?.toMap(),
                lightSources = source.lightSources?.toList(),
            )
        }
        val openings = payload.openings.map { source ->
            source.copy(
                piece = source.piece.copy(
                    id = UUID.randomUUID().toString(),
                    x = source.piece.x + dx,
                    y = source.piece.y + dy,
                    level = level,
                    modelRotation = source.piece.modelRotation?.toList(),
                    materialOverrides = source.piece.materialOverrides?.toMap(),
                    lightSources = source.piece.lightSources?.toList(),
                ),
                sashes = source.sashes.map { it.copy() },
            )
        }
        return PlanClipboardPayload(pieces = pieces, openings = openings)
    }

    /**
     * Seat [opening] on [wall]: centre projected onto the wall axis
     * (clamped so the cutout fits), angle aligned to the wall, level from wall.
     */
    fun rebind(
        opening: HomeDoorOrWindow,
        to: Wall,
        near: Vec2,
    ): HomeDoorOrWindow? {
        val start = vec(to.startX, to.startY)
        val end = vec(to.endX, to.endY)
        val span = sub(end, start)
        val len = length(span)
        if (len <= 1e-9) return null
        val rawT = dot(sub(near, start), span) / (len * len)
        val halfWidthT = (opening.piece.width / 2.0) / len
        if (halfWidthT >= 0.5) return null
        val t = max(halfWidthT, min(1.0 - halfWidthT, rawT))
        val centre = add(start, scale(span, t))
        return opening.copy(
            piece = opening.piece.copy(
                x = centre.x,
                y = centre.y,
                angle = atan2(span.y, span.x),
                level = to.level,
            ),
        )
    }

    fun applyPaste(
        home: Home,
        payload: PlanClipboardPayload,
        atX: Double? = null,
        atY: Double? = null,
        targetWallID: String? = null,
        maxWallDistanceCM: Double = Double.POSITIVE_INFINITY,
    ): PlanClipboardPasteResult {
        if (payload.isEmpty) {
            return PlanClipboardPasteResult(home, emptyList(), emptyList())
        }
        val level = home.selectedLevelID
        val materialized = materialize(payload, atX, atY, level)
        val pieces = materialized.pieces
        val seatedOpenings = seatOpenings(
            home = home,
            openings = materialized.openings,
            atX = atX,
            atY = atY,
            targetWallID = targetWallID,
            maxWallDistanceCM = maxWallDistanceCM,
            centroidFallback = centroid(materialized),
        )
        if (pieces.isEmpty() && seatedOpenings.isEmpty()) {
            return PlanClipboardPasteResult(home, emptyList(), emptyList())
        }
        var next = home
        if (pieces.isNotEmpty()) {
            next = next.copy(
                furniture = next.furniture + pieces,
                furnitureRevision = next.furnitureRevision + 1,
            )
        }
        if (seatedOpenings.isNotEmpty()) {
            next = next.copy(
                doorsAndWindows = next.doorsAndWindows + seatedOpenings,
                topologyVersion = next.topologyVersion + 1,
            )
        }
        return PlanClipboardPasteResult(
            home = next,
            pieceIds = pieces.map { it.id },
            openingIds = seatedOpenings.map { it.piece.id },
        )
    }

    fun applyDuplicate(home: Home, selection: Selection): PlanClipboardPasteResult {
        val payload = encodeSelection(home, selection)
            ?: return PlanClipboardPasteResult(home, emptyList(), emptyList())
        return applyPaste(home, payload, atX = null, atY = null)
    }

    private fun seatOpenings(
        home: Home,
        openings: List<HomeDoorOrWindow>,
        atX: Double?,
        atY: Double?,
        targetWallID: String?,
        maxWallDistanceCM: Double,
        centroidFallback: Vec2?,
    ): List<HomeDoorOrWindow> {
        if (openings.isEmpty()) return emptyList()
        val level = home.selectedLevelID
        val walls = home.walls.filter { level == null || it.level == level }
        if (walls.isEmpty()) return emptyList()

        val anchor = when {
            atX != null && atY != null -> vec(atX, atY)
            else -> centroidFallback ?: return emptyList()
        }

        val wall = when {
            targetWallID != null -> walls.find { it.id == targetWallID }
            else -> closestWall(anchor, walls, maxWallDistanceCM)
        } ?: return emptyList()

        val axisRaw = vec(wall.endX - wall.startX, wall.endY - wall.startY)
        if (length(axisRaw) <= 1e-9) return emptyList()
        val axis = normalize(axisRaw)

        val seated = mutableListOf<HomeDoorOrWindow>()
        var seatPoint = anchor
        for (opening in openings) {
            val bound = rebind(opening, wall, seatPoint) ?: continue
            seated.add(bound)
            seatPoint = add(
                vec(bound.piece.x, bound.piece.y),
                scale(axis, bound.piece.width),
            )
        }
        return seated
    }

    private fun closestWall(
        point: Vec2,
        walls: List<Wall>,
        maxDistanceCM: Double,
    ): Wall? {
        var best: Wall? = null
        var bestDist = Double.POSITIVE_INFINITY
        for (wall in walls) {
            val d = pointToWallCentrelineDistance(point, wall)
            if (d >= maxDistanceCM || d >= bestDist) continue
            bestDist = d
            best = wall
        }
        return best
    }

    private fun pointToWallCentrelineDistance(point: Vec2, wall: Wall): Double {
        val a = vec(wall.startX, wall.startY)
        val b = vec(wall.endX, wall.endY)
        val ab = sub(b, a)
        val lenSq = ab.x * ab.x + ab.y * ab.y
        if (lenSq < 1e-12) return dist(point, a)
        val t = (dot(sub(point, a), ab) / lenSq).coerceIn(0.0, 1.0)
        val proj = add(a, scale(ab, t))
        return dist(point, proj)
    }

    private fun fanInPieces(home: Home, ids: List<String>): List<HomePieceOfFurniture> {
        val seen = mutableSetOf<String>()
        val pieces = mutableListOf<HomePieceOfFurniture>()
        for (id in ids) {
            val seed = home.furniture.find { it.id == id } ?: continue
            val members = if (seed.groupID != null) {
                home.furniture.filter { it.groupID == seed.groupID }
            } else {
                listOf(seed)
            }
            for (p in members) {
                if (seen.add(p.id)) pieces.add(p)
            }
        }
        return pieces
    }

    private fun deepCopyPiece(p: HomePieceOfFurniture): HomePieceOfFurniture = p.copy(
        modelRotation = p.modelRotation?.toList(),
        materialOverrides = p.materialOverrides?.toMap(),
        lightSources = p.lightSources?.toList(),
    )

    private fun deepCopyOpening(o: HomeDoorOrWindow): HomeDoorOrWindow = o.copy(
        piece = deepCopyPiece(o.piece),
        sashes = o.sashes.map { it.copy() },
    )
}
