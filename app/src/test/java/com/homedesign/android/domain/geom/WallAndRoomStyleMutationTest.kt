package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Baseboard
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Point
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.textures.FLOOR_PRESETS
import com.homedesign.android.domain.textures.paintSwatchSelected
import com.homedesign.android.domain.textures.textureFromPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallAndRoomStyleMutationTest {
    private fun wall(extra: Wall.() -> Wall = { this }) = Wall(
        id = "w",
        startX = 0.0,
        startY = 0.0,
        endX = 200.0,
        endY = 0.0,
        thickness = 12.0,
        height = 250.0,
    ).extra()

    private fun room() = Room(
        id = "r",
        points = listOf(
            Point(0.0, 0.0),
            Point(300.0, 0.0),
            Point(300.0, 200.0),
            Point(0.0, 200.0),
        ),
        name = "Living",
    )

    @Test
    fun sideColorClearsTexture() {
        val textured = wall {
            copy(leftSideTexture = textureFromPreset(FLOOR_PRESETS.first()))
        }
        val next = WallStyleMutation.setLeftSideColor("w", "FFF5F5F5", listOf(textured))[0]
        assertTrue(paintSwatchSelected(next.leftSideColor, "FFF5F5F5"))
        assertNull(next.leftSideTexture)
    }

    @Test
    fun heightClampsAndApplies() {
        val walls = listOf(wall())
        assertEquals(walls, WallStyleMutation.setHeight("w", 10.0, walls))
        val next = WallStyleMutation.setHeight("w", 280.0, walls)[0]
        assertEquals(280.0, next.height, 1e-9)
    }

    @Test
    fun glassUsesShininess() {
        val next = WallStyleMutation.setGlass("w", true, listOf(wall()))[0]
        assertTrue(WallStyleMutation.isGlass(next))
        val cleared = WallStyleMutation.setGlass("w", false, listOf(next))[0]
        assertFalse(WallStyleMutation.isGlass(cleared))
    }

    @Test
    fun hatchPatternToggle() {
        val next = WallStyleMutation.setPattern("w", "hatchUp", listOf(wall()))[0]
        assertTrue(WallStyleMutation.isHatched(next))
        val solid = WallStyleMutation.setPattern("w", null, listOf(next))[0]
        assertFalse(WallStyleMutation.isHatched(solid))
    }

    @Test
    fun floorColorClearsTexture() {
        val withTex = room().copy(floorTexture = textureFromPreset(FLOOR_PRESETS.first()))
        val next = RoomStyleMutation.setFloorColor("r", "FF35455E", listOf(withTex))[0]
        assertEquals("FF35455E", next.floorColor)
        assertNull(next.floorTexture)
    }

    @Test
    fun borderPresets() {
        val rooms = listOf(room())
        val tiled = RoomStyleMutation.setBorder("r", BorderKind.Tile, rooms)[0]
        assertEquals(BorderKind.Tile, RoomStyleMutation.borderKindOf(tiled))
        val none = RoomStyleMutation.setBorder("r", BorderKind.None, listOf(tiled))[0]
        assertEquals(BorderKind.None, RoomStyleMutation.borderKindOf(none))
        assertNull(none.borderWidthCM)
    }

    @Test
    fun emptyHomeFactoryStillBuilds() {
        assertEquals("Untitled", HomeFactory.emptyHome("Untitled").name)
    }

    @Test
    fun baseboardEnableAndClear() {
        val walls = listOf(wall())
        val board = Baseboard(thickness = 1.6, height = 7.0, color = "FFF4F1EA")
        val on = WallStyleMutation.setLeftSideBaseboard("w", board, walls)[0]
        assertEquals(7.0, on.leftSideBaseboard?.height)
        val off = WallStyleMutation.setLeftSideBaseboard("w", null, listOf(on))[0]
        assertNull(off.leftSideBaseboard)
    }

    @Test
    fun matchAttributesCopiesBaseboardAndPaint() {
        val source = wall {
            copy(
                leftSideColor = "FF35455E",
                leftSideBaseboard = Baseboard(thickness = 2.0, height = 90.0, color = "FFF4F1EA"),
                height = 280.0,
            )
        }
        val target = Wall(
            id = "t",
            startX = 0.0,
            startY = 50.0,
            endX = 100.0,
            endY = 50.0,
            thickness = 12.0,
            height = 250.0,
        )
        val next = WallStyleMutation.matchAttributes("w", listOf("t"), listOf(source, target))
        val painted = next.find { it.id == "t" }!!
        assertEquals("FF35455E", painted.leftSideColor)
        assertEquals(90.0, painted.leftSideBaseboard?.height)
        assertEquals(280.0, painted.height, 1e-9)
    }
}
