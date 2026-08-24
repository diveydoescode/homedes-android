package com.homedesign.android.domain.geom

import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.model.HomePieceOfFurniture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureSymbolClassifierTest {

    private fun piece(name: String, width: Double = 100.0, depth: Double = 80.0) =
        HomePieceOfFurniture(
            id = "p",
            name = name,
            x = 0.0,
            y = 0.0,
            elevation = 0.0,
            angle = 0.0,
            width = width,
            depth = depth,
            height = 80.0,
            movable = true,
            visible = true,
        )

    private fun entry(name: String, category: String) =
        CatalogEntry(
            id = "e",
            catalog = "generic",
            name = name,
            category = category,
            width = 100.0,
            depth = 80.0,
            height = 80.0,
        )

    @Test
    fun nameKeywordBeatsCategory() {
        assertEquals(
            FurnitureSymbolKind.Sofa,
            FurnitureSymbolClassifier.classify(piece("Sofa"), entry("Unused", "Staircases")),
        )
        assertEquals(
            FurnitureSymbolKind.Bed,
            FurnitureSymbolClassifier.classify(piece("Bed"), entry("Bed", "Lighting")),
        )
    }

    @Test
    fun lSofaBeatsSofa() {
        assertEquals(FurnitureSymbolKind.SofaL, FurnitureSymbolClassifier.classify(piece("Corner sofa")))
        assertEquals(FurnitureSymbolKind.SofaL, FurnitureSymbolClassifier.classify(piece("Sectional")))
        assertEquals(FurnitureSymbolKind.SofaL, FurnitureSymbolClassifier.classify(piece("L-shaped sofa")))
        assertEquals(FurnitureSymbolKind.Sofa, FurnitureSymbolClassifier.classify(piece("Sofa")))
    }

    @Test
    fun tableLampIsLampNotTable() {
        assertEquals(FurnitureSymbolKind.Lamp, FurnitureSymbolClassifier.classify(piece("Table lamp")))
    }

    @Test
    fun bedsideIsNightstand() {
        assertEquals(FurnitureSymbolKind.Nightstand, FurnitureSymbolClassifier.classify(piece("Bedside table")))
        assertEquals(FurnitureSymbolKind.Nightstand, FurnitureSymbolClassifier.classify(piece("Night stand")))
    }

    @Test
    fun fallsBackToCategory() {
        assertEquals(
            FurnitureSymbolKind.Stairs,
            FurnitureSymbolClassifier.classify(piece("Widget"), entry("Widget", "Staircases")),
        )
        assertEquals(
            FurnitureSymbolKind.Lamp,
            FurnitureSymbolClassifier.classify(piece("Gadget"), entry("Gadget", "Lighting")),
        )
    }

    @Test
    fun genericForUnknown() {
        assertEquals(
            FurnitureSymbolKind.Generic,
            FurnitureSymbolClassifier.classify(piece("Mystery crate"), entry("Mystery crate", "Decor")),
        )
        assertEquals(FurnitureSymbolKind.Generic, FurnitureSymbolClassifier.classify(piece("")))
    }

    @Test
    fun canonicalLongAxis() {
        val widthKinds = listOf(
            FurnitureSymbolKind.Sofa,
            FurnitureSymbolKind.Wardrobe,
            FurnitureSymbolKind.Dresser,
            FurnitureSymbolKind.Bookshelf,
            FurnitureSymbolKind.Desk,
            FurnitureSymbolKind.Bathtub,
            FurnitureSymbolKind.Sink,
            FurnitureSymbolKind.Tv,
            FurnitureSymbolKind.Mirror,
        )
        for (kind in widthKinds) {
            assertEquals(kind.name, SymbolAxis.Width, FurnitureSymbolClassifier.canonicalLongAxis(kind))
        }
        assertEquals(SymbolAxis.Depth, FurnitureSymbolClassifier.canonicalLongAxis(FurnitureSymbolKind.Bed))
        assertEquals(SymbolAxis.Depth, FurnitureSymbolClassifier.canonicalLongAxis(FurnitureSymbolKind.Toilet))
        assertNull(FurnitureSymbolClassifier.canonicalLongAxis(FurnitureSymbolKind.Fridge))
        assertNull(FurnitureSymbolClassifier.canonicalLongAxis(FurnitureSymbolKind.Generic))
    }

    @Test
    fun needsQuarterTurn() {
        assertTrue(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Bed, 200.0, 80.0))
        assertFalse(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Bed, 80.0, 200.0))
        assertFalse(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Sofa, 200.0, 80.0))
        assertTrue(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Sofa, 80.0, 200.0))
        assertFalse(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Bed, 100.0, 100.0))
        assertFalse(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Bed, 100.4, 100.0))
        assertTrue(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Bed, 100.6, 100.0))
        assertFalse(FurnitureSymbolClassifier.needsQuarterTurn(FurnitureSymbolKind.Lamp, 200.0, 40.0))
    }
}
