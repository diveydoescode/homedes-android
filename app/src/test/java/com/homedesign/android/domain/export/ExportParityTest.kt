package com.homedesign.android.domain.export

import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.model.Wall
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportParityTest {

    private fun sampleHome(): com.homedesign.android.domain.model.Home {
        val level = HomeFactory.SYNTH_LEVEL_ID
        return HomeFactory.emptyHome("Demo Plan").copy(
            walls = listOf(
                Wall(
                    id = "w1",
                    startX = 0.0,
                    startY = 0.0,
                    endX = 400.0,
                    endY = 0.0,
                    thickness = 12.0,
                    height = 250.0,
                    level = level,
                ),
                Wall(
                    id = "w2",
                    startX = 400.0,
                    startY = 0.0,
                    endX = 400.0,
                    endY = 300.0,
                    thickness = 12.0,
                    height = 250.0,
                    level = level,
                ),
                Wall(
                    id = "w3",
                    startX = 400.0,
                    startY = 300.0,
                    endX = 0.0,
                    endY = 300.0,
                    thickness = 12.0,
                    height = 250.0,
                    level = level,
                ),
                Wall(
                    id = "w4",
                    startX = 0.0,
                    startY = 300.0,
                    endX = 0.0,
                    endY = 0.0,
                    thickness = 12.0,
                    height = 250.0,
                    level = level,
                ),
            ),
            rooms = listOf(
                Room(
                    id = "r1",
                    name = "Living",
                    points = listOf(
                        Point(0.0, 0.0),
                        Point(400.0, 0.0),
                        Point(400.0, 300.0),
                        Point(0.0, 300.0),
                    ),
                    level = level,
                ),
            ),
            furniture = listOf(
                HomePieceOfFurniture(
                    id = "f1",
                    name = "Sofa",
                    x = 200.0,
                    y = 150.0,
                    width = 180.0,
                    depth = 80.0,
                    height = 85.0,
                    visible = true,
                    level = level,
                ),
            ),
        )
    }

    @Test
    fun dxf_includesWebLayerNamesFurnitureAndTitle() {
        val file = exportDXF(sampleHome(), UnitSystem.Millimetre)
        assertNotNull(file)
        val text = file!!.bytes.toString(Charsets.UTF_8)
        for (layer in listOf(
            "WALLS", "ROOMS", "ROOM_LABELS", "FURNITURE", "FURN_LABELS",
            "DOORS_WINDOWS", "A-DIMS", "TITLE",
        )) {
            assertTrue("missing layer $layer", text.contains("\n$layer\n") || text.contains("\n8\n$layer\n"))
        }
        assertTrue(text.contains("Living"))
        assertTrue(text.contains("Sofa"))
        assertTrue(text.contains("Demo Plan"))
    }

    @Test
    fun pdf_emitsNonEmptyContractorSheet() {
        val file = exportPDF(sampleHome(), UnitSystem.Metric)
        assertNotNull(file)
        assertTrue(file!!.bytes.size > 200)
        assertTrue(file.bytes.decodeToString(0, 8).startsWith("%PDF"))
        assertTrue(file.filename.endsWith(".pdf"))
    }

    @Test
    fun sheetTitle_includesLevelWhenNamed() {
        val home = sampleHome()
        val levelId = home.selectedLevelID
        val withLevel = home.copy(
            levels = home.levels.map {
                if (it.id == levelId) it.copy(name = "Ground") else it
            },
        )
        assertTrue(sheetTitle(withLevel, levelId).contains("Ground"))
    }
}
