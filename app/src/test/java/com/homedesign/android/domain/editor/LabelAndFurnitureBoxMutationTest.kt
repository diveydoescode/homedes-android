package com.homedesign.android.domain.editor

import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.PlanLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelAndFurnitureBoxMutationTest {
    @Test
    fun labelMoveAndRename() {
        var home = HomeFactory.emptyHome("T")
        home = applyPlaceLabel(home, 50.0, 60.0, "Hello")
        val id = home.labels.single().id

        home = applyLabelMove(home, id, 80.0, 90.0)
        assertEquals(80.0, home.labels.single().x, 1e-9)
        assertEquals(90.0, home.labels.single().y, 1e-9)

        home = applyRenameLabel(home, id, "World")
        assertEquals("World", home.labels.single().text)

        // Empty rename is a no-op.
        val before = home
        home = applyRenameLabel(home, id, "   ")
        assertTrue(home === before)
    }

    @Test
    fun furnitureBoxCreatesCustomPiece() {
        val home = HomeFactory.emptyHome("T")
        val next = applyCreateFurnitureBox(
            home,
            centerX = 200.0,
            centerY = 150.0,
            width = 120.0,
            depth = 80.0,
            name = "",
        )
        val piece = next.furniture.single()
        assertEquals("Custom", piece.name)
        assertEquals(null, piece.catalogID)
        assertEquals(200.0, piece.x, 1e-9)
        assertEquals(150.0, piece.y, 1e-9)
        assertEquals(120.0, piece.width, 1e-9)
        assertEquals(80.0, piece.depth, 1e-9)
        assertEquals(75.0, piece.height, 1e-9)
    }

    @Test
    fun furnitureBoxRejectsTinySides() {
        val home = HomeFactory.emptyHome("T")
        val next = applyCreateFurnitureBox(home, 0.0, 0.0, 9.0, 50.0, "Desk")
        assertTrue(next === home)
    }

    @Test
    fun furnitureBoxUsesTypedName() {
        val home = HomeFactory.emptyHome("T")
        val next = applyCreateFurnitureBox(home, 0.0, 0.0, 40.0, 40.0, " wardrobe")
        assertEquals("wardrobe", next.furniture.single().name)
    }

    @Test
    fun placeLabelThenMovePreservesText() {
        var home = HomeFactory.emptyHome("T")
        home = home.copy(
            labels = listOf(
                PlanLabel(id = "L1", x = 1.0, y = 2.0, text = "Note", angle = 0.0),
            ),
        )
        home = applyLabelMove(home, "L1", 3.0, 4.0)
        assertEquals("Note", home.labels.single().text)
        assertEquals(3.0, home.labels.single().x, 1e-9)
    }
}
