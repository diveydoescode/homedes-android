package com.homedesign.android.domain.editor

import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection

/**
 * Furniture-only clipboard façade over [PlanClipboard] (kept for call-site compat).
 */
data class FurnitureClipboardPayload(
    val pieces: List<HomePieceOfFurniture>,
)

object FurnitureClipboard {
    fun encodeSelection(home: Home, selection: Selection): FurnitureClipboardPayload? {
        val payload = PlanClipboard.encodeSelection(home, selection) ?: return null
        if (payload.pieces.isEmpty()) return null
        return FurnitureClipboardPayload(pieces = payload.pieces)
    }

    fun applyPaste(
        home: Home,
        payload: FurnitureClipboardPayload,
        atX: Double? = null,
        atY: Double? = null,
    ): Pair<Home, List<String>> {
        val result = PlanClipboard.applyPaste(
            home,
            PlanClipboardPayload(pieces = payload.pieces),
            atX = atX,
            atY = atY,
        )
        return result.home to result.pieceIds
    }

    fun applyDuplicate(home: Home, selection: Selection): Pair<Home, List<String>> {
        val result = PlanClipboard.applyDuplicate(home, selection)
        return result.home to result.pieceIds
    }
}
