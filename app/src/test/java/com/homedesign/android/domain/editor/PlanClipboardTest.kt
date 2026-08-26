package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.pasteOffsetCM
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlanClipboardTest {
    private fun piece(
        id: String,
        x: Double,
        y: Double,
        group: String? = null,
        name: String = "Sofa",
    ) = HomePieceOfFurniture(
        id = id,
        catalogID = "x#sofa",
        name = name,
        x = x,
        y = y,
        width = 200.0,
        depth = 90.0,
        height = 80.0,
        level = "L1",
        groupID = group,
    )

    private fun opening(
        id: String,
        x: Double,
        y: Double,
        width: Double = 90.0,
        level: String? = "L1",
    ) = HomeDoorOrWindow(
        piece = HomePieceOfFurniture(
            id = id,
            catalogID = "x#door",
            name = "Door",
            x = x,
            y = y,
            elevation = 0.0,
            width = width,
            depth = 12.0,
            height = 200.0,
            level = level,
        ),
        cutoutShape = null,
        sashes = emptyList(),
    )

    private fun wall(
        id: String = "w1",
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
        level: String? = "L2",
    ) = Wall(
        id = id,
        startX = fromX,
        startY = fromY,
        endX = toX,
        endY = toY,
        thickness = 10.0,
        height = 250.0,
        level = level,
    )

    @Test
    fun copyLonePieceCapturesJustThatPiece() {
        val home = HomeFactory.emptyHome("t").copy(
            furniture = listOf(piece("a", 0.0, 0.0), piece("b", 100.0, 0.0)),
            selectedLevelID = "L1",
        )
        val payload = PlanClipboard.encodeSelection(home, Selection.Furniture("a"))
        assertEquals(listOf("a"), payload?.pieces?.map { it.id })
        assertEquals(0, payload?.openings?.size)
    }

    @Test
    fun copyGroupedPieceFansInWholeGroup() {
        val home = HomeFactory.emptyHome("t").copy(
            furniture = listOf(
                piece("a", 0.0, 0.0, group = "g1"),
                piece("b", 100.0, 0.0, group = "g1"),
                piece("c", 500.0, 0.0, group = "g2"),
                piece("d", 900.0, 0.0),
            ),
        )
        val payload = PlanClipboard.encodeSelection(home, Selection.Furniture("a"))
        assertEquals(setOf("a", "b"), payload?.pieces?.map { it.id }?.toSet())
    }

    @Test
    fun copyOpeningCapturesThatOpening() {
        val home = HomeFactory.emptyHome("t").copy(
            doorsAndWindows = listOf(opening("o1", 50.0, 0.0), opening("o2", 150.0, 0.0)),
        )
        val payload = PlanClipboard.encodeSelection(home, Selection.Opening("o1"))
        assertNotNull(payload)
        assertEquals(1, payload!!.openings.size)
        assertEquals("o1", payload.openings[0].piece.id)
        assertTrue(payload.pieces.isEmpty())
    }

    @Test
    fun copyUnknownReturnsNull() {
        val home = HomeFactory.emptyHome("t")
        assertNull(PlanClipboard.encodeSelection(home, Selection.Furniture("nope")))
        assertNull(PlanClipboard.encodeSelection(home, Selection.Opening("nope")))
        assertNull(PlanClipboard.encodeSelection(home, Selection.Wall("w")))
    }

    @Test
    fun materializeAnchorsCentroidAtTarget() {
        val payload = PlanClipboardPayload(
            pieces = listOf(piece("a", 0.0, 100.0), piece("b", 200.0, 100.0)),
        )
        val out = PlanClipboard.materialize(payload, atX = 500.0, atY = 700.0, level = "L9")
        val xs = out.pieces.map { it.x }.sorted()
        assertEquals(listOf(400.0, 600.0), xs)
        assertTrue(out.pieces.all { abs(it.y - 700.0) < 1e-9 })
        assertTrue(out.pieces.all { it.level == "L9" })
    }

    @Test
    fun materializeWithoutTargetOffsetsBy40() {
        val payload = PlanClipboardPayload(pieces = listOf(piece("a", 100.0, 200.0)))
        val out = PlanClipboard.materialize(payload, atX = null, atY = null, level = "L1")
        assertEquals(100.0 + pasteOffsetCM, out.pieces[0].x, 1e-9)
        assertEquals(200.0 + pasteOffsetCM, out.pieces[0].y, 1e-9)
    }

    @Test
    fun materializeMintsFreshIdsAndRemapsGroups() {
        val payload = PlanClipboardPayload(
            pieces = listOf(
                piece("a", 0.0, 0.0, group = "g1"),
                piece("b", 100.0, 0.0, group = "g1"),
                piece("c", 200.0, 0.0),
            ),
        )
        val out = PlanClipboard.materialize(payload, null, null, "L1")
        assertTrue(out.pieces.map { it.id }.toSet().intersect(setOf("a", "b", "c")).isEmpty())
        val groups = out.pieces.mapNotNull { it.groupID }
        assertEquals(2, groups.size)
        assertEquals(1, groups.toSet().size)
        assertNotEquals("g1", groups[0])
        assertNull(out.pieces[2].groupID)
    }

    @Test
    fun materializeRewritesOpeningLevel() {
        val payload = PlanClipboardPayload(
            pieces = listOf(piece("a", 0.0, 0.0)),
            openings = listOf(opening("o", 9.0, 9.0)),
        )
        val out = PlanClipboard.materialize(payload, null, null, "L7")
        assertEquals("L7", out.pieces[0].level)
        assertEquals("L7", out.openings[0].piece.level)
    }

    @Test
    fun rebindSeatsOpeningOnWallAxisWithWallAngle() {
        val w = wall(fromX = 0.0, fromY = 0.0, toX = 400.0, toY = 0.0)
        val o = opening("o", 123.0, 456.0, width = 90.0)
        val bound = PlanClipboard.rebind(o, w, vec(200.0, 35.0))
        assertNotNull(bound)
        assertEquals(200.0, bound!!.piece.x, 1e-9)
        assertEquals(0.0, bound.piece.y, 1e-9)
        assertEquals(0.0, bound.piece.angle, 1e-9)
        assertEquals("L2", bound.piece.level)
        assertEquals(90.0, bound.piece.width, 1e-9)
    }

    @Test
    fun rebindClampsSoCutoutFits() {
        val w = wall(fromX = 0.0, fromY = 0.0, toX = 400.0, toY = 0.0)
        val o = opening("o", 0.0, 0.0, width = 100.0)
        val bound = PlanClipboard.rebind(o, w, vec(395.0, -20.0))
        assertNotNull(bound)
        assertEquals(350.0, bound!!.piece.x, 1e-9)
    }

    @Test
    fun rebindRefusesWallShorterThanOpening() {
        val w = wall(fromX = 0.0, fromY = 0.0, toX = 80.0, toY = 0.0)
        val o = opening("o", 0.0, 0.0, width = 100.0)
        assertNull(PlanClipboard.rebind(o, w, vec(40.0, 0.0)))
    }

    @Test
    fun pasteOpeningRebindsToClosestWallOnActiveLevel() {
        val level = HomeFactory.SYNTH_LEVEL_ID
        val w = wall(
            id = "w1",
            fromX = 0.0,
            fromY = 0.0,
            toX = 400.0,
            toY = 0.0,
            level = level,
        )
        val door = opening("door", 100.0, 0.0, width = 90.0, level = level)
        val home = HomeFactory.emptyHome("t").copy(
            walls = listOf(w),
            doorsAndWindows = listOf(door),
            selectedLevelID = level,
        )
        val payload = PlanClipboard.encodeSelection(home, Selection.Opening("door"))
        assertNotNull(payload)
        val result = PlanClipboard.applyPaste(home, payload!!)
        assertEquals(1, result.openingIds.size)
        assertEquals(2, result.home.doorsAndWindows.size)
        val pasted = result.home.doorsAndWindows.first { it.piece.id == result.openingIds[0] }
        // Offset +40,+40 then rebind onto the wall centreline.
        assertEquals(0.0, pasted.piece.y, 1e-6)
        assertEquals(0.0, pasted.piece.angle, 1e-6)
        assertEquals(level, pasted.piece.level)
        assertEquals(home.topologyVersion + 1, result.home.topologyVersion)
    }

    @Test
    fun duplicateOpeningSelectsFreshId() {
        val level = HomeFactory.SYNTH_LEVEL_ID
        val w = wall(fromX = 0.0, fromY = 0.0, toX = 500.0, toY = 0.0, level = level)
        val door = opening("door", 200.0, 0.0, width = 80.0, level = level)
        val home = HomeFactory.emptyHome("t").copy(
            walls = listOf(w),
            doorsAndWindows = listOf(door),
            selectedLevelID = level,
        )
        val result = PlanClipboard.applyDuplicate(home, Selection.Opening("door"))
        assertEquals(1, result.openingIds.size)
        assertNotEquals("door", result.openingIds[0])
        assertEquals(2, result.home.doorsAndWindows.size)
    }

    @Test
    fun pasteFurnitureOnlyBumpsFurnitureRevision() {
        val home = HomeFactory.emptyHome("t").copy(
            furniture = listOf(piece("a", 10.0, 20.0)),
        )
        val payload = PlanClipboard.encodeSelection(home, Selection.Furniture("a"))!!
        val result = PlanClipboard.applyPaste(home, payload)
        assertEquals(1, result.pieceIds.size)
        assertTrue(result.openingIds.isEmpty())
        assertEquals(home.furnitureRevision + 1, result.home.furnitureRevision)
        assertEquals(home.topologyVersion, result.home.topologyVersion)
    }
}
