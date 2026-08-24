package com.homedesign.android.domain

import com.homedesign.android.domain.editor.applyFurnitureRotate
import com.homedesign.android.domain.editor.commitFurnitureMove
import com.homedesign.android.domain.geom.FurnitureSvgParse
import com.homedesign.android.domain.geom.rotateHandlePosition
import com.homedesign.android.domain.geom.snapFurnitureAngle
import com.homedesign.android.domain.geom.wrapFurnitureAngle
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Wall
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureGeomTest {

    @Test
    fun snapFurnitureAngle_snapsWithinWindow() {
        val step = 15.0 * PI / 180.0
        val near = step + (2.0 * PI / 180.0) // 2° off a 15° step (< 3° window)
        assertEquals(step, snapFurnitureAngle(near), 1e-9)
    }

    @Test
    fun snapFurnitureAngle_keepsOutsideWindow() {
        val raw = 7.0 * PI / 180.0 // midway-ish, outside 3° of 0° and 15°
        assertEquals(raw, snapFurnitureAngle(raw), 1e-9)
    }

    @Test
    fun wrapFurnitureAngle_normalisesNegative() {
        assertEquals(1.5 * PI, wrapFurnitureAngle(-0.5 * PI), 1e-9)
    }

    @Test
    fun furnitureSvgParse_readsViewBoxAndPaths() {
        val raw = """
            <svg viewBox="0 0 100 50" xmlns="http://www.w3.org/2000/svg">
              <rect x="0" y="0" width="100" height="50" fill="#fff"/>
              <path d="M 0 0 L 100 0"/>
              <path d="M 0 50 L 100 50"/>
            </svg>
        """.trimIndent()
        val parsed = FurnitureSvgParse.parse(raw)
        assertNotNull(parsed)
        assertEquals(100.0, parsed!!.width, 1e-9)
        assertEquals(50.0, parsed.height, 1e-9)
        assertEquals(2, parsed.paths.size)
    }

    @Test
    fun furnitureSvgParse_emptyPathsReturnsNull() {
        assertNull(FurnitureSvgParse.parse("""<svg viewBox="0 0 10 10"><rect/></svg>"""))
    }

    @Test
    fun assetPath_normalisesCatalogIcon() {
        assertEquals("svg/bed.svg", FurnitureSvgParse.assetPath("svg/bed.svg"))
        assertEquals("svg/bed.svg", FurnitureSvgParse.assetPath("/svg/bed.svg"))
        assertEquals("svg/sofa.svg", FurnitureSvgParse.assetPath("sofa.svg"))
    }

    @Test
    fun rotateHandle_isBeyondFrontEdge() {
        val piece = HomePieceOfFurniture(
            id = "p",
            x = 0.0,
            y = 0.0,
            width = 100.0,
            depth = 40.0,
            height = 80.0,
            angle = 0.0,
        )
        // angle 0 → front (0,1); half depth 20; scale 1 → +24 stick
        val h = rotateHandlePosition(piece, scale = 1.0)
        assertEquals(0.0, h.x, 1e-9)
        assertEquals(44.0, h.y, 1e-9)
    }

    @Test
    fun applyFurnitureRotate_bumpsRevision() {
        val start = HomeFactory.emptyHome("t").copy(
            furniture = listOf(
                HomePieceOfFurniture(
                    id = "s",
                    x = 10.0,
                    y = 20.0,
                    width = 80.0,
                    depth = 40.0,
                    height = 70.0,
                ),
            ),
        )
        val next = applyFurnitureRotate(start, "s", PI / 2)
        assertEquals(PI / 2, next.furniture[0].angle, 1e-9)
        assertEquals(start.furnitureRevision + 1, next.furnitureRevision)
    }

    @Test
    fun commitFurnitureMove_snapsToWallKeepAngle() {
        val wall = Wall(
            id = "w",
            startX = 0.0,
            startY = 0.0,
            endX = 400.0,
            endY = 0.0,
            thickness = 20.0,
            height = 250.0,
            level = "level0",
        )
        val piece = HomePieceOfFurniture(
            id = "s",
            x = 200.0,
            y = 40.0, // near top face of wall
            width = 120.0,
            depth = 60.0,
            height = 80.0,
            angle = 0.0,
            level = "level0",
        )
        val home = HomeFactory.emptyHome("t").copy(
            walls = listOf(wall),
            furniture = listOf(piece),
            selectedLevelID = "level0",
        )
        val next = commitFurnitureMove(home, "s", 200.0, 40.0)
        val moved = next.furniture[0]
        assertEquals(0.0, moved.angle, 1e-9)
        // flush: halfThickness 10 + halfDepth 30 = 40 from wall centreline y=0
        assertEquals(40.0, moved.y, 1e-6)
        assertTrue(next.furnitureRevision > home.furnitureRevision)
    }
}
