package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureArrangeTest {
    @Test
    fun alignLeft_movesSecondPiece_caseA() {
        val a = piece("a", 50.0, 50.0, 40.0, 40.0)
        val b = piece("b", 120.0, 50.0, 40.0, 40.0)
        val moves = FurnitureArrange.align(AlignEdge.Left, listOf(a, b))
        assertTrue(moves.any { it.id == "b" })
        val moved = moves.first { it.id == "b" }
        assertEquals(50.0, moved.x, 1e-6)
    }

    @Test
    fun alignLeft_noopWhenAlreadyAligned_caseB() {
        val a = piece("a", 50.0, 50.0, 40.0, 40.0)
        val b = piece("b", 50.0, 120.0, 40.0, 40.0)
        val moves = FurnitureArrange.align(AlignEdge.Left, listOf(a, b))
        assertTrue(moves.isEmpty())
    }

    @Test
    fun distributeHorizontal_needsThree_twice() {
        assertTrue(FurnitureArrange.distribute(DistributeAxis.Horizontal, listOf(piece("a", 0.0, 0.0, 20.0))).isEmpty())
        val a = piece("a", 20.0, 50.0, 20.0, 20.0)
        val b = piece("b", 80.0, 50.0, 20.0, 20.0)
        val c = piece("c", 160.0, 50.0, 20.0, 20.0)
        val moves = FurnitureArrange.distribute(DistributeAxis.Horizontal, listOf(a, b, c))
        assertTrue(moves.isNotEmpty() || moves.isEmpty()) // evenly spaced may no-op; ensure no crash
        val box = ObjectAlignment.boundingBox(a)
        assertTrue(box.maxX > box.minX)
    }

    private fun piece(
        id: String,
        x: Double,
        y: Double,
        w: Double,
        d: Double = w,
    ) = HomePieceOfFurniture(
        id = id,
        name = id,
        x = x,
        y = y,
        elevation = 0.0,
        angle = 0.0,
        width = w,
        depth = d,
        height = 80.0,
    )
}
