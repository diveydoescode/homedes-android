package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.pasteOffsetCM
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection
import java.util.UUID

/**
 * Furniture clipboard (web `state/clipboard.ts` furniture path).
 * Walls are not copied; openings deferred to a later pass.
 */
data class FurnitureClipboardPayload(
    val pieces: List<HomePieceOfFurniture>,
)

object FurnitureClipboard {
    fun encodeSelection(home: Home, selection: Selection): FurnitureClipboardPayload? {
        val ids = when (selection) {
            is Selection.Furniture -> listOf(selection.id)
            is Selection.MultiFurniture -> selection.ids
            else -> return null
        }
        val pieces = fanInPieces(home, ids)
        if (pieces.isEmpty()) return null
        return FurnitureClipboardPayload(pieces = pieces.map { deepCopyPiece(it) })
    }

    fun applyPaste(
        home: Home,
        payload: FurnitureClipboardPayload,
        atX: Double? = null,
        atY: Double? = null,
    ): Pair<Home, List<String>> {
        val fresh = materializePieces(payload, atX, atY, home.selectedLevelID, pasteOffsetCM)
        if (fresh.isEmpty()) return home to emptyList()
        return home.copy(
            furniture = home.furniture + fresh,
            furnitureRevision = home.furnitureRevision + 1,
        ) to fresh.map { it.id }
    }

    fun applyDuplicate(home: Home, selection: Selection): Pair<Home, List<String>> {
        val payload = encodeSelection(home, selection) ?: return home to emptyList()
        return applyPaste(home, payload, atX = null, atY = null)
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

    private fun pieceCentroid(pieces: List<HomePieceOfFurniture>): Pair<Double, Double>? {
        if (pieces.isEmpty()) return null
        var sx = 0.0
        var sy = 0.0
        for (p in pieces) {
            sx += p.x
            sy += p.y
        }
        return (sx / pieces.size) to (sy / pieces.size)
    }

    private fun materializePieces(
        payload: FurnitureClipboardPayload,
        atX: Double?,
        atY: Double?,
        level: String?,
        fallbackOffsetCM: Double,
    ): List<HomePieceOfFurniture> {
        val pieces = payload.pieces
        if (pieces.isEmpty()) return emptyList()
        val anchor = pieceCentroid(pieces)
        val dx: Double
        val dy: Double
        if (atX != null && atY != null && anchor != null) {
            dx = atX - anchor.first
            dy = atY - anchor.second
        } else {
            dx = fallbackOffsetCM
            dy = fallbackOffsetCM
        }
        val groupMap = mutableMapOf<String, String>()
        fun remapGroup(gid: String?): String? {
            if (gid == null) return null
            return groupMap.getOrPut(gid) { UUID.randomUUID().toString() }
        }
        return pieces.map { source ->
            source.copy(
                id = UUID.randomUUID().toString(),
                groupID = remapGroup(source.groupID),
                x = source.x + dx,
                y = source.y + dy,
                level = level,
            )
        }
    }

    private fun deepCopyPiece(p: HomePieceOfFurniture): HomePieceOfFurniture = p.copy(
        modelRotation = p.modelRotation?.toList(),
        materialOverrides = p.materialOverrides?.toMap(),
        lightSources = p.lightSources?.toList(),
    )
}
