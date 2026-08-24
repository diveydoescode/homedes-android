package com.homedesign.android.domain.geom

import com.homedesign.android.domain.editor.applyMirrorPlan
import com.homedesign.android.domain.editor.applyMirrorSelection
import com.homedesign.android.domain.editor.applyRotatePlan
import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.PlanLabel
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallCurveProfile
import com.homedesign.android.domain.model.WallSpan
import kotlin.math.PI
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTransformTest {

    private fun wall(
        id: String,
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
        extra: Wall.() -> Wall = { this },
    ): Wall = Wall(
        id = id,
        startX = sx,
        startY = sy,
        endX = ex,
        endY = ey,
        thickness = 10.0,
        height = 250.0,
        leftSideColor = "FFLEFT",
        rightSideColor = "FFRIGHT",
    ).extra()

    private fun piece(id: String, x: Double, y: Double): HomePieceOfFurniture =
        HomePieceOfFurniture(
            id = id,
            name = "Sofa",
            x = x,
            y = y,
            width = 50.0,
            depth = 30.0,
            height = 40.0,
        )

    private fun payload(): PlanTransform.Payload = PlanTransform.Payload(
        walls = listOf(wall("a", 0.0, 0.0, 100.0, 0.0)),
        rooms = listOf(
            Room(
                id = "r",
                points = listOf(
                    Point(0.0, 0.0),
                    Point(100.0, 0.0),
                    Point(100.0, 80.0),
                    Point(0.0, 80.0),
                ),
            ),
        ),
        furniture = listOf(piece("f", 20.0, 40.0)),
        dimensionLines = listOf(
            DimensionLine(id = "d", xStart = 0.0, yStart = 0.0, xEnd = 100.0, yEnd = 0.0, offset = 25.0),
        ),
        labels = listOf(PlanLabel(id = "l", x = 10.0, y = 10.0, text = "Hi", angle = 0.0)),
        compass = JsonObject(
            mapOf(
                "x" to JsonPrimitive(5.0),
                "y" to JsonPrimitive(5.0),
                "diameter" to JsonPrimitive(100.0),
                "northDirection" to JsonPrimitive(0.0),
            ),
        ),
    )

    @Test
    fun rotate90Cw_mapsPlusXToPlusY() {
        val out = PlanTransform.rotate(payload(), PlanRotation.Clockwise, vec(0.0, 0.0))
        assertEquals(0.0, out.walls[0].endX, 1e-9)
        assertEquals(100.0, out.walls[0].endY, 1e-9)
        assertEquals(PI / 2, out.furniture[0].angle, 1e-9)
    }

    @Test
    fun northDirection_turnsWithDrawing() {
        val out = PlanTransform.rotate(payload(), PlanRotation.Clockwise, vec(0.0, 0.0))
        val north = (out.compass as JsonObject)["northDirection"]!!.jsonPrimitive.doubleOrNull!!
        assertEquals(PI / 2, north, 1e-9)
    }

    @Test
    fun doubleVerticalMirror_isIdentity() {
        val source = payload()
        val once = PlanTransform.mirror(source, PlanAxis.Vertical, vec(50.0, 0.0))
        val twice = PlanTransform.mirror(once, PlanAxis.Vertical, vec(50.0, 0.0))
        assertEquals(source.walls[0].startX, twice.walls[0].startX, 1e-9)
        assertEquals(source.walls[0].leftSideColor, twice.walls[0].leftSideColor)
        assertEquals(source.furniture[0].modelMirrored, twice.furniture[0].modelMirrored)
        assertEquals(source.dimensionLines[0].offset, twice.dimensionLines[0].offset, 1e-9)
    }

    @Test
    fun mirror_flipsBowSign() {
        val p = payload().copy(
            walls = listOf(
                wall("a", 0.0, 0.0, 100.0, 0.0) {
                    copy(
                        arcExtent = 0.4,
                        curveProfile = WallCurveProfile(spans = listOf(WallSpan.Arc(bow = 0.25))),
                    )
                },
            ),
        )
        val out = PlanTransform.mirror(p, PlanAxis.Horizontal, vec(0.0, 0.0))
        assertEquals(-0.4, out.walls[0].arcExtent!!, 1e-9)
        val arc = out.walls[0].curveProfile!!.spans[0] as WallSpan.Arc
        assertEquals(-0.25, arc.bow!!, 1e-9)
        assertEquals(-25.0, out.dimensionLines[0].offset, 1e-9)
    }

    @Test
    fun mirrorSelection_onlyMovesSelectedFurniture() {
        val home = HomeFactory.emptyHome("sel").copy(
            furniture = listOf(piece("a", 10.0, 20.0), piece("b", 80.0, 20.0)),
        )
        val next = applyMirrorSelection(home, listOf("a"), PlanAxis.Vertical)
        assertEquals(80.0, next.furniture.first { it.id == "b" }.x, 1e-9)
        assertTrue(next.furniture.first { it.id == "a" }.modelMirrored)
        assertEquals(home.topologyVersion + 1, next.topologyVersion)
    }

    @Test
    fun applyMirrorPlan_noopOnEmpty() {
        val empty = HomeFactory.emptyHome("e")
        assertSame(empty, applyMirrorPlan(empty, PlanAxis.Vertical))
        assertSame(empty, applyRotatePlan(empty, PlanRotation.Clockwise))
    }
}
