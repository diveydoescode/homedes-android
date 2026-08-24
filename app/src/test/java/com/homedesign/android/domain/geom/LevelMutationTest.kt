package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelMutationTest {
    @Test
    fun newFloorStacksOnTheHighestStorey() {
        val ground = Level(
            id = "g",
            name = "Ground floor",
            elevation = 0.0,
            floorThickness = HomeFactory.DEFAULT_FLOOR_THICKNESS_CM,
            height = 250.0,
            elevationIndex = 0,
            visible = true,
            viewable = true,
        )
        val levels = LevelMutation.addLevelOnTop(
            levels = listOf(ground),
            defaultHeight = 250.0,
            id = "f1",
        )
        assertEquals(2, levels.size)
        val added = levels[1]
        assertEquals(250.0 + HomeFactory.DEFAULT_FLOOR_THICKNESS_CM, added.elevation, 1e-9)
        assertEquals(250.0, added.height, 1e-9)
        assertEquals(1, added.elevationIndex)
        assertEquals("Floor 2", added.name)
        assertTrue(added.visible && added.viewable)
    }

    @Test
    fun stackingFollowsTheTallestLevelNotTheLast() {
        val a = Level(
            id = "a",
            elevation = 0.0,
            floorThickness = 12.0,
            height = 250.0,
            elevationIndex = 0,
            visible = true,
            viewable = true,
        )
        val b = Level(
            id = "b",
            elevation = 600.0,
            floorThickness = 12.0,
            height = 300.0,
            elevationIndex = 1,
            visible = true,
            viewable = true,
        )
        val levels = LevelMutation.addLevelOnTop(
            levels = listOf(b, a),
            defaultHeight = 250.0,
            id = "c",
        )
        assertEquals(900.0 + HomeFactory.DEFAULT_FLOOR_THICKNESS_CM, levels.last().elevation, 1e-9)
        assertEquals(2, levels.last().elevationIndex)
    }

    @Test
    fun emptyHomeGetsAGroundFloor() {
        val levels = LevelMutation.addLevelOnTop(
            levels = emptyList(),
            defaultHeight = 250.0,
            id = "g",
        )
        assertEquals(1, levels.size)
        assertEquals(0.0, levels[0].elevation, 1e-9)
        assertEquals("Floor 1", levels[0].name)
    }

    @Test
    fun elevatorLabel_groundBasementAttic() {
        fun level(name: String?, index: Int = 0) = Level(
            id = "x",
            name = name,
            elevation = 0.0,
            floorThickness = 12.0,
            height = 250.0,
            elevationIndex = index,
            visible = true,
            viewable = true,
        )
        assertEquals("G", LevelMutation.elevatorLabel(level("Ground floor")))
        assertEquals("B", LevelMutation.elevatorLabel(level("Basement")))
        assertEquals("A", LevelMutation.elevatorLabel(level("Attic")))
        assertEquals("2", LevelMutation.elevatorLabel(level(null, index = 1)))
    }

    @Test
    fun orderedVisible_highestFirst() {
        val levels = listOf(
            Level("L0", "Ground", 0.0, 12.0, 250.0, 0, true, true),
            Level("L1", "1st", 262.0, 12.0, 250.0, 1, true, true),
            Level("L2", "2nd", 524.0, 12.0, 250.0, 2, false, true),
        )
        val ordered = LevelMutation.orderedVisible(levels)
        assertEquals(listOf("L1", "L0"), ordered.map { it.id })
    }
}
