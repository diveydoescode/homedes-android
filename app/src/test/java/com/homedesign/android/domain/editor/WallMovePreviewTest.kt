package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.WallMutation
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Test

class WallMovePreviewTest {
    @Test
    fun moveWallEndpoint_movesJoinedCoincidentEnds() {
        val a = wall("a", 0.0, 0.0, 100.0, 0.0)
        val b = wall("b", 100.0, 0.0, 200.0, 0.0)
        val moved = WallMutation.moveWallEndpoint(
            listOf(a, b),
            "a",
            atStart = false,
            newPosition = vec(100.0, 50.0),
            joinedCorner = true,
        )
        assertEquals(100.0, moved.first { it.id == "a" }.endX, 1e-9)
        assertEquals(50.0, moved.first { it.id == "a" }.endY, 1e-9)
        assertEquals(100.0, moved.first { it.id == "b" }.startX, 1e-9)
        assertEquals(50.0, moved.first { it.id == "b" }.startY, 1e-9)
    }

    @Test
    fun previewWallMove_translatesBothEndsAndJoinedMate() {
        val a = wall("a", 0.0, 0.0, 100.0, 0.0)
        val b = wall("b", 100.0, 0.0, 100.0, 80.0)
        val home = HomeFactory.emptyHome("m").copy(walls = listOf(a, b))
        val (walls, _) = previewWallMove(home, "a", vec(0.0, 25.0))
        assertEquals(0.0, walls.first { it.id == "a" }.startX, 1e-9)
        assertEquals(25.0, walls.first { it.id == "a" }.startY, 1e-9)
        assertEquals(100.0, walls.first { it.id == "a" }.endX, 1e-9)
        assertEquals(25.0, walls.first { it.id == "a" }.endY, 1e-9)
        // Joined corner at (100,0) follows via end move.
        assertEquals(100.0, walls.first { it.id == "b" }.startX, 1e-9)
        assertEquals(25.0, walls.first { it.id == "b" }.startY, 1e-9)
    }

    @Test
    fun previewEndpointMove_updatesOnlyTargetCorner() {
        val a = wall("a", 0.0, 0.0, 100.0, 0.0)
        val home = HomeFactory.emptyHome("e").copy(walls = listOf(a))
        val (walls, _) = previewEndpointMove(home, "a", atStart = true, newPosition = vec(10.0, 5.0))
        assertEquals(10.0, walls.first().startX, 1e-9)
        assertEquals(5.0, walls.first().startY, 1e-9)
        assertEquals(100.0, walls.first().endX, 1e-9)
        assertEquals(0.0, walls.first().endY, 1e-9)
    }

    private fun wall(
        id: String,
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
    ) = Wall(
        id = id,
        startX = sx,
        startY = sy,
        endX = ex,
        endY = ey,
        thickness = 10.0,
        height = 250.0,
        level = HomeFactory.SYNTH_LEVEL_ID,
    )
}
