package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.AlignEdge
import com.homedesign.android.domain.geom.ArrangeMove
import com.homedesign.android.domain.geom.DistributeAxis
import com.homedesign.android.domain.geom.FurnitureArrange
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection

/** Port of web `state/arrangement.ts` align / distribute / multi-select toggle. */
fun toggleFurnitureInSelection(selection: Selection, pieceId: String): Selection {
    val ids = when (selection) {
        is Selection.MultiFurniture -> selection.ids.toMutableList()
        is Selection.Furniture -> mutableListOf(selection.id)
        else -> mutableListOf()
    }
    val idx = ids.indexOf(pieceId)
    if (idx >= 0) ids.removeAt(idx) else ids.add(pieceId)
    return when {
        ids.isEmpty() -> Selection.None
        ids.size == 1 -> Selection.Furniture(ids.first())
        else -> Selection.MultiFurniture(ids)
    }
}

fun applyAlign(home: Home, ids: List<String>, edge: AlignEdge): Home {
    val pieces = selectedPieces(home, ids)
    if (pieces.size < 2) return home
    val locked = pieces.filter { it.movable == false }.map { it.id }.toSet()
    val moves = FurnitureArrange.align(edge, pieces).filter { it.id !in locked }
    return applyMoves(home, moves)
}

fun applyDistribute(home: Home, ids: List<String>, axis: DistributeAxis): Home {
    val pieces = selectedPieces(home, ids)
    if (pieces.size < 3) return home
    val locked = pieces.filter { it.movable == false }.map { it.id }.toSet()
    val moves = FurnitureArrange.distribute(axis, pieces).filter { it.id !in locked }
    return applyMoves(home, moves)
}

private fun selectedPieces(home: Home, ids: List<String>): List<HomePieceOfFurniture> {
    val set = ids.toSet()
    return home.furniture.filter { it.id in set }
}

private fun applyMoves(home: Home, moves: List<ArrangeMove>): Home {
    if (moves.isEmpty()) return home
    val byId = moves.associateBy { it.id }
    return home.copy(
        furniture = home.furniture.map { p ->
            val m = byId[p.id] ?: return@map p
            p.copy(x = m.x, y = m.y)
        },
        furnitureRevision = home.furnitureRevision + 1,
    )
}
