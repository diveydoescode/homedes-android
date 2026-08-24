package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WallJoinInferenceTest {
    @Test
    fun infer_joinsTwoFreeEnds_caseA() {
        val a = wall("a", 0.0, 0.0, 100.0, 0.0)
        val b = wall("b", 100.0, 0.0, 200.0, 0.0)
        val result = WallJoinInference.infer(listOf(a, b))
        assertEquals(1, result.joints)
        assertEquals("b", result.walls.find { it.id == "a" }?.atEnd)
        assertEquals("a", result.walls.find { it.id == "b" }?.atStart)
    }

    @Test
    fun infer_doesNotJoinThreeEnds_caseB() {
        val a = wall("a", 0.0, 0.0, 100.0, 0.0)
        val b = wall("b", 100.0, 0.0, 200.0, 0.0)
        val c = wall("c", 100.0, 0.0, 100.0, 100.0)
        val result = WallJoinInference.infer(listOf(a, b, c))
        assertEquals(0, result.joints)
        assertNull(result.walls.find { it.id == "a" }?.atEnd)
    }

    @Test
    fun openingHole_hasFourPoints_twice() {
        val wall = wall("w", 0.0, 0.0, 200.0, 0.0, thickness = 20.0)
        val hole = openingHoleFromBinding(wall, 0.25, 0.5)
        assertEquals(4, hole.size)
        val hole2 = openingHoleFromBinding(wall, 0.0, 0.1)
        assertEquals(4, hole2.size)
        assertNotNull(hole.firstOrNull())
    }

    private fun wall(
        id: String,
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
        thickness: Double = 10.0,
    ) = Wall(
        id = id,
        startX = sx,
        startY = sy,
        endX = ex,
        endY = ey,
        thickness = thickness,
        height = 250.0,
        level = "L0",
    )
}
