package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture

/** Ported from web `FurnitureGroupMove.ts` / iOS FurnitureGroupMove. */
data class GroupMove(val id: String, val x: Double, val y: Double)

object FurnitureGroupMove {
    /** All ids in [ids] plus every piece sharing a groupID with any of them. */
    fun expandIDs(pieces: List<HomePieceOfFurniture>, ids: List<String>): List<String> {
        val seed = ids.toSet()
        val groups = mutableSetOf<String>()
        for (p in pieces) {
            val gid = p.groupID
            if (p.id in seed && gid != null) groups.add(gid)
        }
        if (groups.isEmpty()) return ids.toList()
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (p in pieces) {
            if (p.id in seen) continue
            val gid = p.groupID
            if (p.id in seed || (gid != null && gid in groups)) {
                seen.add(p.id)
                out.add(p.id)
            }
        }
        return out
    }

    /**
     * New centres for every OTHER piece sharing [draggedID]'s groupID.
     * Empty when ungrouped / unknown id. Dragged piece itself is omitted.
     */
    fun fanDeltaToGroup(
        pieces: List<HomePieceOfFurniture>,
        draggedID: String,
        delta: Vec2,
    ): List<GroupMove> {
        val dragged = pieces.find { it.id == draggedID } ?: return emptyList()
        val gid = dragged.groupID ?: return emptyList()
        val moves = mutableListOf<GroupMove>()
        for (piece in pieces) {
            if (piece.id == draggedID) continue
            if (piece.groupID != gid) continue
            moves.add(GroupMove(piece.id, piece.x + delta.x, piece.y + delta.y))
        }
        return moves
    }
}
