package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Sash
import com.homedesign.android.domain.model.Wall
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class OpeningKind { Door, Window, FrenchDoor }
enum class ResizeSide { Start, End }

data class OpeningTypeDefaults(
    val displayName: String,
    val defaultWidthCM: Double,
    val defaultHeightCM: Double,
    val defaultDepthCM: Double,
    val defaultElevationCM: Double,
    val defaultSashes: List<Sash>,
    val defaultCutoutShape: String,
    val defaultCatalogID: String,
)

const val DEFAULT_CUTOUT_SHAPE = "M0,0 v1 h1 v-1 z"

val DEFAULT_DOOR_SASH = Sash(
    xAxis = 0.9166667,
    yAxis = 0.7579528,
    width = 0.8333333,
    startAngle = PI,
    endAngle = (3 * PI) / 2,
)

private val FRENCH_SASHES = listOf(
    Sash(0.0151, 0.7291, 0.4808, 0.0, -PI / 2),
    Sash(0.9843, 0.7291, 0.4808, PI, (3 * PI) / 2),
)

private val WINDOW_SASHES = listOf(
    Sash(0.0813, 0.56, 0.4187, 0.0, -PI / 2),
    Sash(0.9187, 0.56, 0.4187, PI, (3 * PI) / 2),
)

object OpeningType {
    val door = OpeningTypeDefaults(
        displayName = "Door",
        defaultWidthCM = 80.0,
        defaultHeightCM = 210.0,
        defaultDepthCM = 5.0,
        defaultElevationCM = 0.0,
        defaultSashes = listOf(DEFAULT_DOOR_SASH),
        defaultCutoutShape = DEFAULT_CUTOUT_SHAPE,
        defaultCatalogID = "Scopia#door",
    )
    val window = OpeningTypeDefaults(
        displayName = "Window",
        defaultWidthCM = 100.0,
        defaultHeightCM = 100.0,
        defaultDepthCM = 10.0,
        defaultElevationCM = 90.0,
        defaultSashes = WINDOW_SASHES,
        defaultCutoutShape = DEFAULT_CUTOUT_SHAPE,
        defaultCatalogID = "OlaKristianHoff#window_double_2x3_frame_sill",
    )
    val frenchDoor = OpeningTypeDefaults(
        displayName = "French Door",
        defaultWidthCM = 140.0,
        defaultHeightCM = 210.0,
        defaultDepthCM = 8.0,
        defaultElevationCM = 0.0,
        defaultSashes = FRENCH_SASHES,
        defaultCutoutShape = DEFAULT_CUTOUT_SHAPE,
        defaultCatalogID = "SeberRider#doubleFrenchWindow2",
    )

    fun of(kind: OpeningKind): OpeningTypeDefaults = when (kind) {
        OpeningKind.Door -> door
        OpeningKind.Window -> window
        OpeningKind.FrenchDoor -> frenchDoor
    }
}

private fun wallLength(wall: Wall): Double =
    hypot(wall.endX - wall.startX, wall.endY - wall.startY)

object OpeningMutation {
    fun remove(openingID: String, openings: List<HomeDoorOrWindow>): List<HomeDoorOrWindow> {
        if (openings.none { it.piece.id == openingID }) return openings
        return openings.filter { it.piece.id != openingID }
    }

    fun slide(
        openingID: String,
        newCenterT: Double,
        wall: Wall,
        openings: List<HomeDoorOrWindow>,
    ): List<HomeDoorOrWindow> {
        val idx = openings.indexOfFirst { it.piece.id == openingID }
        if (idx < 0) return openings
        val length = wallLength(wall)
        if (length <= 1e-9) return openings
        val opening = openings[idx]
        val halfWidthT = opening.piece.width / 2.0 / length
        if (halfWidthT >= 0.5) return openings
        val clampedT = max(halfWidthT, min(1.0 - halfWidthT, newCenterT))
        val x = wall.startX + (wall.endX - wall.startX) * clampedT
        val y = wall.startY + (wall.endY - wall.startY) * clampedT
        return openings.toMutableList().also {
            it[idx] = opening.copy(piece = opening.piece.copy(x = x, y = y))
        }
    }

    fun resize(
        openingID: String,
        side: ResizeSide,
        newEdgeT: Double,
        wall: Wall,
        openings: List<HomeDoorOrWindow>,
        minWidthCM: Double = openingMinWidthCM,
    ): List<HomeDoorOrWindow> {
        val idx = openings.indexOfFirst { it.piece.id == openingID }
        if (idx < 0) return openings
        val length = wallLength(wall)
        if (length <= 1e-9) return openings

        val opening = openings[idx]
        val halfWidthT = opening.piece.width / 2.0 / length
        val dx = wall.endX - wall.startX
        val dy = wall.endY - wall.startY
        val ux = dx / length
        val uy = dy / length
        val tCenter =
            ((opening.piece.x - wall.startX) * ux + (opening.piece.y - wall.startY) * uy) / length
        val tFixed = if (side == ResizeSide.Start) tCenter + halfWidthT else tCenter - halfWidthT
        val minWidthT = minWidthCM / length

        val clampedT = if (side == ResizeSide.Start) {
            val maxT = tFixed - minWidthT
            if (maxT < 0) return openings
            max(0.0, min(maxT, newEdgeT))
        } else {
            val minT = tFixed + minWidthT
            if (minT > 1) return openings
            max(minT, min(1.0, newEdgeT))
        }

        val newWidth = abs(tFixed - clampedT) * length
        val newCenterT = (tFixed + clampedT) / 2.0
        val x = wall.startX + dx * newCenterT
        val y = wall.startY + dy * newCenterT
        return openings.toMutableList().also {
            it[idx] = opening.copy(piece = opening.piece.copy(x = x, y = y, width = newWidth))
        }
    }

    fun add(
        type: OpeningKind,
        centerT: Double,
        wall: Wall,
        openings: List<HomeDoorOrWindow>,
        id: String = UUID.randomUUID().toString(),
    ): List<HomeDoorOrWindow> {
        val spec = OpeningType.of(type)
        val length = wallLength(wall)
        if (length <= 1e-9) return openings
        val halfWidthT = spec.defaultWidthCM / 2.0 / length
        if (halfWidthT >= 0.5) return openings
        val clampedT = max(halfWidthT, min(1.0 - halfWidthT, centerT))
        val x = wall.startX + (wall.endX - wall.startX) * clampedT
        val y = wall.startY + (wall.endY - wall.startY) * clampedT
        val wallAngle = atan2(wall.endY - wall.startY, wall.endX - wall.startX)
        val piece = HomePieceOfFurniture(
            id = id,
            catalogID = spec.defaultCatalogID,
            name = spec.displayName,
            x = x,
            y = y,
            elevation = spec.defaultElevationCM,
            angle = wallAngle,
            width = spec.defaultWidthCM,
            depth = spec.defaultDepthCM,
            height = spec.defaultHeightCM,
            movable = true,
            visible = true,
            modelMirrored = false,
            level = wall.level,
        )
        val opening = HomeDoorOrWindow(
            piece = piece,
            wallCutOutOnBothSides = true,
            widthDepthDeformable = true,
            cutoutShape = spec.defaultCutoutShape,
            sashes = spec.defaultSashes.map { it.copy() },
            mirroredX = false,
            mirroredY = false,
            isOpen = false,
        )
        return openings + opening
    }
}

fun projectTOnWall(wall: Wall, x: Double, y: Double): Double {
    val dx = wall.endX - wall.startX
    val dy = wall.endY - wall.startY
    val len2 = dx * dx + dy * dy
    if (len2 <= 1e-9) return 0.0
    return ((x - wall.startX) * dx + (y - wall.startY) * dy) / len2
}
