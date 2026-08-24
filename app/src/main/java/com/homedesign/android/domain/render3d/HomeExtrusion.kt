package com.homedesign.android.domain.render3d

import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.OpeningSymbol
import com.homedesign.android.domain.geom.OpeningSymbolKind
import com.homedesign.android.domain.geom.OpeningSwing
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.geom.WallSegmentation
import com.homedesign.android.domain.geom.WallSegment
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallTexture
import com.homedesign.android.domain.textures.findPreset
import com.homedesign.android.domain.textures.presetAssetPath
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Extrude a 2D Home (cm, Y-down) into simple 3D mesh data (metres, Y-up).
 * Plan (x,y) → world (x, 0, y) with Y flipped so +Z is "down the plan".
 */
data class MeshTri(
    val positions: FloatArray, // xyz xyz …
    val normals: FloatArray,
    val colorArgb: Int,
    /** UV-free Filament roughness (0..1). */
    val roughness: Float = 0.72f,
    /** Optional UV0 (u v per vertex); used with [textureAssetPath]. */
    val uvs: FloatArray? = null,
    /**
     * Texture key for Filament: assets-relative JPG
     * (`textures/floors/parquet-brown.jpg`) or absolute file via `file:` prefix
     * (OBJ MTL `map_Kd`, user imports).
     */
    val textureAssetPath: String? = null,
)

/** Operable door leaf in local hinge space; Filament applies yaw about the hinge. */
data class DoorLeaf3D(
    val localMesh: MeshTri,
    /** Hinge pivot in metres (world). */
    val hingeX: Float,
    val hingeY: Float,
    val hingeZ: Float,
    /** Closed-door yaw (radians) matching [orientedBox] plan angle. */
    val closedYawRad: Float,
    /** Signed max swing from closed (radians). */
    val swingRad: Float,
)

data class ExtrudeOptions(
    val roofs: Boolean = false,
    val fence: Boolean = false,
)

/** Infinite wall slab in XZ (metres) for walk collision. */
data class WalkWallSeg(
    val ax: Float,
    val az: Float,
    val bx: Float,
    val bz: Float,
    val halfThick: Float,
)

/** Doorway disc that cancels wall collision (metres). */
data class WalkDoorPass(
    val cx: Float,
    val cz: Float,
    val radius: Float,
)

data class HomeScene3D(
    val meshes: List<MeshTri>,
    val doorLeaves: List<DoorLeaf3D> = emptyList(),
    val centerX: Float,
    val centerZ: Float,
    val radius: Float,
    /** Plan bounds in metres (for minimap / fence). */
    val minX: Float = centerX - radius,
    val maxX: Float = centerX + radius,
    val minZ: Float = centerZ - radius,
    val maxZ: Float = centerZ + radius,
    val walkWalls: List<WalkWallSeg> = emptyList(),
    val walkDoors: List<WalkDoorPass> = emptyList(),
)

object HomeExtrusion {
    private const val CM_TO_M = 0.01f
    private const val ROOF_RISE_FRAC = 0.28
    private const val ROOF_RISE_MIN_CM = 40.0
    private const val ROOF_RISE_MAX_CM = 160.0
    private const val FENCE_PAD_CM = 90.0
    private const val FENCE_POST_SPACING_CM = 140.0
    private const val FENCE_POST_H_CM = 120.0
    private const val FENCE_POST_W_CM = 8.0
    private const val FENCE_RAIL_T_CM = 4.0

    private const val ROUGH_FLOOR = 0.78f
    private const val ROUGH_WALL = 0.68f
    private const val ROUGH_ROOF = 0.88f
    private const val ROUGH_FURNITURE = 0.55f
    private const val ROUGH_DOOR = 0.48f
    private const val ROUGH_FENCE = 0.82f
    private const val ROUGH_GRASS = 0.95f

    private val DEFAULT_WALL = 0xFF3A3835.toInt()
    private val DEFAULT_ROOF = 0xFF8B4518.toInt()
    private val DEFAULT_FLOOR = 0xFFE8DFD2.toInt()
    private val DEFAULT_FURNITURE = 0xFFB85C3C.toInt()

    fun build(
        home: Home,
        level: String? = home.selectedLevelID,
        options: ExtrudeOptions = ExtrudeOptions(),
    ): HomeScene3D {
        val walls = home.walls.filter { level == null || it.level == level }
        val rooms = home.rooms.filter { level == null || it.level == level }
        val openings = home.doorsAndWindows.filter { level == null || it.piece.level == level }
        val wallsById = walls.associateBy { it.id }
        val cutoutsByWall = WallSegmentation.cutoutsByWallID(walls, openings)
        val meshes = mutableListOf<MeshTri>()

        for (room in rooms) {
            if (room.points.size < 3) continue
            val color = parseColor(room.floorColor) ?: DEFAULT_FLOOR
            val tex = resolveTexture(room.floorTexture)
            meshes.add(
                floorMesh(
                    pts = room.points.map { it.x to it.y },
                    colorArgb = color,
                    textureAssetPath = tex?.first,
                    tileWidthCM = tex?.second ?: 100.0,
                    tileHeightCM = tex?.third ?: 100.0,
                ),
            )
        }

        var maxWallH = home.wallHeight.coerceAtLeast(1.0)
        for (wall in walls) {
            val h = wall.height.coerceAtLeast(1.0)
            if (h > maxWallH) maxWallH = h
            val color = parseColor(wall.leftSideColor)
                ?: parseColor(wall.rightSideColor)
                ?: DEFAULT_WALL
            val leftTex = resolveTexture(wall.leftSideTexture, preferWall = true)
            val rightTex = resolveTexture(wall.rightSideTexture, preferWall = true)
            val cutouts = cutoutsByWall[wall.id].orEmpty()
            val curved = ArcWallGeometry.isCurved(wall)
            if (!curved && cutouts.isNotEmpty()) {
                for (seg in WallSegmentation.wallSegments(wall, cutouts)) {
                    meshes.addAll(segmentMeshes(seg, color, leftTex, rightTex))
                }
            } else {
                val outline = if (curved) {
                    ArcWallGeometry.footprint(wall)
                } else {
                    WallGeometry.miteredPoints(wall, wallsById)
                }
                if (outline.size >= 3) {
                    meshes.addAll(
                        wallPrismMeshes(
                            footprint = outline.map { it.x to it.y },
                            y0 = 0.0,
                            y1 = h,
                            colorArgb = color,
                            leftTex = leftTex,
                            rightTex = rightTex,
                            fourPointSides = outline.size == 4 && !curved,
                        ),
                    )
                } else {
                    meshes.addAll(
                        wallPrismMeshes(
                            footprint = WallGeometry.unjoinedOutline(wall).map { it.x to it.y },
                            y0 = 0.0,
                            y1 = h,
                            colorArgb = color,
                            leftTex = leftTex,
                            rightTex = rightTex,
                            fourPointSides = true,
                        ),
                    )
                }
            }
        }

        val furniture = home.furniture.filter { it.visible && (level == null || it.level == level) }
        for (piece in furniture) {
            meshes.addAll(furnitureMeshes(piece))
        }

        if (options.roofs) {
            for (room in rooms) {
                if (room.points.size < 3) continue
                // Prefer a roof-leaning tint even when ceilingColor is set (ceilings are often light).
                val ceiling = parseColor(room.ceilingColor)
                val color = when {
                    ceiling == null -> DEFAULT_ROOF
                    isLightColor(ceiling) -> DEFAULT_ROOF
                    else -> warmRoof(ceiling)
                }
                meshes.add(
                    hipRoof(
                        pts = room.points.map { it.x to it.y },
                        eavesY = maxWallH,
                        colorArgb = color,
                    ),
                )
            }
        }

        val bounds = planBoundsCm(walls, rooms)
        if (options.fence && bounds != null) {
            meshes.addAll(
                fenceMeshes(
                    minX = bounds[0] - FENCE_PAD_CM,
                    minZ = bounds[1] - FENCE_PAD_CM,
                    maxX = bounds[2] + FENCE_PAD_CM,
                    maxZ = bounds[3] + FENCE_PAD_CM,
                ),
            )
        }

        val doorLeaves = buildDoorLeaves(home, level)
        val walkWalls = walls.map { w ->
            WalkWallSeg(
                ax = (w.startX * CM_TO_M).toFloat(),
                az = (w.startY * CM_TO_M).toFloat(),
                bx = (w.endX * CM_TO_M).toFloat(),
                bz = (w.endY * CM_TO_M).toFloat(),
                halfThick = ((w.thickness * 0.5) * CM_TO_M).toFloat(),
            )
        }
        // Tall openings act as doorways (pass-through discs), matching iOS Lab collision.
        val walkDoors = openings
            .filter { it.piece.height > 150.0 }
            .map { dw ->
                val p = dw.piece
                WalkDoorPass(
                    cx = (p.x * CM_TO_M).toFloat(),
                    cz = (p.y * CM_TO_M).toFloat(),
                    radius = (((p.width * 0.5) + 15.0) * CM_TO_M).toFloat(),
                )
            }

        return finalizeScene(meshes, doorLeaves, walkWalls, walkDoors)
    }

    /** Solo furniture mesh for AR place-one-piece mode. */
    fun buildFurnitureOnly(home: Home, furnitureId: String): HomeScene3D {
        val piece = home.furniture.find { it.id == furnitureId }
            ?: home.doorsAndWindows.find { it.piece.id == furnitureId }?.piece
        if (piece == null) {
            return HomeScene3D(
                meshes = emptyList(),
                centerX = 0f,
                centerZ = 0f,
                radius = 1f,
            )
        }
        return finalizeScene(furnitureMeshes(piece), emptyList(), emptyList(), emptyList())
    }

    /** Flat grass disc under the home (metres, Y-up). */
    fun groundDisc(
        centerX: Float,
        centerZ: Float,
        radiusM: Float,
        colorArgb: Int = 0xFF5B8C4A.toInt(),
        segments: Int = 48,
    ): MeshTri {
        val y = -0.02f
        val pos = FloatArray(segments * 9)
        val nrm = FloatArray(segments * 9)
        for (i in 0 until segments) {
            val a0 = i * 2.0 * Math.PI / segments
            val a1 = (i + 1) * 2.0 * Math.PI / segments
            val base = i * 9
            pos[base] = centerX
            pos[base + 1] = y
            pos[base + 2] = centerZ
            pos[base + 3] = centerX + (cos(a0) * radiusM).toFloat()
            pos[base + 4] = y
            pos[base + 5] = centerZ + (sin(a0) * radiusM).toFloat()
            pos[base + 6] = centerX + (cos(a1) * radiusM).toFloat()
            pos[base + 7] = y
            pos[base + 8] = centerZ + (sin(a1) * radiusM).toFloat()
            for (k in 0..2) {
                nrm[base + k * 3] = 0f
                nrm[base + k * 3 + 1] = 1f
                nrm[base + k * 3 + 2] = 0f
            }
        }
        return MeshTri(pos, nrm, colorArgb, ROUGH_GRASS)
    }

    private fun finalizeScene(
        meshes: List<MeshTri>,
        doorLeaves: List<DoorLeaf3D>,
        walkWalls: List<WalkWallSeg>,
        walkDoors: List<WalkDoorPass>,
    ): HomeScene3D {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        fun absorb(m: MeshTri) {
            var i = 0
            while (i < m.positions.size) {
                val x = m.positions[i]
                val z = m.positions[i + 2]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
                i += 3
            }
        }
        for (m in meshes) absorb(m)
        for (leaf in doorLeaves) {
            minX = min(minX, leaf.hingeX - 1.5f)
            maxX = max(maxX, leaf.hingeX + 1.5f)
            minZ = min(minZ, leaf.hingeZ - 1.5f)
            maxZ = max(maxZ, leaf.hingeZ + 1.5f)
        }
        if (!minX.isFinite()) {
            minX = -5f; maxX = 5f; minZ = -5f; maxZ = 5f
        }
        val cx = (minX + maxX) / 2f
        val cz = (minZ + maxZ) / 2f
        val radius = hypot((maxX - minX).toDouble(), (maxZ - minZ).toDouble()).toFloat().coerceAtLeast(2f) / 2f
        return HomeScene3D(
            meshes = meshes,
            doorLeaves = doorLeaves,
            centerX = cx,
            centerZ = cz,
            radius = radius,
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
            walkWalls = walkWalls,
            walkDoors = walkDoors,
        )
    }

    private enum class FurnitureKind {
        Bed, Sofa, Table, Chair, Toilet, Bathtub, Wardrobe, Plant, Box,
    }

    /** Terracotta-ish accents per kind (ARGB). */
    private object FurnColor {
        val bed = 0xFFC4785A.toInt()
        val bedHead = 0xFFA65F45.toInt()
        val sofa = 0xFFB85C3C.toInt()
        val sofaBack = 0xFF9E4A32.toInt()
        val table = 0xFFB8734A.toInt()
        val tableLeg = 0xFF8F5538.toInt()
        val chair = 0xFFC68A5C.toInt()
        val chairBack = 0xFFA86F48.toInt()
        val toilet = 0xFFD4A090.toInt()
        val toiletTank = 0xFFC48878.toInt()
        val bath = 0xFFC9A090.toInt()
        val bathInner = 0xFFB88878.toInt()
        val wardrobe = 0xFFA65C42.toInt()
        val wardrobeDoor = 0xFF8E4A34.toInt()
        val plantPot = 0xFFB06A4A.toInt()
        val plantLeaf = 0xFF6B8F4E.toInt()
    }

    private fun classifyFurniture(piece: HomePieceOfFurniture): FurnitureKind {
        val key = listOfNotNull(piece.name, piece.catalogID)
            .joinToString(" ")
            .lowercase()
        return when {
            key.contains("bed") && !key.contains("table") && !key.contains("side") -> FurnitureKind.Bed
            key.contains("sofa") || key.contains("couch") || key.contains("loveseat") -> FurnitureKind.Sofa
            key.contains("armchair") -> FurnitureKind.Sofa
            key.contains("toilet") || key.contains("wc") -> FurnitureKind.Toilet
            key.contains("bath") || key.contains("tub") -> FurnitureKind.Bathtub
            key.contains("wardrobe") || key.contains("closet") || key.contains("armoire") ||
                key.contains("dresser") || key.contains("bookcase") -> FurnitureKind.Wardrobe
            key.contains("plant") || key.contains("tree") || key.contains("foliage") -> FurnitureKind.Plant
            key.contains("table") || key.contains("desk") || key.contains("counter") -> FurnitureKind.Table
            key.contains("chair") || key.contains("stool") || key.contains("seat") -> FurnitureKind.Chair
            else -> FurnitureKind.Box
        }
    }

    private fun furnitureMeshes(piece: HomePieceOfFurniture): List<MeshTri> {
        val tint = parseColor(piece.color)
        val modelPath = piece.modelURL
        if (!modelPath.isNullOrBlank() && modelPath.lowercase().endsWith(".obj")) {
            val color = tint ?: DEFAULT_FURNITURE
            val loaded = ObjLoader.loadAsFurniture(modelPath, piece, color, ROUGH_FURNITURE)
            if (!loaded.isNullOrEmpty()) return loaded
        }
        return when (classifyFurniture(piece)) {
            FurnitureKind.Bed -> bedMeshes(piece, tint)
            FurnitureKind.Sofa -> sofaMeshes(piece, tint)
            FurnitureKind.Table -> tableMeshes(piece, tint)
            FurnitureKind.Chair -> chairMeshes(piece, tint)
            FurnitureKind.Toilet -> toiletMeshes(piece, tint)
            FurnitureKind.Bathtub -> bathtubMeshes(piece, tint)
            FurnitureKind.Wardrobe -> wardrobeMeshes(piece, tint)
            FurnitureKind.Plant -> plantMeshes(piece, tint)
            FurnitureKind.Box -> listOf(furnitureBox(piece, tint ?: DEFAULT_FURNITURE))
        }
    }

    private fun furnitureBox(piece: HomePieceOfFurniture, colorArgb: Int = parseColor(piece.color) ?: DEFAULT_FURNITURE): MeshTri =
        localBox(
            piece,
            lx = 0.0,
            lz = 0.0,
            width = piece.width,
            depth = piece.depth,
            y0 = piece.elevation,
            y1 = piece.elevation + piece.height,
            colorArgb = colorArgb,
        )

    private fun bedMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val platformH = (h * 0.38).coerceIn(18.0, 42.0)
        val headH = h.coerceAtLeast(platformH + 20.0)
        val headDepth = (d * 0.08).coerceIn(6.0, 14.0)
        val platform = localBox(
            piece, 0.0, headDepth * 0.5, w, d - headDepth, y0, y0 + platformH,
            tint ?: FurnColor.bed,
        )
        val head = localBox(
            piece, 0.0, -(d * 0.5 - headDepth * 0.5), w, headDepth, y0, y0 + headH,
            tint?.let { darken(it, 0.85) } ?: FurnColor.bedHead,
        )
        return listOf(platform, head)
    }

    private fun sofaMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val seatH = (h * 0.48).coerceIn(28.0, 48.0)
        val backDepth = (d * 0.22).coerceIn(10.0, 22.0)
        val seatDepth = (d - backDepth).coerceAtLeast(d * 0.5)
        val seat = localBox(
            piece, 0.0, backDepth * 0.5, w, seatDepth, y0, y0 + seatH,
            tint ?: FurnColor.sofa,
        )
        val back = localBox(
            piece, 0.0, -(d * 0.5 - backDepth * 0.5), w, backDepth, y0, y0 + h,
            tint?.let { darken(it, 0.88) } ?: FurnColor.sofaBack,
        )
        val armW = (w * 0.08).coerceIn(6.0, 14.0)
        val armH = (seatH + h) * 0.5
        val armL = localBox(
            piece, -(w * 0.5 - armW * 0.5), backDepth * 0.15, armW, seatDepth * 0.9, y0, y0 + armH,
            tint?.let { darken(it, 0.9) } ?: FurnColor.sofaBack,
        )
        val armR = localBox(
            piece, (w * 0.5 - armW * 0.5), backDepth * 0.15, armW, seatDepth * 0.9, y0, y0 + armH,
            tint?.let { darken(it, 0.9) } ?: FurnColor.sofaBack,
        )
        return listOf(seat, back, armL, armR)
    }

    private fun tableMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val topT = (h * 0.08).coerceIn(3.0, 8.0)
        val leg = (min(w, d) * 0.06).coerceIn(4.0, 10.0)
        val inset = leg * 1.2
        val topColor = tint ?: FurnColor.table
        val legColor = tint?.let { darken(it, 0.82) } ?: FurnColor.tableLeg
        val top = localBox(piece, 0.0, 0.0, w, d, y0 + h - topT, y0 + h, topColor)
        val legs = listOf(
            localBox(piece, -(w * 0.5 - inset), -(d * 0.5 - inset), leg, leg, y0, y0 + h - topT, legColor),
            localBox(piece, (w * 0.5 - inset), -(d * 0.5 - inset), leg, leg, y0, y0 + h - topT, legColor),
            localBox(piece, -(w * 0.5 - inset), (d * 0.5 - inset), leg, leg, y0, y0 + h - topT, legColor),
            localBox(piece, (w * 0.5 - inset), (d * 0.5 - inset), leg, leg, y0, y0 + h - topT, legColor),
        )
        return listOf(top) + legs
    }

    private fun chairMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val seatH = (h * 0.45).coerceIn(32.0, 48.0)
        val backDepth = (d * 0.12).coerceIn(3.0, 8.0)
        val seatDepth = (d - backDepth).coerceAtLeast(d * 0.55)
        val leg = (min(w, d) * 0.08).coerceIn(3.0, 6.0)
        val seatColor = tint ?: FurnColor.chair
        val backColor = tint?.let { darken(it, 0.88) } ?: FurnColor.chairBack
        val seat = localBox(
            piece, 0.0, backDepth * 0.5, w * 0.92, seatDepth, y0 + seatH - 4.0, y0 + seatH, seatColor,
        )
        val back = localBox(
            piece, 0.0, -(d * 0.5 - backDepth * 0.5), w * 0.9, backDepth, y0 + seatH - 4.0, y0 + h, backColor,
        )
        val insetX = w * 0.35
        val insetZ = d * 0.28
        val legs = listOf(
            localBox(piece, -insetX, -insetZ, leg, leg, y0, y0 + seatH - 4.0, backColor),
            localBox(piece, insetX, -insetZ, leg, leg, y0, y0 + seatH - 4.0, backColor),
            localBox(piece, -insetX, insetZ, leg, leg, y0, y0 + seatH - 4.0, backColor),
            localBox(piece, insetX, insetZ, leg, leg, y0, y0 + seatH - 4.0, backColor),
        )
        return listOf(seat, back) + legs
    }

    private fun toiletMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val bowlH = (h * 0.55).coerceIn(32.0, 48.0)
        val tankD = (d * 0.28).coerceIn(12.0, 22.0)
        val bowlD = (d - tankD).coerceAtLeast(d * 0.45)
        val bowlColor = tint ?: FurnColor.toilet
        val tankColor = tint?.let { darken(it, 0.9) } ?: FurnColor.toiletTank
        val bowl = localCylinder(
            piece,
            lx = 0.0,
            lz = tankD * 0.5,
            radius = min(w * 0.45, bowlD * 0.45),
            y0 = y0,
            y1 = y0 + bowlH,
            colorArgb = bowlColor,
            segments = 16,
        )
        val tank = localBox(
            piece, 0.0, -(d * 0.5 - tankD * 0.5), w * 0.85, tankD, y0 + bowlH * 0.35, y0 + h, tankColor,
        )
        return listOf(bowl, tank)
    }

    private fun bathtubMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val wallT = (min(w, d) * 0.08).coerceIn(4.0, 10.0)
        val floorH = (h * 0.18).coerceIn(6.0, 14.0)
        val outer = tint ?: FurnColor.bath
        val inner = tint?.let { darken(it, 0.92) } ?: FurnColor.bathInner
        val floor = localBox(piece, 0.0, 0.0, w - wallT * 2, d - wallT * 2, y0, y0 + floorH, inner)
        val north = localBox(piece, 0.0, -(d * 0.5 - wallT * 0.5), w, wallT, y0, y0 + h, outer)
        val south = localBox(piece, 0.0, (d * 0.5 - wallT * 0.5), w, wallT, y0, y0 + h, outer)
        val west = localBox(
            piece, -(w * 0.5 - wallT * 0.5), 0.0, wallT, d - wallT * 2, y0, y0 + h, outer,
        )
        val east = localBox(
            piece, (w * 0.5 - wallT * 0.5), 0.0, wallT, d - wallT * 2, y0, y0 + h, outer,
        )
        return listOf(floor, north, south, west, east)
    }

    private fun wardrobeMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val body = tint ?: FurnColor.wardrobe
        val door = tint?.let { darken(it, 0.88) } ?: FurnColor.wardrobeDoor
        val cabinet = localBox(piece, 0.0, 0.0, w, d * 0.92, y0, y0 + h, body)
        val doorT = (d * 0.06).coerceIn(1.5, 4.0)
        val gap = 1.5
        val halfW = (w - gap) * 0.5
        val doorL = localBox(
            piece, -(halfW * 0.5 + gap * 0.25), d * 0.5 - doorT * 0.5, halfW * 0.95, doorT,
            y0 + 4.0, y0 + h - 4.0, door,
        )
        val doorR = localBox(
            piece, (halfW * 0.5 + gap * 0.25), d * 0.5 - doorT * 0.5, halfW * 0.95, doorT,
            y0 + 4.0, y0 + h - 4.0, door,
        )
        return listOf(cabinet, doorL, doorR)
    }

    private fun plantMeshes(piece: HomePieceOfFurniture, tint: Int?): List<MeshTri> {
        val w = piece.width
        val d = piece.depth
        val h = piece.height
        val y0 = piece.elevation
        val r = min(w, d) * 0.5
        val potH = (h * 0.28).coerceIn(12.0, 28.0)
        val pot = localCylinder(
            piece, 0.0, 0.0, r * 0.55, y0, y0 + potH,
            tint ?: FurnColor.plantPot, segments = 14,
        )
        val foliage = localCylinder(
            piece, 0.0, 0.0, r * 0.95, y0 + potH * 0.7, y0 + h,
            tint?.let { mixGreen(it) } ?: FurnColor.plantLeaf, segments = 16,
        )
        return listOf(pot, foliage)
    }

    private fun darken(argb: Int, factor: Double): Int {
        val r = (((argb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun mixGreen(argb: Int): Int {
        val r = (((argb shr 16) and 0xFF) * 0.35 + 70).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) * 0.45 + 110).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * 0.3 + 40).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Axis-aligned box in piece-local plan space (width→X, depth→Z), then
     * rotated by [HomePieceOfFurniture.angle] (and flipped if modelMirrored).
     */
    private fun localBox(
        piece: HomePieceOfFurniture,
        lx: Double,
        lz: Double,
        width: Double,
        depth: Double,
        y0: Double,
        y1: Double,
        colorArgb: Int,
    ): MeshTri {
        val mx = if (piece.modelMirrored) -1.0 else 1.0
        val cx = piece.x
        val cz = piece.y
        val angle = piece.angle
        val c = cos(angle)
        val s = sin(angle)
        val hw = width / 2
        val hd = depth / 2
        fun corner(ox: Double, oz: Double): Pair<Double, Double> {
            val localX = (lx + ox) * mx
            val localZ = lz + oz
            return (cx + localX * c - localZ * s) to (cz + localX * s + localZ * c)
        }
        val foot = listOf(
            corner(-hw, -hd),
            corner(hw, -hd),
            corner(hw, hd),
            corner(-hw, hd),
        )
        return prismFromFootprint(foot, y0, y1, colorArgb, ROUGH_FURNITURE)
    }

    private fun localCylinder(
        piece: HomePieceOfFurniture,
        lx: Double,
        lz: Double,
        radius: Double,
        y0: Double,
        y1: Double,
        colorArgb: Int,
        segments: Int = 16,
    ): MeshTri {
        val mx = if (piece.modelMirrored) -1.0 else 1.0
        val cx = piece.x
        val cz = piece.y
        val angle = piece.angle
        val c = cos(angle)
        val s = sin(angle)
        val foot = ArrayList<Pair<Double, Double>>(segments)
        for (i in 0 until segments) {
            val a = i * 2.0 * Math.PI / segments
            val ox = cos(a) * radius
            val oz = sin(a) * radius
            val localX = (lx + ox) * mx
            val localZ = lz + oz
            foot.add((cx + localX * c - localZ * s) to (cz + localX * s + localZ * c))
        }
        return prismFromFootprint(foot, y0, y1, colorArgb, ROUGH_FURNITURE)
    }

    private fun segmentMeshes(
        seg: WallSegment,
        colorArgb: Int,
        leftTex: Triple<String, Double, Double>?,
        rightTex: Triple<String, Double, Double>?,
    ): List<MeshTri> {
        val dx = seg.endX - seg.startX
        val dy = seg.endY - seg.startY
        val len = hypot(dx, dy)
        if (len < 1e-9 || seg.topY <= seg.bottomY) return emptyList()
        val ux = dx / len
        val uy = dy / len
        // Left-hand offset matches WallGeometry.unjoinedOutline (Y-down plan).
        val hx = uy * (seg.thickness * 0.5)
        val hy = -ux * (seg.thickness * 0.5)
        val foot = listOf(
            (seg.startX + hx) to (seg.startY + hy),
            (seg.endX + hx) to (seg.endY + hy),
            (seg.endX - hx) to (seg.endY - hy),
            (seg.startX - hx) to (seg.startY - hy),
        )
        return wallPrismMeshes(
            footprint = foot,
            y0 = seg.bottomY,
            y1 = seg.topY,
            colorArgb = colorArgb,
            leftTex = leftTex,
            rightTex = rightTex,
            fourPointSides = true,
        )
    }

    private fun isLightColor(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r + g + b) / 3 > 180
    }

    private fun warmRoof(argb: Int): Int {
        val r = ((argb shr 16) and 0xFF)
        val g = ((argb shr 8) and 0xFF)
        val b = (argb and 0xFF)
        val nr = min(255, (r * 0.85 + 40).toInt())
        val ng = min(255, (g * 0.65 + 20).toInt())
        val nb = min(255, (b * 0.45).toInt())
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    private fun buildDoorLeaves(home: Home, level: String?): List<DoorLeaf3D> {
        val openings = home.doorsAndWindows.filter { level == null || it.piece.level == level }
        val out = ArrayList<DoorLeaf3D>()
        for (dw in openings) {
            if (OpeningSymbol.classify(dw) != OpeningSymbolKind.Operable) continue
            if (!shouldSwingLeaf(dw)) continue
            if (dw.sashes.isEmpty()) {
                singleLeafFromPiece(dw)?.let { out.add(it) }
                continue
            }
            for (sash in dw.sashes) {
                sashLeaf(dw, sash)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun shouldSwingLeaf(dw: HomeDoorOrWindow): Boolean {
        if (!OpeningSwing.isWindow(dw)) return true
        val name = (dw.piece.name ?: "").lowercase()
        return name.contains("french") || name.contains("double")
    }

    private fun singleLeafFromPiece(dw: HomeDoorOrWindow): DoorLeaf3D? {
        val hinge = OpeningSwing.hingePoint(dw) ?: return null
        val piece = dw.piece
        val w = piece.width.coerceAtLeast(1.0)
        val d = piece.depth.coerceAtLeast(2.0)
        val h = piece.height.coerceAtLeast(1.0)
        val color = 0xFFC4A574.toInt()
        val local = localDoorLeaf(
            (w * CM_TO_M).toFloat(),
            (d * CM_TO_M).toFloat(),
            (h * CM_TO_M).toFloat(),
            color,
            ROUGH_DOOR,
        )
        // Hinge on left (or right if mirroredX): closed leaf extends along piece.angle.
        val yaw = if (dw.mirroredX) {
            (piece.angle + Math.PI).toFloat()
        } else {
            piece.angle.toFloat()
        }
        return DoorLeaf3D(
            localMesh = local,
            hingeX = (hinge.x * CM_TO_M).toFloat(),
            hingeY = (piece.elevation * CM_TO_M).toFloat(),
            hingeZ = (hinge.y * CM_TO_M).toFloat(),
            closedYawRad = yaw,
            swingRad = OpeningSwing.signedSwingRadians(dw).toFloat(),
        )
    }

    private fun sashLeaf(dw: HomeDoorOrWindow, sash: com.homedesign.android.domain.model.Sash): DoorLeaf3D? {
        val arc = OpeningSymbol.sashArc(sash, dw)
        if (arc.radius < 1.0) return null
        val piece = dw.piece
        val d = piece.depth.coerceAtLeast(2.0)
        val h = piece.height.coerceAtLeast(1.0)
        val color = 0xFFC4A574.toInt()
        val local = localDoorLeaf(
            (arc.radius * CM_TO_M).toFloat(),
            (d * CM_TO_M).toFloat(),
            (h * CM_TO_M).toFloat(),
            color,
            ROUGH_DOOR,
        )
        // Closed leaf lies along startAngle (plan).
        val swing = (arc.endAngle - arc.startAngle).toFloat()
        return DoorLeaf3D(
            localMesh = local,
            hingeX = (arc.center.x * CM_TO_M).toFloat(),
            hingeY = (piece.elevation * CM_TO_M).toFloat(),
            hingeZ = (arc.center.y * CM_TO_M).toFloat(),
            closedYawRad = arc.startAngle.toFloat(),
            swingRad = swing,
        )
    }

    /** Leaf in metres: hinge at x=0, extends +X by width, Y up, Z = depth. */
    private fun localDoorLeaf(
        widthM: Float,
        depthM: Float,
        heightM: Float,
        colorArgb: Int,
        roughness: Float = ROUGH_DOOR,
    ): MeshTri {
        val w = widthM.toDouble() / CM_TO_M
        val d = depthM.toDouble() / CM_TO_M
        val h = heightM.toDouble() / CM_TO_M
        // Build in "plan cm" local then convert via planToWorld with origin hinge.
        val foot = listOf(
            0.0 to (-d / 2),
            w to (-d / 2),
            w to (d / 2),
            0.0 to (d / 2),
        )
        return prismFromFootprint(foot, 0.0, h, colorArgb, roughness)
    }

    private fun hipRoof(
        pts: List<Pair<Double, Double>>,
        eavesY: Double,
        colorArgb: Int,
    ): MeshTri {
        var cx = 0.0
        var cz = 0.0
        for (p in pts) {
            cx += p.first
            cz += p.second
        }
        cx /= pts.size
        cz /= pts.size
        var maxR = 0.0
        for (p in pts) {
            maxR = max(maxR, hypot(p.first - cx, p.second - cz))
        }
        val rise = (maxR * ROOF_RISE_FRAC).coerceIn(ROOF_RISE_MIN_CM, ROOF_RISE_MAX_CM)
        val peakY = eavesY + rise
        val peak = planToWorld(cx, cz, peakY)
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val n = pts.size
        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            val wa = planToWorld(a.first, a.second, eavesY)
            val wb = planToWorld(b.first, b.second, eavesY)
            val abx = wb[0] - wa[0]
            val aby = wb[1] - wa[1]
            val abz = wb[2] - wa[2]
            val apx = peak[0] - wa[0]
            val apy = peak[1] - wa[1]
            val apz = peak[2] - wa[2]
            var nx = aby * apz - abz * apy
            var ny = abz * apx - abx * apz
            var nz = abx * apy - aby * apx
            val len = hypot(nx.toDouble(), hypot(ny.toDouble(), nz.toDouble())).toFloat().coerceAtLeast(1e-6f)
            nx /= len; ny /= len; nz /= len
            if (ny < 0f) {
                nx = -nx; ny = -ny; nz = -nz
            }
            addTri(pos, nrm, wa, wb, peak, floatArrayOf(nx, ny, nz))
        }
        return MeshTri(pos.toFloatArray(), nrm.toFloatArray(), colorArgb, ROUGH_ROOF)
    }

    private fun fenceMeshes(minX: Double, minZ: Double, maxX: Double, maxZ: Double): List<MeshTri> {
        val postColor = 0xFF5C4033.toInt()
        val railColor = 0xFF7A5C45.toInt()
        val meshes = ArrayList<MeshTri>()
        val corners = listOf(
            minX to minZ,
            maxX to minZ,
            maxX to maxZ,
            minX to maxZ,
        )
        val edges = listOf(
            corners[0] to corners[1],
            corners[1] to corners[2],
            corners[2] to corners[3],
            corners[3] to corners[0],
        )
        for ((a, b) in edges) {
            val dx = b.first - a.first
            val dz = b.second - a.second
            val len = hypot(dx, dz).coerceAtLeast(1.0)
            val count = max(2, (len / FENCE_POST_SPACING_CM).toInt() + 1)
            val posts = ArrayList<Pair<Double, Double>>()
            for (i in 0 until count) {
                val t = i / (count - 1).toDouble()
                val px = a.first + dx * t
                val pz = a.second + dz * t
                posts.add(px to pz)
                meshes.add(
                    orientedBox(
                        cx = px,
                        cz = pz,
                        width = FENCE_POST_W_CM,
                        depth = FENCE_POST_W_CM,
                        y0 = 0.0,
                        y1 = FENCE_POST_H_CM,
                        angle = 0.0,
                        colorArgb = postColor,
                        roughness = ROUGH_FENCE,
                    ),
                )
            }
            val railHeights = listOf(40.0, 85.0)
            for (rh in railHeights) {
                for (i in 0 until posts.size - 1) {
                    val p0 = posts[i]
                    val p1 = posts[i + 1]
                    val mx = (p0.first + p1.first) / 2
                    val mz = (p0.second + p1.second) / 2
                    val seg = hypot(p1.first - p0.first, p1.second - p0.second)
                    val ang = kotlin.math.atan2(p1.second - p0.second, p1.first - p0.first)
                    meshes.add(
                        orientedBox(
                            cx = mx,
                            cz = mz,
                            width = seg,
                            depth = FENCE_RAIL_T_CM,
                            y0 = rh - FENCE_RAIL_T_CM,
                            y1 = rh + FENCE_RAIL_T_CM,
                            angle = ang,
                            colorArgb = railColor,
                            roughness = ROUGH_FENCE,
                        ),
                    )
                }
            }
        }
        return meshes
    }

    private fun planBoundsCm(
        walls: List<Wall>,
        rooms: List<com.homedesign.android.domain.model.Room>,
    ): DoubleArray? {
        var minX = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        fun hit(x: Double, z: Double) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        for (w in walls) {
            hit(w.startX, w.startY)
            hit(w.endX, w.endY)
        }
        for (r in rooms) {
            for (p in r.points) hit(p.x, p.y)
        }
        if (!minX.isFinite()) return null
        return doubleArrayOf(minX, minZ, maxX, maxZ)
    }

    private fun parseColor(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val hex = raw.removePrefix("#").takeLast(6)
        if (hex.length != 6) return null
        return runCatching { (0xFF shl 24) or hex.toInt(16) }.getOrNull()
    }

    private fun planToWorld(xCm: Double, yCm: Double, yUpCm: Double): FloatArray =
        floatArrayOf(
            (xCm * CM_TO_M).toFloat(),
            (yUpCm * CM_TO_M).toFloat(),
            (yCm * CM_TO_M).toFloat(),
        )

    /**
     * Returns (loadKey, tileWcm, tileHcm).
     * Presets use assets-relative paths; user imports use absolute file paths
     * (`file:` prefix so Plan3D can distinguish).
     */
    private fun resolveTexture(
        tex: WallTexture?,
        preferWall: Boolean = false,
    ): Triple<String, Double, Double>? {
        if (tex == null) return null
        val handle = tex.image ?: tex.catalogID ?: return null
        val preset = findPreset(handle, preferWall = preferWall)
        if (preset != null) {
            val path = presetAssetPath(preset)
            val tw = tex.width?.takeIf { it > 1.0 } ?: preset.width
            val th = tex.height?.takeIf { it > 1.0 } ?: preset.height
            return Triple(path, tw, th)
        }
        // Absolute path from UserTextureStore.
        if (handle.startsWith("/") || handle.contains(":\\") || handle.startsWith("file:")) {
            val path = handle.removePrefix("file:")
            val tw = tex.width?.takeIf { it > 1.0 } ?: 100.0
            val th = tex.height?.takeIf { it > 1.0 } ?: 100.0
            return Triple("file:$path", tw, th)
        }
        return null
    }

    private fun floorMesh(
        pts: List<Pair<Double, Double>>,
        colorArgb: Int,
        textureAssetPath: String? = null,
        tileWidthCM: Double = 100.0,
        tileHeightCM: Double = 100.0,
    ): MeshTri {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val uvs = if (textureAssetPath != null) ArrayList<Float>() else null
        val tileWm = (tileWidthCM.coerceAtLeast(1.0) * CM_TO_M).toFloat()
        val tileHm = (tileHeightCM.coerceAtLeast(1.0) * CM_TO_M).toFloat()
        fun pushUv(world: FloatArray) {
            uvs?.add(world[0] / tileWm)
            uvs?.add(world[2] / tileHm)
        }
        val c0 = pts[0]
        for (i in 1 until pts.size - 1) {
            val a = planToWorld(c0.first, c0.second, 0.0)
            val b = planToWorld(pts[i].first, pts[i].second, 0.0)
            val c = planToWorld(pts[i + 1].first, pts[i + 1].second, 0.0)
            pos.addAll(a.toList()); pos.addAll(b.toList()); pos.addAll(c.toList())
            pushUv(a); pushUv(b); pushUv(c)
            repeat(3) {
                nrm.add(0f); nrm.add(1f); nrm.add(0f)
            }
        }
        return MeshTri(
            positions = pos.toFloatArray(),
            normals = nrm.toFloatArray(),
            colorArgb = colorArgb,
            roughness = ROUGH_FLOOR,
            uvs = uvs?.toFloatArray(),
            textureAssetPath = textureAssetPath,
        )
    }

    /**
     * Extrude a wall footprint. When [fourPointSides] and textures are set,
     * left (0→1) / right (3→2) faces get UV tiles; caps + ends stay solid colour.
     * Curved / many-point footprints apply [leftTex] to every vertical face when set.
     */
    private fun wallPrismMeshes(
        footprint: List<Pair<Double, Double>>,
        y0: Double,
        y1: Double,
        colorArgb: Int,
        leftTex: Triple<String, Double, Double>? = null,
        rightTex: Triple<String, Double, Double>? = null,
        fourPointSides: Boolean = false,
    ): List<MeshTri> {
        if (footprint.size < 3 || y1 <= y0) return emptyList()
        val textured = leftTex != null || rightTex != null
        if (!textured) {
            return listOf(prismFromFootprint(footprint, y0, y1, colorArgb, ROUGH_WALL))
        }
        val out = ArrayList<MeshTri>(4)
        out.add(prismCaps(footprint, y0, y1, colorArgb))
        if (fourPointSides && footprint.size == 4) {
            out.add(wallSideQuad(footprint[0], footprint[1], y0, y1, colorArgb, leftTex))
            out.add(wallSideQuad(footprint[3], footprint[2], y0, y1, colorArgb, rightTex))
            out.add(wallSideQuad(footprint[1], footprint[2], y0, y1, colorArgb, null))
            out.add(wallSideQuad(footprint[3], footprint[0], y0, y1, colorArgb, null))
        } else {
            val n = footprint.size
            for (i in 0 until n) {
                val a = footprint[i]
                val b = footprint[(i + 1) % n]
                out.add(wallSideQuad(a, b, y0, y1, colorArgb, leftTex ?: rightTex))
            }
        }
        return out.filter { it.positions.isNotEmpty() }
    }

    private fun prismCaps(
        footprint: List<Pair<Double, Double>>,
        y0: Double,
        y1: Double,
        colorArgb: Int,
    ): MeshTri {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val n = footprint.size
        val t0 = footprint[0]
        for (i in 1 until n - 1) {
            addTri(
                pos, nrm,
                planToWorld(t0.first, t0.second, y1),
                planToWorld(footprint[i].first, footprint[i].second, y1),
                planToWorld(footprint[i + 1].first, footprint[i + 1].second, y1),
                floatArrayOf(0f, 1f, 0f),
            )
        }
        for (i in 1 until n - 1) {
            addTri(
                pos, nrm,
                planToWorld(t0.first, t0.second, y0),
                planToWorld(footprint[i + 1].first, footprint[i + 1].second, y0),
                planToWorld(footprint[i].first, footprint[i].second, y0),
                floatArrayOf(0f, -1f, 0f),
            )
        }
        return MeshTri(pos.toFloatArray(), nrm.toFloatArray(), colorArgb, ROUGH_WALL)
    }

    /** Vertical wall face; U along length, V up height when [tex] is set. */
    private fun wallSideQuad(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        y0: Double,
        y1: Double,
        colorArgb: Int,
        tex: Triple<String, Double, Double>?,
    ): MeshTri {
        val dx = (b.first - a.first).toFloat()
        val dz = (b.second - a.second).toFloat()
        val len = hypot(dx.toDouble(), dz.toDouble()).toFloat().coerceAtLeast(1e-6f)
        val nx = dz / len
        val nz = -dx / len
        val nrmSide = floatArrayOf(nx, 0f, nz)
        val bl = planToWorld(a.first, a.second, y0)
        val br = planToWorld(b.first, b.second, y0)
        val tr = planToWorld(b.first, b.second, y1)
        val tl = planToWorld(a.first, a.second, y1)
        val pos = ArrayList<Float>(18)
        val nrm = ArrayList<Float>(18)
        addTri(pos, nrm, bl, br, tr, nrmSide)
        addTri(pos, nrm, bl, tr, tl, nrmSide)
        if (tex == null) {
            return MeshTri(pos.toFloatArray(), nrm.toFloatArray(), colorArgb, ROUGH_WALL)
        }
        val tileWm = (tex.second.coerceAtLeast(1.0) * CM_TO_M).toFloat()
        val tileHm = (tex.third.coerceAtLeast(1.0) * CM_TO_M).toFloat()
        val uMax = len / tileWm
        val vMax = ((y1 - y0) * CM_TO_M).toFloat() / tileHm
        // Two tris: bl-br-tr, bl-tr-tl
        val uvs = floatArrayOf(
            0f, 0f, uMax, 0f, uMax, vMax,
            0f, 0f, uMax, vMax, 0f, vMax,
        )
        return MeshTri(
            positions = pos.toFloatArray(),
            normals = nrm.toFloatArray(),
            colorArgb = colorArgb,
            roughness = ROUGH_WALL,
            uvs = uvs,
            textureAssetPath = tex.first,
        )
    }

    private fun prismFromFootprint(
        footprint: List<Pair<Double, Double>>,
        y0: Double,
        y1: Double,
        colorArgb: Int,
        roughness: Float = ROUGH_WALL,
    ): MeshTri {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val n = footprint.size
        val t0 = footprint[0]
        for (i in 1 until n - 1) {
            addTri(
                pos, nrm,
                planToWorld(t0.first, t0.second, y1),
                planToWorld(footprint[i].first, footprint[i].second, y1),
                planToWorld(footprint[i + 1].first, footprint[i + 1].second, y1),
                floatArrayOf(0f, 1f, 0f),
            )
        }
        for (i in 1 until n - 1) {
            addTri(
                pos, nrm,
                planToWorld(t0.first, t0.second, y0),
                planToWorld(footprint[i + 1].first, footprint[i + 1].second, y0),
                planToWorld(footprint[i].first, footprint[i].second, y0),
                floatArrayOf(0f, -1f, 0f),
            )
        }
        for (i in 0 until n) {
            val j = (i + 1) % n
            val a = footprint[i]
            val b = footprint[j]
            val dx = (b.first - a.first).toFloat()
            val dz = (b.second - a.second).toFloat()
            val len = hypot(dx.toDouble(), dz.toDouble()).toFloat().coerceAtLeast(1e-6f)
            val nx = dz / len
            val nz = -dx / len
            val nrmSide = floatArrayOf(nx, 0f, nz)
            val bl = planToWorld(a.first, a.second, y0)
            val br = planToWorld(b.first, b.second, y0)
            val tr = planToWorld(b.first, b.second, y1)
            val tl = planToWorld(a.first, a.second, y1)
            addTri(pos, nrm, bl, br, tr, nrmSide)
            addTri(pos, nrm, bl, tr, tl, nrmSide)
        }
        return MeshTri(pos.toFloatArray(), nrm.toFloatArray(), colorArgb, roughness)
    }

    private fun orientedBox(
        cx: Double,
        cz: Double,
        width: Double,
        depth: Double,
        y0: Double,
        y1: Double,
        angle: Double,
        colorArgb: Int,
        roughness: Float = ROUGH_FURNITURE,
    ): MeshTri {
        val hw = width / 2
        val hd = depth / 2
        val c = cos(angle)
        val s = sin(angle)
        fun corner(lx: Double, lz: Double): Pair<Double, Double> {
            val x = cx + lx * c - lz * s
            val z = cz + lx * s + lz * c
            return x to z
        }
        val foot = listOf(
            corner(-hw, -hd),
            corner(hw, -hd),
            corner(hw, hd),
            corner(-hw, hd),
        )
        return prismFromFootprint(foot, y0, y1, colorArgb, roughness)
    }

    private fun addTri(
        pos: MutableList<Float>,
        nrm: MutableList<Float>,
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        normal: FloatArray,
    ) {
        pos.addAll(a.toList()); pos.addAll(b.toList()); pos.addAll(c.toList())
        repeat(3) {
            nrm.add(normal[0]); nrm.add(normal[1]); nrm.add(normal[2])
        }
    }
}
