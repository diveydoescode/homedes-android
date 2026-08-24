package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.AlignEdge
import com.homedesign.android.domain.geom.DistributeAxis
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureArrangementTest {
    @Test
    fun toggleFurnitureInSelection_buildsMultiThenBack() {
        val one = toggleFurnitureInSelection(Selection.None, "a")
        assertEquals(Selection.Furniture("a"), one)
        val two = toggleFurnitureInSelection(one, "b")
        assertEquals(Selection.MultiFurniture(listOf("a", "b")), two)
        val back = toggleFurnitureInSelection(two, "a")
        assertEquals(Selection.Furniture("b"), back)
    }

    @Test
    fun applyAlign_movesSecondPieceLeft() {
        val a = piece("a", 50.0, 50.0)
        val b = piece("b", 120.0, 50.0)
        val home = HomeFactory.emptyHome("t").copy(furniture = listOf(a, b))
        val next = applyAlign(home, listOf("a", "b"), AlignEdge.Left)
        val moved = next.furniture.first { it.id == "b" }
        assertEquals(50.0, moved.x, 1e-6)
        assertTrue(next.furnitureRevision > home.furnitureRevision)
    }

    @Test
    fun applyDistribute_requiresThree() {
        val a = piece("a", 20.0, 50.0, 20.0)
        val b = piece("b", 80.0, 50.0, 20.0)
        val home = HomeFactory.emptyHome("t").copy(furniture = listOf(a, b))
        assertTrue(applyDistribute(home, listOf("a", "b"), DistributeAxis.Horizontal) === home)
    }

    private fun piece(id: String, x: Double, y: Double, w: Double = 40.0) = HomePieceOfFurniture(
        id = id,
        name = id,
        x = x,
        y = y,
        elevation = 0.0,
        angle = 0.0,
        width = w,
        depth = w,
        height = 80.0,
    )
}
