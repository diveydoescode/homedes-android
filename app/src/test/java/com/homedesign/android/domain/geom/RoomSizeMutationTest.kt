package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class RoomSizeMutationTest {
    private fun rectRoom(): Room = Room(
        id = "r1",
        points = listOf(
            Point(0.0, 0.0),
            Point(400.0, 0.0),
            Point(400.0, 300.0),
            Point(0.0, 300.0),
        ),
        areaVisible = true,
        floorVisible = true,
        ceilingVisible = true,
        autoDetected = true,
    )

    private fun walls(): List<Wall> = listOf(
        Wall("top", 0.0, 0.0, 400.0, 0.0, 10.0, 250.0),
        Wall("right", 400.0, 0.0, 400.0, 300.0, 10.0, 250.0),
        Wall("bot", 400.0, 300.0, 0.0, 300.0, 10.0, 250.0),
        Wall("left", 0.0, 300.0, 0.0, 0.0, 10.0, 250.0),
    )

    private fun door(x: Double, y: Double): HomeDoorOrWindow = HomeDoorOrWindow(
        piece = HomePieceOfFurniture(
            id = "d1",
            name = "Door",
            x = x,
            y = y,
            elevation = 0.0,
            angle = PI / 2,
            width = 80.0,
            depth = 10.0,
            height = 210.0,
            movable = true,
            visible = true,
            modelMirrored = false,
        ),
    )

    @Test
    fun growsFarHalfSpace() {
        val next = RoomSizeMutation.resize("r1", 500.0, 360.0, listOf(rectRoom()), walls(), emptyList())
        assertEquals(RoomBoundingSize(500.0, 360.0), RoomSizeMutation.boundingSize(next.rooms[0]))
        val right = next.walls.find { it.id == "right" }!!
        assertEquals(500.0, right.startX, 1e-9)
        assertEquals(500.0, right.endX, 1e-9)
        val left = next.walls.find { it.id == "left" }!!
        assertEquals(0.0, left.startX, 1e-9)
        assertEquals(0.0, left.endX, 1e-9)
    }

    @Test
    fun translatesOpeningOnFarWall() {
        val next = RoomSizeMutation.resize(
            "r1", 500.0, null, listOf(rectRoom()), walls(), listOf(door(400.0, 150.0)),
        )
        assertEquals(500.0, next.openings[0].piece.x, 1e-9)
        assertEquals(150.0, next.openings[0].piece.y, 1e-9)
    }

    @Test
    fun leavesOpeningOnStretchedWall() {
        val next = RoomSizeMutation.resize(
            "r1", 500.0, null, listOf(rectRoom()), walls(), listOf(door(200.0, 0.0)),
        )
        assertEquals(200.0, next.openings[0].piece.x, 1e-9)
        assertEquals(0.0, next.openings[0].piece.y, 1e-9)
    }

    @Test
    fun refusesTargetUnder20cm() {
        val before = walls()
        val next = RoomSizeMutation.resize("r1", 10.0, null, listOf(rectRoom()), before, emptyList())
        assertEquals(before[0].endX, next.walls[0].endX, 1e-9)
    }
}
