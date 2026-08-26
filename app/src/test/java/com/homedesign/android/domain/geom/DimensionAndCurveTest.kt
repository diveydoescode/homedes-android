package com.homedesign.android.domain.geom

import com.homedesign.android.domain.editor.AUTO_EXT_DIM_PREFIX
import com.homedesign.android.domain.editor.applyExteriorDimensionChain
import com.homedesign.android.domain.editor.applySpanBow
import com.homedesign.android.domain.editor.commitRectangleRoom
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class DimensionAndCurveTest {
    @Test
    fun wallLeftNormal_plusX_isMinusY() {
        val n = wallLeftNormal(100.0, 0.0)
        assertNotNull(n)
        assertEquals(0.0, n!!.x, 1e-9)
        assertEquals(-1.0, n.y, 1e-9)
    }

    @Test
    fun envelopeFaceCorners_outer_extendsThicknessHalf() {
        val home = commitRectangleRoom(
            HomeFactory.emptyHome("F"),
            vec(0.0, 0.0),
            vec(700.0, 400.0),
            thickness = 10.0,
            idPrefix = "r",
        )
        val edges = RoomDetection.exteriorWallEdges(home.walls)
        val corners = envelopeFaceCorners(
            edges,
            home.walls.associateBy { it.id },
            DimensionFaceMode.Outer,
        )
        assertEquals(4, corners.size)
        val xs = corners.map { it.x }.sorted()
        val ys = corners.map { it.y }.sorted()
        assertEquals(-5.0, xs.first(), 1e-6)
        assertEquals(705.0, xs.last(), 1e-6)
        assertEquals(-5.0, ys.first(), 1e-6)
        assertEquals(405.0, ys.last(), 1e-6)
    }

    @Test
    fun exteriorChain_rectangle_yieldsFourRuns() {
        val boxed = commitRectangleRoom(
            HomeFactory.emptyHome("Dim"),
            vec(0.0, 0.0),
            vec(400.0, 300.0),
            idPrefix = "r",
        )
        val next = applyExteriorDimensionChain(boxed)
        assertEquals(4, next.dimensionLines.size)
        assertTrue(next.dimensionLines.all { it.id.startsWith(AUTO_EXT_DIM_PREFIX) })
        assertEquals(boxed.topologyVersion + 1, next.topologyVersion)
        val roomMid = vec(200.0, 150.0)
        for (d in next.dimensionLines) {
            val midX = (d.xStart + d.xEnd) / 2.0 + ((d.yEnd - d.yStart) / hypot(d.xEnd - d.xStart, d.yEnd - d.yStart)) * d.offset
            val midY = (d.yStart + d.yEnd) / 2.0 + (-(d.xEnd - d.xStart) / hypot(d.xEnd - d.xStart, d.yEnd - d.yStart)) * d.offset
            val spanX = (d.xStart + d.xEnd) / 2.0
            val spanY = (d.yStart + d.yEnd) / 2.0
            val out = hypot(midX - roomMid.x, midY - roomMid.y)
            val on = hypot(spanX - roomMid.x, spanY - roomMid.y)
            assertTrue(out > on)
        }
    }

    @Test
    fun exteriorChain_rerun_replacesAutoKeepsManual() {
        val boxed = commitRectangleRoom(
            HomeFactory.emptyHome("Dim"),
            vec(0.0, 0.0),
            vec(400.0, 300.0),
            idPrefix = "r",
        )
        val first = applyExteriorDimensionChain(boxed)
        val withManual = com.homedesign.android.domain.editor.applyAddDimension(
            first,
            vec(50.0, 50.0),
            vec(150.0, 50.0),
        )
        assertEquals(5, withManual.dimensionLines.size)
        val second = applyExteriorDimensionChain(withManual)
        assertEquals(5, second.dimensionLines.size)
        val auto = second.dimensionLines.filter { it.id.startsWith(AUTO_EXT_DIM_PREFIX) }
        assertEquals(4, auto.size)
        val firstIds = first.dimensionLines.map { it.id }.toSet()
        assertTrue(auto.none { it.id in firstIds })
        assertTrue(second.dimensionLines.any { !it.id.startsWith(AUTO_EXT_DIM_PREFIX) })
    }

    @Test
    fun dimensionsForWall_splitsAtOpening() {
        val boxed = commitRectangleRoom(
            HomeFactory.emptyHome("Dim"),
            vec(0.0, 0.0),
            vec(400.0, 300.0),
            idPrefix = "r",
        )
        val wall = boxed.walls.first { abs(it.startY - it.endY) < 1e-9 && minOf(it.startY, it.endY) < 1.0 }
        val door = com.homedesign.android.domain.model.HomeDoorOrWindow(
            piece = com.homedesign.android.domain.model.HomePieceOfFurniture(
                id = "door",
                name = "Door",
                x = 200.0,
                y = wall.startY,
                width = 80.0,
                depth = 10.0,
                height = 210.0,
                angle = 0.0,
                level = wall.level,
            ),
        )
        val dims = DimensionMutation.dimensions(
            forWall = wall,
            inWalls = boxed.walls,
            openings = listOf(door),
        )
        assertEquals(3, dims.size)
        val unbroken = DimensionMutation.dimension(forWall = wall, inWalls = boxed.walls)
        assertEquals(unbroken.offset, dims[0].offset, 1e-9)
    }

    @Test
    fun setSpanBow_materialisesCurveProfile() {
        val wall = Wall(
            id = "w",
            startX = 0.0,
            startY = 0.0,
            endX = 400.0,
            endY = 0.0,
            thickness = 10.0,
            height = 250.0,
            level = HomeFactory.SYNTH_LEVEL_ID,
        )
        val bowed = WallCurveMutation.setSpanBow(listOf(wall), "w", 0, 0.45)
        val next = bowed.single()
        assertTrue(ArcWallGeometry.isCurved(next))
        assertEquals(null, next.arcExtent)
        assertNotNull(next.curveProfile)
        val bow = spanBow(next.curveProfile!!.spans.single())
        assertEquals(0.45, bow, 1e-6)
    }

    @Test
    fun setSpanBow_nearZeroClearsCurve() {
        var walls = listOf(
            Wall(
                id = "w",
                startX = 0.0,
                startY = 0.0,
                endX = 400.0,
                endY = 0.0,
                thickness = 10.0,
                height = 250.0,
                level = HomeFactory.SYNTH_LEVEL_ID,
                curveProfile = com.homedesign.android.domain.model.WallCurveProfile(
                    breaks = emptyList(),
                    spans = listOf(WallSpan.Arc(bow = 0.5)),
                ),
            ),
        )
        walls = WallCurveMutation.setSpanBow(walls, "w", 0, 0.0004)
        assertTrue(!ArcWallGeometry.isCurved(walls.single()))
        assertEquals(null, walls.single().curveProfile)
    }

    @Test
    fun insertBreakpoint_splitsSpan() {
        val wall = Wall(
            id = "w",
            startX = 0.0,
            startY = 0.0,
            endX = 400.0,
            endY = 0.0,
            thickness = 10.0,
            height = 250.0,
            level = HomeFactory.SYNTH_LEVEL_ID,
        )
        val walls = WallCurveMutation.insertBreakpoint(listOf(wall), "w", 0.5)
        val next = walls.single()
        assertNotNull(next.curveProfile)
        assertEquals(listOf(0.5), next.curveProfile!!.breaks)
        assertEquals(2, next.curveProfile!!.spans.size)
        val moved = WallCurveMutation.setSpanBow(walls, "w", 0, 0.4)
        assertTrue(ArcWallGeometry.isCurved(moved.single()))
        assertEquals(1, ArcWallGeometry.breakpointPositions(moved.single()).size)
        assertEquals(0.5, ArcWallGeometry.breakpointPositions(moved.single()).single().t, 1e-9)
    }

    @Test
    fun applyAddCurvePoint_bumpsTopology() {
        val home = HomeFactory.emptyHome("C").copy(
            walls = listOf(
                Wall(
                    id = "w",
                    startX = 0.0,
                    startY = 0.0,
                    endX = 300.0,
                    endY = 0.0,
                    thickness = 10.0,
                    height = 250.0,
                    level = HomeFactory.SYNTH_LEVEL_ID,
                ),
            ),
            topologyVersion = 2,
        )
        val next = com.homedesign.android.domain.editor.applyAddCurvePoint(home, "w", 0.4)
        assertEquals(3, next.topologyVersion)
        assertEquals(listOf(0.4), next.walls.single().curveProfile?.breaks)
    }

    @Test
    fun applySpanBow_bumpsTopology() {
        val home = HomeFactory.emptyHome("C").copy(
            walls = listOf(
                Wall(
                    id = "w",
                    startX = 0.0,
                    startY = 0.0,
                    endX = 300.0,
                    endY = 0.0,
                    thickness = 10.0,
                    height = 250.0,
                    level = HomeFactory.SYNTH_LEVEL_ID,
                ),
            ),
            topologyVersion = 2,
        )
        val next = applySpanBow(home, "w", 0, 0.3)
        assertTrue(ArcWallGeometry.isCurved(next.walls.single()))
        assertEquals(3, next.topologyVersion)
        assertTrue(abs(spanBow(effectiveProfile(next.walls.single()).spans.single()) - 0.3) < 1e-6)
    }
}
