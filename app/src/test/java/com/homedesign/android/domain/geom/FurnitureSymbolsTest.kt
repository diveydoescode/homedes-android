package com.homedesign.android.domain.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureSymbolsTest {
    @Test
    fun proceduralKinds_emitPaths() {
        val kinds = listOf(
            FurnitureSymbolKind.Nightstand,
            FurnitureSymbolKind.Dresser,
            FurnitureSymbolKind.Fridge,
            FurnitureSymbolKind.Tv,
            FurnitureSymbolKind.Rug,
            FurnitureSymbolKind.Lamp,
            FurnitureSymbolKind.Stairs,
            FurnitureSymbolKind.Chandelier,
            FurnitureSymbolKind.Mirror,
        )
        for (kind in kinds) {
            val art = FurnitureSymbols.paths(kind, 80.0, 40.0)
            assertTrue("$kind should have strokes", art.paths.isNotEmpty())
            assertTrue(art.paths.all { it.d.isNotBlank() })
        }
    }

    @Test
    fun svgBackedKinds_withoutProcedural_areEmpty() {
        // Bed/sofa/etc. rely on SVG assets in the presentation layer.
        val art = FurnitureSymbols.paths(FurnitureSymbolKind.Bed, 200.0, 160.0)
        assertEquals(0, art.paths.size)
    }

    @Test
    fun generic_isEmpty() {
        val art = FurnitureSymbols.paths(FurnitureSymbolKind.Generic, 100.0, 100.0)
        assertTrue(art.paths.isEmpty())
    }

    @Test
    fun chandelier_isOverhead() {
        assertTrue(FurnitureSymbols.isOverhead(FurnitureSymbolKind.Chandelier))
        val art = FurnitureSymbols.paths(FurnitureSymbolKind.Chandelier, 60.0, 60.0)
        assertTrue(art.overhead)
    }
}
