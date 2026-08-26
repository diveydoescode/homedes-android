package com.homedesign.android.domain.geom

import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.editor.applyPlaceFurniture
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Level
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.render3d.HomeExtrusion
import com.homedesign.android.domain.render3d.MeshTri
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaircaseCutoutTest {

    private val outer = listOf(
        vec(0.0, 0.0),
        vec(600.0, 0.0),
        vec(600.0, 400.0),
        vec(0.0, 400.0),
    )
    private val hole = listOf(
        vec(200.0, 150.0),
        vec(320.0, 150.0),
        vec(320.0, 250.0),
        vec(200.0, 250.0),
    )

    private fun triangleArea(a: Vec2, b: Vec2, c: Vec2): Double =
        abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2.0

    private fun pointInTriangle(p: Vec2, a: Vec2, b: Vec2, c: Vec2, strict: Boolean = true): Boolean {
        val d1 = (p.x - b.x) * (a.y - b.y) - (a.x - b.x) * (p.y - b.y)
        val d2 = (p.x - c.x) * (b.y - c.y) - (b.x - c.x) * (p.y - c.y)
        val d3 = (p.x - a.x) * (c.y - a.y) - (c.x - a.x) * (p.y - a.y)
        return if (strict) {
            (d1 > 0 && d2 > 0 && d3 > 0) || (d1 < 0 && d2 < 0 && d3 < 0)
        } else {
            val hasNeg = d1 < 0 || d2 < 0 || d3 < 0
            val hasPos = d1 > 0 || d2 > 0 || d3 > 0
            !(hasNeg && hasPos)
        }
    }

    @Test
    fun holeTriangulationCoversOuterMinusHole() {
        val (positions, indices) = PolygonTriangulator.triangulate(outer, listOf(hole))
        assertEquals(8, positions.size)
        assertEquals(0, indices.size % 3)
        var area = 0.0
        var holeCentroidCovered = false
        val holeCentre = vec(260.0, 200.0)
        var k = 0
        while (k < indices.size) {
            val a = positions[indices[k]]
            val b = positions[indices[k + 1]]
            val c = positions[indices[k + 2]]
            area += triangleArea(a, b, c)
            if (pointInTriangle(holeCentre, a, b, c)) holeCentroidCovered = true
            k += 3
        }
        assertEquals(240_000.0 - 12_000.0, area, 1.0)
        assertFalse("hole centre must stay uncovered", holeCentroidCovered)
    }

    @Test
    fun noHolesFallsBackToPlainTriangulation() {
        val (positions, indices) = PolygonTriangulator.triangulate(outer, emptyList())
        assertEquals(4, positions.size)
        assertEquals(6, indices.size)
    }

    @Test
    fun stairFootprintBecomesHoleWhenFullyInsideRoom() {
        val roomPts = listOf(
            Point(0.0, 0.0),
            Point(600.0, 0.0),
            Point(600.0, 400.0),
            Point(0.0, 400.0),
        )
        val stair = stair(x = 300.0, y = 200.0)
        val holes = StaircaseCutout.holesInRoom(roomPts, listOf(stair))
        assertEquals(1, holes.size)
        val expected = FurnitureGeometry.cornerPoints(stair)
        assertEquals(expected.size, holes[0].size)
        for (i in expected.indices) {
            assertEquals(expected[i].x, holes[0][i].x, 1e-9)
            assertEquals(expected[i].y, holes[0][i].y, 1e-9)
        }
    }

    @Test
    fun rotatedStairFootprintUsesPieceAngle() {
        val roomPts = listOf(
            Point(0.0, 0.0),
            Point(600.0, 0.0),
            Point(600.0, 400.0),
            Point(0.0, 400.0),
        )
        val stair = stair(x = 300.0, y = 200.0, angle = PI / 2)
        val holes = StaircaseCutout.holesInRoom(roomPts, listOf(stair))
        assertEquals(1, holes.size)
        val xs = holes[0].map { it.x }
        val ys = holes[0].map { it.y }
        // 100×240 rotated 90° → 240×100 AABB around centre.
        assertEquals(180.0, xs.minOrNull()!!, 1e-6)
        assertEquals(420.0, xs.maxOrNull()!!, 1e-6)
        assertEquals(150.0, ys.minOrNull()!!, 1e-6)
        assertEquals(250.0, ys.maxOrNull()!!, 1e-6)
    }

    @Test
    fun stairsOutsideRoomAreIgnored() {
        val roomPts = listOf(
            Point(0.0, 0.0),
            Point(600.0, 0.0),
            Point(600.0, 400.0),
            Point(0.0, 400.0),
        )
        val holes = StaircaseCutout.holesInRoom(
            roomPts,
            listOf(stair(x = 2000.0, y = 2000.0)),
        )
        assertTrue(holes.isEmpty())
    }

    @Test
    fun stairsCutTheFloorAboveNotOwnFloor() {
        val home = twoStoreyHome(stair("s1", "g", 300.0, 200.0))
        val upper = HomeExtrusion.build(home, level = "f")
        val ground = HomeExtrusion.build(home, level = "g")
        val upperFloor = upper.meshes.first()
        val groundFloor = ground.meshes.first()
        // Fan quad is 6 verts; a hole expands the mesh.
        assertEquals(6, groundFloor.positions.size / 3)
        assertTrue(upperFloor.positions.size / 3 > 6)

        val roomArea = 600.0 * 400.0
        val stairArea = 100.0 * 240.0
        val cutArea = meshPlanAreaCm2(upperFloor)
        assertEquals(roomArea - stairArea, cutArea, 1500.0)
        assertFalse(meshCoversPlanPoint(upperFloor, 300.0, 200.0))
        assertTrue(meshCoversPlanPoint(groundFloor, 300.0, 200.0))
    }

    @Test
    fun nonCuttingAndOutsideStairsLeaveFloorsAlone() {
        val home = twoStoreyHome(
            stair("plain", "g", 300.0, 200.0, cut = false),
            stair("outside", "g", 2000.0, 2000.0),
        )
        val upper = HomeExtrusion.build(home, level = "f")
        assertEquals(6, upper.meshes.first().positions.size / 3)
    }

    @Test
    fun floorsWithoutStairsKeepFanQuad() {
        val home = twoStoreyHome()
        val ground = HomeExtrusion.build(home, level = "g")
        val upper = HomeExtrusion.build(home, level = "f")
        assertEquals(6, ground.meshes.first().positions.size / 3)
        assertEquals(6, upper.meshes.first().positions.size / 3)
    }

    @Test
    fun applyPlaceFurniture_flagsStaircaseCategory() {
        val entry = CatalogEntry(
            id = "test#stair",
            catalog = "generic",
            name = "Straight staircase",
            category = "Staircases",
            width = 100.0,
            depth = 240.0,
            height = 250.0,
        )
        val next = applyPlaceFurniture(HomeFactory.emptyHome("t"), entry, 100.0, 100.0)
        assertEquals(true, next.furniture.single().staircaseCutOut)
    }

    @Test
    fun applyPlaceFurniture_nonStairLeavesCutoutNull() {
        val entry = CatalogEntry(
            id = "test#sofa",
            catalog = "generic",
            name = "Sofa",
            category = "Seating",
            width = 160.0,
            depth = 80.0,
            height = 80.0,
        )
        val next = applyPlaceFurniture(HomeFactory.emptyHome("t"), entry, 100.0, 100.0)
        assertNull(next.furniture.single().staircaseCutOut)
    }

    private fun stair(
        id: String = "s",
        level: String = "g",
        x: Double,
        y: Double,
        cut: Boolean = true,
        angle: Double = 0.0,
        width: Double = 100.0,
        depth: Double = 240.0,
    ): HomePieceOfFurniture = HomePieceOfFurniture(
        id = id,
        name = "Straight staircase",
        x = x,
        y = y,
        angle = angle,
        width = width,
        depth = depth,
        height = 250.0,
        level = level,
        staircaseCutOut = if (cut) true else null,
    )

    private fun twoStoreyHome(vararg stairs: HomePieceOfFurniture): Home {
        val points = listOf(
            Point(0.0, 0.0),
            Point(600.0, 0.0),
            Point(600.0, 400.0),
            Point(0.0, 400.0),
        )
        return Home(
            wallHeight = 250.0,
            selectedLevelID = "f",
            levels = listOf(
                Level(
                    id = "g",
                    name = "Ground",
                    elevation = 0.0,
                    floorThickness = 12.0,
                    height = 250.0,
                    elevationIndex = 0,
                    visible = true,
                    viewable = true,
                ),
                Level(
                    id = "f",
                    name = "First",
                    elevation = 262.0,
                    floorThickness = 12.0,
                    height = 250.0,
                    elevationIndex = 1,
                    visible = true,
                    viewable = true,
                ),
            ),
            rooms = listOf(
                Room(id = "rg", points = points, level = "g"),
                Room(id = "rf", points = points, level = "f"),
            ),
            furniture = stairs.toList(),
        )
    }

    /** Mesh positions are metres; plan-cm area is 10_000× the metre-XZ area. */
    private fun meshPlanAreaCm2(mesh: MeshTri): Double {
        var area = 0.0
        var i = 0
        val p = mesh.positions
        while (i + 8 < p.size) {
            val a = vec(p[i].toDouble() * 100.0, p[i + 2].toDouble() * 100.0)
            val b = vec(p[i + 3].toDouble() * 100.0, p[i + 5].toDouble() * 100.0)
            val c = vec(p[i + 6].toDouble() * 100.0, p[i + 8].toDouble() * 100.0)
            area += triangleArea(a, b, c)
            i += 9
        }
        return area
    }

    private fun meshCoversPlanPoint(mesh: MeshTri, xCm: Double, yCm: Double): Boolean {
        val p = vec(xCm, yCm)
        var i = 0
        val pos = mesh.positions
        while (i + 8 < pos.size) {
            val a = vec(pos[i].toDouble() * 100.0, pos[i + 2].toDouble() * 100.0)
            val b = vec(pos[i + 3].toDouble() * 100.0, pos[i + 5].toDouble() * 100.0)
            val c = vec(pos[i + 6].toDouble() * 100.0, pos[i + 8].toDouble() * 100.0)
            if (pointInTriangle(p, a, b, c, strict = false)) return true
            i += 9
        }
        return false
    }
}
