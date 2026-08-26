package com.homedesign.android.domain.catalog

import com.homedesign.android.domain.editor.applyPlaceFurniture
import com.homedesign.android.domain.editor.applyPlaceLabel
import com.homedesign.android.domain.editor.deleteSelection
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureCatalogTest {

    @Test
    fun idsMatchIos() {
        assertEquals("structure#pillar-round", StructureCatalog.pillarRoundID)
        assertEquals("structure#pillar-square", StructureCatalog.pillarSquareID)
        assertEquals("structure#beam", StructureCatalog.beamID)
        assertEquals("structure#mirror", StructureCatalog.mirrorID)
        assertEquals("structure#garden-path", StructureCatalog.pathID)
        assertEquals("structure#railing", StructureCatalog.railingID)
        assertEquals("structure#rug", StructureCatalog.rugID)
        assertEquals(7, StructureCatalog.entries.size)
        assertTrue(StructureCatalog.entries.all { it.category == "Structure" })
        assertTrue(StructureCatalog.entries.all { it.catalog == "structure" })
    }

    @Test
    fun catalogByIdResolvesStructureBuiltins() {
        for (entry in StructureCatalog.entries) {
            val found = catalogById(entry.id)
            assertNotNull(entry.id, found)
            assertEquals(entry.name, found!!.name)
            assertEquals(entry.width, found.width, 1e-9)
            assertEquals(entry.depth, found.depth, 1e-9)
            assertEquals(entry.height, found.height, 1e-9)
        }
        assertNull(catalogById("structure#does-not-exist"))
    }

    @Test
    fun mergeKeepsStructureWhenReplacingCatalog() {
        val generic = CatalogEntry(
            id = "Scopia#table",
            catalog = "generic",
            name = "Table",
            category = "Tables",
            width = 100.0,
            depth = 80.0,
            height = 75.0,
        )
        val merged = mergeStructureCatalog(listOf(generic))
        assertEquals(1 + StructureCatalog.entries.size, merged.size)
        assertTrue(merged.any { it.id == StructureCatalog.beamID })
        assertTrue(merged.any { it.id == generic.id })
    }

    @Test
    fun structureFlags() {
        assertTrue(StructureCatalog.isStructure(StructureCatalog.beamID))
        assertTrue(StructureCatalog.isStructure(StructureCatalog.pillarRoundID))
        assertFalse(StructureCatalog.isStructure("Scopia#table_lamp"))
        assertFalse(StructureCatalog.isStructure(null))

        assertTrue(StructureCatalog.isFloorToCeiling(StructureCatalog.pillarRoundID))
        assertTrue(StructureCatalog.isFloorToCeiling(StructureCatalog.pillarSquareID))
        assertFalse(StructureCatalog.isFloorToCeiling(StructureCatalog.beamID))

        assertTrue(StructureCatalog.isCeilingHung(StructureCatalog.beamID))
        assertFalse(StructureCatalog.isCeilingHung(StructureCatalog.mirrorID))

        assertTrue(StructureCatalog.isWallHung(StructureCatalog.mirrorID))
        assertFalse(StructureCatalog.isWallHung(StructureCatalog.rugID))
    }

    @Test
    fun applyPlaceFurniture_pillarsSpanLevelHeight() {
        val home = HomeFactory.emptyHome("t")
        val next = applyPlaceFurniture(home, StructureCatalog.pillarRound, 10.0, 20.0)
        val piece = next.furniture.single()
        assertEquals(StructureCatalog.pillarRoundID, piece.catalogID)
        assertEquals(0.0, piece.elevation, 1e-9)
        assertEquals(home.wallHeight, piece.height, 1e-9)
        assertEquals(10.0, piece.x, 1e-9)
        assertEquals(20.0, piece.y, 1e-9)
    }

    @Test
    fun applyPlaceFurniture_beamHangsAtCeiling() {
        val home = HomeFactory.emptyHome("t")
        val next = applyPlaceFurniture(home, StructureCatalog.beam, 0.0, 0.0)
        val piece = next.furniture.single()
        assertEquals(StructureCatalog.beamID, piece.catalogID)
        assertEquals(home.wallHeight - StructureCatalog.beam.height, piece.elevation, 1e-9)
        assertEquals(StructureCatalog.beam.height, piece.height, 1e-9)
    }

    @Test
    fun applyPlaceLabel_appendsAndDeleteRemoves() {
        val home = HomeFactory.emptyHome("t")
        val next = applyPlaceLabel(home, 5.0, 6.0, "  Hello  ")
        assertEquals(1, next.labels.size)
        val label = next.labels.single()
        assertEquals("Hello", label.text)
        assertEquals(5.0, label.x, 1e-9)
        assertEquals(6.0, label.y, 1e-9)
        assertEquals(home.selectedLevelID, label.level)
        assertTrue(applyPlaceLabel(home, 0.0, 0.0, "   ") === home)

        val deleted = deleteSelection(next, Selection.Annotation(label.id, isLabel = true))
        assertTrue(deleted.home.labels.isEmpty())
        assertEquals("Label deleted", deleted.toast)
        assertEquals(Selection.None, deleted.selection)
    }
}
