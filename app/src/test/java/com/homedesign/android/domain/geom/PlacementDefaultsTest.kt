package com.homedesign.android.domain.geom

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementDefaultsTest {

    @Test
    fun mount_routesWallCeilingFloorFromNameKeywords() {
        assertEquals(FurnitureMount.Wall, PlacementDefaults.mount("Sconce"))
        assertEquals(FurnitureMount.Wall, PlacementDefaults.mount("Wall lamp"))
        assertEquals(FurnitureMount.Wall, PlacementDefaults.mount("Rectangular mirror"))
        assertEquals(FurnitureMount.Ceiling, PlacementDefaults.mount("Chandelier"))
        assertEquals(FurnitureMount.Ceiling, PlacementDefaults.mount("Pendant light"))
        assertEquals(FurnitureMount.Floor, PlacementDefaults.mount("Sofa"))
        assertEquals(FurnitureMount.Floor, PlacementDefaults.mount("Dining table"))
        assertEquals(FurnitureMount.Floor, PlacementDefaults.mount(null))
    }

    @Test
    fun defaultElevation_floorIsZero() {
        assertEquals(0.0, PlacementDefaults.defaultElevation("Sofa", 80.0, 250.0), 1e-9)
    }

    @Test
    fun defaultElevation_wallClampsToMin170OrClearance() {
        assertEquals(170.0, PlacementDefaults.wallMountElevationCM, 1e-9)
        assertEquals(170.0, PlacementDefaults.defaultElevation("Sconce", 20.0, 250.0), 1e-9)
        assertEquals(150.0, PlacementDefaults.defaultElevation("Sconce", 50.0, 200.0), 1e-9)
        assertEquals(0.0, PlacementDefaults.defaultElevation("Sconce", 200.0, 180.0), 1e-9)
    }

    @Test
    fun defaultElevation_ceilingAtLevelMinusHeight() {
        assertEquals(220.0, PlacementDefaults.defaultElevation("Chandelier", 30.0, 250.0), 1e-9)
        assertEquals(0.0, PlacementDefaults.defaultElevation("Chandelier", 300.0, 250.0), 1e-9)
    }
}
