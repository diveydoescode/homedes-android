package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.HomeFactory
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionLengthTest {

    @Test
    fun applyDimensionLength_scalesEndKeepingStart() {
        val dim = DimensionLine(
            id = "d1",
            xStart = 0.0,
            yStart = 0.0,
            xEnd = 100.0,
            yEnd = 0.0,
            offset = 35.0,
            level = "level0",
        )
        val home = HomeFactory.emptyHome("t").copy(dimensionLines = listOf(dim))
        val next = applyDimensionLength(home, "d1", 250.0)
        val out = next.dimensionLines.single()
        assertEquals(0.0, out.xStart, 1e-9)
        assertEquals(0.0, out.yStart, 1e-9)
        assertEquals(250.0, out.xEnd, 1e-9)
        assertEquals(0.0, out.yEnd, 1e-9)
        assertTrue(next.topologyVersion > home.topologyVersion)
    }

    @Test
    fun applyDimensionLength_preservesDirectionOnDiagonal() {
        val dim = DimensionLine(
            id = "d2",
            xStart = 10.0,
            yStart = 20.0,
            xEnd = 40.0,
            yEnd = 60.0,
            offset = 35.0,
        )
        val home = HomeFactory.emptyHome("t").copy(dimensionLines = listOf(dim))
        val next = applyDimensionLength(home, "d2", 100.0)
        val out = next.dimensionLines.single()
        assertEquals(10.0, out.xStart, 1e-9)
        assertEquals(20.0, out.yStart, 1e-9)
        assertEquals(100.0, hypot(out.xEnd - out.xStart, out.yEnd - out.yStart), 1e-6)
        // Direction (3,4) unit → end = start + 100*(0.6,0.8)
        assertEquals(70.0, out.xEnd, 1e-6)
        assertEquals(100.0, out.yEnd, 1e-6)
    }

    @Test
    fun applyDimensionLength_rejectsTooShortOrMissing() {
        val dim = DimensionLine(
            id = "d3",
            xStart = 0.0,
            yStart = 0.0,
            xEnd = 80.0,
            yEnd = 0.0,
            offset = 35.0,
        )
        val home = HomeFactory.emptyHome("t").copy(dimensionLines = listOf(dim))
        assertTrue(applyDimensionLength(home, "d3", 0.5) === home)
        assertTrue(applyDimensionLength(home, "missing", 120.0) === home)
    }

    @Test
    fun applyDimensionEndpoint_movesStartOrEnd() {
        val dim = DimensionLine(
            id = "d4",
            xStart = 0.0,
            yStart = 0.0,
            xEnd = 200.0,
            yEnd = 0.0,
            offset = 35.0,
        )
        val home = HomeFactory.emptyHome("t").copy(dimensionLines = listOf(dim))
        val movedEnd = applyDimensionEndpoint(home, "d4", atStart = false, 320.0, 0.0)
        assertEquals(320.0, movedEnd.dimensionLines.single().xEnd, 1e-9)
        assertEquals(0.0, movedEnd.dimensionLines.single().xStart, 1e-9)
        val movedStart = applyDimensionEndpoint(home, "d4", atStart = true, -40.0, 10.0)
        assertEquals(-40.0, movedStart.dimensionLines.single().xStart, 1e-9)
        assertEquals(10.0, movedStart.dimensionLines.single().yStart, 1e-9)
    }

    @Test
    fun signedDimensionOffset_flipsAcrossSegment() {
        val above = signedDimensionOffset(vec(0.0, 0.0), vec(200.0, 0.0), vec(100.0, -40.0))
        val below = signedDimensionOffset(vec(0.0, 0.0), vec(200.0, 0.0), vec(100.0, 40.0))
        assertEquals(40.0, above, 1e-9)
        assertEquals(-40.0, below, 1e-9)
        val dim = DimensionLine(
            id = "d5",
            xStart = 0.0,
            yStart = 0.0,
            xEnd = 200.0,
            yEnd = 0.0,
            offset = 35.0,
        )
        val home = HomeFactory.emptyHome("t").copy(dimensionLines = listOf(dim))
        val flipped = applyDimensionOffset(home, "d5", below)
        assertEquals(-40.0, flipped.dimensionLines.single().offset, 1e-9)
    }
}
