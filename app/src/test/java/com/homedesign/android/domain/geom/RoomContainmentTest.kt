package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomContainmentTest {

    private fun squareRoom(side: Double = 400.0, id: String = "r1"): Room =
        Room(
            id = id,
            points = listOf(
                Point(0.0, 0.0),
                Point(side, 0.0),
                Point(side, side),
                Point(0.0, side),
            ),
            areaVisible = true,
            floorVisible = true,
            ceilingVisible = true,
            ceilingFlat = false,
            autoDetected = false,
        )

    private fun piece(
        x: Double,
        y: Double,
        w: Double = 100.0,
        d: Double = 60.0,
        angle: Double = 0.0,
    ): HomePieceOfFurniture =
        HomePieceOfFurniture(
            id = "p",
            x = x,
            y = y,
            elevation = 0.0,
            angle = angle,
            width = w,
            depth = d,
            height = 80.0,
            movable = true,
            visible = true,
            modelMirrored = false,
        )

    private fun allCornersInside(p: HomePieceOfFurniture, room: Room): Boolean =
        FurnitureGeometry.cornerPoints(p).all { c ->
            pointInPolygon(room.points.map { vec(it.x, it.y) }, c)
        }

    @Test
    fun centredPieceIsUnchanged() {
        val result = RoomContainment.clampFurnitureToRoom(piece(200.0, 200.0), listOf(squareRoom()))
        assertEquals(200.0, result.x, 1e-9)
        assertEquals(200.0, result.y, 1e-9)
    }

    @Test
    fun pieceOverlappingLeftWallIsPushedInside() {
        val room = squareRoom()
        val result = RoomContainment.clampFurnitureToRoom(piece(20.0, 200.0), listOf(room))
        assertTrue(result.x > 20.0)
        assertTrue(allCornersInside(piece(result.x, result.y), room))
    }

    @Test
    fun pieceOverCornerIsPushedFullyInside() {
        val room = squareRoom()
        val result = RoomContainment.clampFurnitureToRoom(piece(10.0, 10.0), listOf(room))
        assertTrue(allCornersInside(piece(result.x, result.y), room))
        assertTrue(result.x > 10.0)
        assertTrue(result.y > 10.0)
    }

    @Test
    fun rotatedPieceFootprintRespected() {
        val room = squareRoom()
        val result = RoomContainment.clampFurnitureToRoom(
            piece(60.0, 200.0, 160.0, 40.0, PI / 4),
            listOf(room),
        )
        assertTrue(
            allCornersInside(piece(result.x, result.y, 160.0, 40.0, PI / 4), room),
        )
    }

    @Test
    fun noContainingRoomLeavesPieceUnchanged() {
        val result = RoomContainment.clampFurnitureToRoom(
            piece(9000.0, 9000.0),
            listOf(squareRoom()),
        )
        assertEquals(9000.0, result.x, 1e-9)
        assertEquals(9000.0, result.y, 1e-9)
    }

    @Test
    fun emptyRoomsLeavesPieceUnchanged() {
        val result = RoomContainment.clampFurnitureToRoom(piece(50.0, 50.0), emptyList())
        assertEquals(50.0, result.x, 1e-9)
        assertEquals(50.0, result.y, 1e-9)
    }

    @Test
    fun smallestContainingRoomIsChosen() {
        val big = squareRoom(1000.0, "big")
        val small = Room(
            id = "small",
            points = listOf(
                Point(100.0, 100.0),
                Point(300.0, 100.0),
                Point(300.0, 300.0),
                Point(100.0, 300.0),
            ),
            areaVisible = true,
            floorVisible = true,
            ceilingVisible = true,
            ceilingFlat = false,
            autoDetected = false,
        )
        val result = RoomContainment.clampFurnitureToRoom(
            piece(120.0, 200.0, 100.0, 60.0),
            listOf(big, small),
        )
        assertTrue(result.x > 120.0)
        assertTrue(allCornersInside(piece(result.x, result.y), small))
    }

    @Test
    fun wellInsidePieceNotNudgedByMargin() {
        val result = RoomContainment.clampFurnitureToRoom(
            piece(200.0, 200.0, 50.0, 50.0),
            listOf(squareRoom()),
            marginCM = 1.0,
        )
        assertEquals(200.0, result.x, 1e-9)
        assertEquals(200.0, result.y, 1e-9)
    }

    @Test
    fun pointInRoomAndPieceInRoom() {
        val r = squareRoom()
        assertTrue(RoomContainment.pointInRoom(r, vec(200.0, 200.0)))
        assertFalse(RoomContainment.pointInRoom(r, vec(-1.0, 0.0)))
        assertTrue(RoomContainment.pieceInRoom(piece(200.0, 200.0), r))
        assertFalse(RoomContainment.pieceInRoom(piece(9000.0, 9000.0), r))
    }
}
