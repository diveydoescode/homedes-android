package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Baseboard
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.WallTexture
import kotlin.math.hypot

/**
 * Per-side wall paint / texture / size. Port of web `WallStyleMutation.ts`
 * (and iOS WallStyleMutation). A non-nil side colour clears that side's texture.
 */
object WallStyleMutation {
    private fun update(
        walls: List<Wall>,
        wallID: String,
        transform: (Wall) -> Wall,
    ): List<Wall> {
        if (walls.none { it.id == wallID }) return walls.toList()
        return walls.map { wall -> if (wall.id == wallID) transform(wall) else wall }
    }

    fun setLeftSideColor(wallID: String, color: String?, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { wall ->
            wall.copy(
                leftSideColor = color,
                leftSideTexture = if (color != null) null else wall.leftSideTexture,
            )
        }

    fun setRightSideColor(wallID: String, color: String?, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { wall ->
            wall.copy(
                rightSideColor = color,
                rightSideTexture = if (color != null) null else wall.rightSideTexture,
            )
        }

    fun setTopColor(wallID: String, color: String?, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(topColor = color) }

    fun setLeftSideTexture(wallID: String, texture: WallTexture, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(leftSideTexture = texture, leftSideColor = null) }

    fun setRightSideTexture(wallID: String, texture: WallTexture, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(rightSideTexture = texture, rightSideColor = null) }

    fun clearLeftSideTexture(wallID: String, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(leftSideTexture = null) }

    fun clearRightSideTexture(wallID: String, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(rightSideTexture = null) }

    fun setLeftSideBaseboard(wallID: String, baseboard: Baseboard?, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(leftSideBaseboard = baseboard) }

    fun setRightSideBaseboard(wallID: String, baseboard: Baseboard?, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(rightSideBaseboard = baseboard) }

    fun setHeight(wallID: String, height: Double, walls: List<Wall>): List<Wall> {
        if (height !in 20.0..600.0) return walls.toList()
        return update(walls, wallID) { it.copy(height = height) }
    }

    fun setThickness(wallID: String, thickness: Double, walls: List<Wall>): List<Wall> {
        if (thickness !in 4.0..60.0) return walls.toList()
        return update(walls, wallID) { it.copy(thickness = thickness) }
    }

    /** SH3D hatch token (`hatchUp`) or null for solid fill. */
    fun setPattern(wallID: String, pattern: String?, walls: List<Wall>): List<Wall> =
        update(walls, wallID) { it.copy(pattern = pattern) }

    /**
     * Glass partition hint via shininess + optional glass tint on both sides.
     * Model has no dedicated glass flag — shininess is the closest field.
     */
    fun setGlass(
        wallID: String,
        glass: Boolean,
        walls: List<Wall>,
        glassColor: String = com.homedesign.android.domain.textures.GLASS_SIDE_COLOR,
        shininess: Double = com.homedesign.android.domain.textures.GLASS_SHININESS,
    ): List<Wall> =
        update(walls, wallID) { wall ->
            if (glass) {
                wall.copy(
                    leftSideShininess = shininess,
                    rightSideShininess = shininess,
                    leftSideColor = glassColor,
                    rightSideColor = glassColor,
                    leftSideTexture = null,
                    rightSideTexture = null,
                )
            } else {
                wall.copy(
                    leftSideShininess = null,
                    rightSideShininess = null,
                )
            }
        }

    fun isGlass(wall: Wall): Boolean {
        val left = wall.leftSideShininess ?: 0.0
        val right = wall.rightSideShininess ?: 0.0
        return left >= 0.25 || right >= 0.25
    }

    fun isHatched(wall: Wall): Boolean {
        val p = wall.pattern ?: return false
        return p.contains("hatch", ignoreCase = true)
    }

    fun matchAttributes(
        sourceID: String,
        targetIDs: List<String>,
        walls: List<Wall>,
        copyLength: Boolean = false,
    ): List<Wall> {
        val source = walls.find { it.id == sourceID } ?: return walls.toList()
        val targets = targetIDs.filter { it != sourceID }.toSet()
        if (targets.isEmpty()) return walls.toList()
        val sourceLength = hypot(source.endX - source.startX, source.endY - source.startY)
        val sourceStraight = !ArcWallGeometry.isCurved(source)
        return walls.map { wall ->
            if (wall.id !in targets) return@map wall
            var copy = wall.copy(
                thickness = source.thickness,
                height = source.height,
                pattern = source.pattern,
                leftSideColor = source.leftSideColor,
                rightSideColor = source.rightSideColor,
                topColor = source.topColor,
                leftSideShininess = source.leftSideShininess,
                rightSideShininess = source.rightSideShininess,
                leftSidePattern = source.leftSidePattern,
                rightSidePattern = source.rightSidePattern,
                leftSideTexture = source.leftSideTexture,
                rightSideTexture = source.rightSideTexture,
                leftSideBaseboard = source.leftSideBaseboard,
                rightSideBaseboard = source.rightSideBaseboard,
            )
            if (
                copyLength &&
                sourceStraight &&
                sourceLength > 1e-6 &&
                !ArcWallGeometry.isCurved(copy)
            ) {
                val dx = copy.endX - copy.startX
                val dy = copy.endY - copy.startY
                val curLen = hypot(dx, dy)
                if (curLen > 1e-6) {
                    val scale = sourceLength / curLen
                    copy = copy.copy(
                        endX = copy.startX + dx * scale,
                        endY = copy.startY + dy * scale,
                    )
                }
            }
            copy
        }
    }
}
