package com.homedesign.android.domain.project

import com.homedesign.android.domain.geom.RoomDetection
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Level
import com.homedesign.android.domain.model.Outdoor
import com.homedesign.android.domain.model.Sash
import com.homedesign.android.domain.model.Wall
import kotlin.math.PI

/**
 * Compact showcase plan (iOS Villa Bianca spirit) — walls, rooms, openings, a few pieces.
 * Used by the dashboard Showcase entry.
 */
object ShowcaseVilla {
    const val NAME = "Villa Bianca"

    fun make(): Home {
        val level = Level(
            id = "ground",
            name = "Ground floor",
            elevation = 0.0,
            floorThickness = 12.0,
            height = 290.0,
            elevationIndex = 0,
            visible = true,
            viewable = true,
        )
        val tExt = 24.0
        val tInt = 10.0
        fun wall(
            id: String,
            sx: Double,
            sy: Double,
            ex: Double,
            ey: Double,
            thickness: Double,
            arc: Double? = null,
        ): Wall = Wall(
            id = id,
            startX = sx,
            startY = sy,
            endX = ex,
            endY = ey,
            thickness = thickness,
            height = 290.0,
            level = level.id,
            arcExtent = arc,
            pattern = "hatchUp",
            leftSideColor = "FFF7F5F1",
            rightSideColor = "FFF7F5F1",
            topColor = "FFF2F0EC",
        )

        val walls = mutableListOf(
            wall("ext-n", 0.0, 0.0, 1400.0, 0.0, tExt),
            wall("ext-e", 1400.0, 0.0, 1400.0, 1000.0, tExt),
            wall("ext-s", 1400.0, 1000.0, 0.0, 1000.0, tExt, arc = -18 * PI / 180),
            wall("ext-w", 0.0, 1000.0, 0.0, 0.0, tExt),
            wall("cor-n", 0.0, 420.0, 1400.0, 420.0, tInt),
            wall("cor-s", 0.0, 580.0, 1400.0, 580.0, tInt),
            wall("nb-1", 360.0, 0.0, 360.0, 420.0, tInt),
            wall("nb-2", 620.0, 0.0, 620.0, 420.0, tInt),
            wall("nb-3", 880.0, 0.0, 880.0, 420.0, tInt),
            wall("glass-k", 420.0, 580.0, 420.0, 1000.0, 8.0).copy(
                leftSideColor = "4DD9EAF0",
                rightSideColor = "4DD9EAF0",
                topColor = "4DD9EAF0",
            ),
        )

        var home = Home(
            name = NAME,
            wallHeight = 290.0,
            selectedLevelID = level.id,
            levels = listOf(level),
            walls = walls,
            outdoor = Outdoor(grass = true, fence = true, marginCM = 500.0),
        )
        val rooms = RoomDetection.reconcileRooms(walls = home.walls, existing = emptyList(), level = level.id)
        home = home.copy(rooms = nameRooms(rooms))
        home = home.copy(
            doorsAndWindows = openings(level.id),
            furniture = furniture(level.id),
        )
        return home
    }

    private fun nameRooms(rooms: List<com.homedesign.android.domain.model.Room>): List<com.homedesign.android.domain.model.Room> {
        // Rough naming by centroid X within the north / south bands.
        return rooms.map { room ->
            if (room.points.isEmpty()) return@map room
            val cx = room.points.map { it.x }.average()
            val cy = room.points.map { it.y }.average()
            val label = when {
                cy < 420 -> when {
                    cx < 360 -> "Bedroom 2"
                    cx < 620 -> "Study"
                    cx < 880 -> "Bath"
                    else -> "Master bedroom"
                }
                cy < 580 -> "Corridor"
                cx < 420 -> "Kitchen"
                else -> "Living"
            }
            room.copy(name = label, autoDetected = true)
        }
    }

    private fun openings(levelId: String): List<HomeDoorOrWindow> {
        fun door(id: String, x: Double, y: Double, angle: Double, width: Double = 91.0) =
            HomeDoorOrWindow(
                piece = HomePieceOfFurniture(
                    id = id,
                    catalogID = "Scopia#door",
                    name = "Door",
                    x = x,
                    y = y,
                    angle = angle,
                    width = width,
                    depth = 17.5,
                    height = 210.0,
                    movable = false,
                    level = levelId,
                ),
                wallThickness = 0.8,
                wallCutOutOnBothSides = true,
                cutoutShape = "M0,0 v1 h1 v-1 z",
                sashes = listOf(
                    Sash(xAxis = 0.05, yAxis = 0.8, width = 0.9, startAngle = 0.0, endAngle = -PI / 2),
                ),
            )
        fun window(id: String, x: Double, y: Double, angle: Double, width: Double = 123.0) =
            HomeDoorOrWindow(
                piece = HomePieceOfFurniture(
                    id = id,
                    catalogID = "OlaKristianHoff#window_double_2x3_frame_sill",
                    name = "Window",
                    x = x,
                    y = y,
                    elevation = 90.0,
                    angle = angle,
                    width = width,
                    depth = 25.0,
                    height = 110.0,
                    movable = false,
                    level = levelId,
                ),
                wallThickness = 0.7,
                wallCutOutOnBothSides = true,
                cutoutShape = "M0,0 v1 h1 v-1 z",
            )
        return listOf(
            door("door-entry", 0.0, 500.0, PI / 2, 100.0),
            door("door-master", 880.0, 210.0, 0.0),
            window("win-living", 1100.0, 1000.0, PI, 180.0),
            window("win-bed2", 180.0, 0.0, 0.0, 120.0),
        )
    }

    private fun furniture(levelId: String): List<HomePieceOfFurniture> = listOf(
        HomePieceOfFurniture(
            id = "sofa-1",
            catalogID = "Blend Swap CC-0#sofa2",
            name = "Sofa",
            x = 900.0,
            y = 780.0,
            angle = PI,
            width = 166.9,
            depth = 74.7,
            height = 84.8,
            level = levelId,
        ),
        HomePieceOfFurniture(
            id = "table-1",
            catalogID = "Blend Swap CC-0#table",
            name = "Dining table",
            x = 700.0,
            y = 780.0,
            width = 180.0,
            depth = 90.0,
            height = 73.9,
            level = levelId,
        ),
        HomePieceOfFurniture(
            id = "bed-master",
            catalogID = "Blend Swap CC-0#bed1",
            name = "Bed",
            x = 1140.0,
            y = 210.0,
            angle = PI / 2,
            width = 140.7,
            depth = 208.0,
            height = 95.5,
            level = levelId,
        ),
        HomePieceOfFurniture(
            id = "plant-1",
            catalogID = "Blend Swap CC-0#decorativePlant",
            name = "Plant",
            x = 500.0,
            y = 700.0,
            width = 55.9,
            depth = 60.2,
            height = 103.1,
            level = levelId,
        ),
    )
}
