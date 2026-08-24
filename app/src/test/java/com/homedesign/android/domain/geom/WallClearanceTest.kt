package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class WallClearanceTest {
    private fun piece(
        id: String,
        x: Double,
        y: Double,
        opts: HomePieceOfFurniture.() -> HomePieceOfFurniture = { this },
    ): HomePieceOfFurniture {
        val base = HomePieceOfFurniture(
            id = id,
            x = x,
            y = y,
            elevation = 0.0,
            angle = 0.0,
            width = 180.0,
            depth = 200.0,
            height = 50.0,
            movable = true,
            visible = true,
            modelMirrored = false,
        )
        return base.opts()
    }

    private val wall = Wall(
        id = "w",
        startX = 0.0,
        startY = 0.0,
        endX = 1000.0,
        endY = 0.0,
        thickness = 12.0,
        height = 250.0,
    )

    @Test
    fun movesBedAtCentrelineToFace() {
        val result = WallClearance.resolve(listOf(piece("bed", 500.0, 100.0)), listOf(wall))
        assertEquals(1, result.moves.size)
        assertEquals(106.0, result.furniture[0].y, 1e-9)
        assertEquals(500.0, result.furniture[0].x, 1e-9)
    }

    @Test
    fun healsOtherSideToOtherFace() {
        val result = WallClearance.resolve(listOf(piece("desk", 500.0, -100.0)), listOf(wall))
        assertEquals(1, result.moves.size)
        assertEquals(-106.0, result.furniture[0].y, 1e-9)
    }

    @Test
    fun leavesDeepOverlapAlone() {
        val result = WallClearance.resolve(listOf(piece("wardrobe", 500.0, 80.0)), listOf(wall))
        assertTrue(result.moves.isEmpty())
        assertEquals(80.0, result.furniture[0].y, 1e-9)
    }

    @Test
    fun leavesClearPieceUntouched() {
        val result = WallClearance.resolve(listOf(piece("sofa", 500.0, 400.0)), listOf(wall))
        assertTrue(result.moves.isEmpty())
    }

    @Test
    fun leavesPieceAlreadyOnFace() {
        val result = WallClearance.resolve(listOf(piece("bed", 500.0, 106.0)), listOf(wall))
        assertTrue(result.moves.isEmpty())
    }

    @Test
    fun ignoresBeyondWallRun() {
        val result = WallClearance.resolve(listOf(piece("bed", 1400.0, 100.0)), listOf(wall))
        assertTrue(result.moves.isEmpty())
    }

    @Test
    fun skipsWallMountedAndStructure() {
        val result = WallClearance.resolve(
            listOf(
                piece("mirror", 500.0, 2.0) {
                    copy(depth = 4.0, elevation = 120.0, name = "Mirror")
                },
                piece("col", 500.0, 0.0) {
                    copy(width = 30.0, depth = 30.0, catalogID = "structure#column")
                },
            ),
            listOf(wall),
        )
        assertTrue(result.moves.isEmpty())
    }

    @Test
    fun usesRotatedFootprint() {
        val result = WallClearance.resolve(
            listOf(piece("bed", 500.0, 85.0) { copy(angle = PI / 2) }),
            listOf(wall),
        )
        assertEquals(1, result.moves.size)
        assertEquals(96.0, result.furniture[0].y, 1e-9)
    }

    @Test
    fun healsVerticalWallAlongX() {
        val vwall = Wall("v", 0.0, 0.0, 0.0, 1000.0, 12.0, 250.0)
        val result = WallClearance.resolve(listOf(piece("bed", 90.0, 500.0)), listOf(vwall))
        assertEquals(1, result.moves.size)
        assertEquals(96.0, result.furniture[0].x, 1e-9)
    }

    @Test
    fun ignoresWallOnOtherLevel() {
        val upstairs = wall.copy(id = "u", level = "level1")
        val result = WallClearance.resolve(listOf(piece("bed", 500.0, 100.0)), listOf(upstairs))
        assertTrue(result.moves.isEmpty())
    }
}
