package com.homedesign.android.domain.geom

import com.homedesign.android.domain.editor.applyGroup
import com.homedesign.android.domain.editor.applyUngroup
import com.homedesign.android.domain.editor.commitFurnitureMove
import com.homedesign.android.domain.editor.sharedGroupID
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.HomePieceOfFurniture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureGroupMoveTest {

    @Test
    fun fanDeltaToGroup_movesOtherMembers() {
        val pieces = listOf(
            piece("a", 0.0, 0.0, "g1"),
            piece("b", 100.0, 50.0, "g1"),
            piece("c", 200.0, 200.0, "g1"),
        )
        val moves = FurnitureGroupMove.fanDeltaToGroup(pieces, "a", vec(10.0, -5.0))
        assertEquals(2, moves.size)
        assertEquals(110.0, moves.first { it.id == "b" }.x, 1e-9)
        assertEquals(45.0, moves.first { it.id == "b" }.y, 1e-9)
        assertEquals(210.0, moves.first { it.id == "c" }.x, 1e-9)
        assertEquals(195.0, moves.first { it.id == "c" }.y, 1e-9)
        assertTrue(moves.none { it.id == "a" })
    }

    @Test
    fun fanDeltaToGroup_ungroupedReturnsEmpty() {
        val pieces = listOf(piece("solo", 0.0, 0.0), piece("other", 50.0, 50.0))
        assertTrue(FurnitureGroupMove.fanDeltaToGroup(pieces, "solo", vec(10.0, 10.0)).isEmpty())
    }

    @Test
    fun expandIDs_includesSharedGroup() {
        val pieces = listOf(
            piece("a", 0.0, 0.0, "g1"),
            piece("b", 40.0, 0.0, "g1"),
            piece("solo", 80.0, 0.0),
        )
        val ids = FurnitureGroupMove.expandIDs(pieces, listOf("a")).sorted()
        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun applyGroup_assignsSharedId() {
        val home = HomeFactory.emptyHome("g").copy(
            furniture = listOf(piece("a", 0.0, 0.0), piece("b", 40.0, 0.0), piece("c", 80.0, 0.0)),
        )
        val next = applyGroup(home, listOf("a", "b"))
        assertNotNull(next.furniture[0].groupID)
        assertEquals(next.furniture[0].groupID, next.furniture[1].groupID)
        assertNull(next.furniture[2].groupID)
        assertEquals(sharedGroupID(next, listOf("a", "b")), next.furniture[0].groupID)
        assertTrue(applyGroup(home, listOf("a")) === home)
    }

    @Test
    fun applyUngroup_clearsGroupId() {
        val grouped = applyGroup(
            HomeFactory.emptyHome("g").copy(
                furniture = listOf(piece("a", 0.0, 0.0), piece("b", 10.0, 0.0)),
            ),
            listOf("a", "b"),
        )
        val next = applyUngroup(grouped, listOf("a", "b"))
        assertTrue(next.furniture.all { it.groupID == null })
    }

    @Test
    fun commitFurnitureMove_fansGroupAndSkipsLocked() {
        var home = HomeFactory.emptyHome("g").copy(
            furniture = listOf(
                piece("a", 0.0, 0.0),
                piece("b", 100.0, 0.0),
                piece("c", 200.0, 0.0).copy(movable = false),
            ),
        )
        home = applyGroup(home, listOf("a", "b", "c"))
        val gid = home.furniture[0].groupID
        assertNotNull(gid)
        val next = commitFurnitureMove(home, "a", 10.0, -5.0)
        val by = next.furniture.associateBy { it.id }
        assertEquals(10.0, by.getValue("a").x, 1e-6)
        assertEquals(-5.0, by.getValue("a").y, 1e-6)
        assertEquals(110.0, by.getValue("b").x, 1e-6)
        assertEquals(-5.0, by.getValue("b").y, 1e-6)
        assertEquals(200.0, by.getValue("c").x, 1e-6)
        assertEquals(0.0, by.getValue("c").y, 1e-6)
        assertEquals(gid, by.getValue("a").groupID)
        assertNotEquals(home.furnitureRevision, next.furnitureRevision)
    }

    private fun piece(
        id: String,
        x: Double,
        y: Double,
        group: String? = null,
    ) = HomePieceOfFurniture(
        id = id,
        name = id,
        x = x,
        y = y,
        elevation = 0.0,
        angle = 0.0,
        width = 40.0,
        depth = 40.0,
        height = 40.0,
        groupID = group,
    )
}
