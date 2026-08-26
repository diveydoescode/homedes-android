package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.WallStyleMutation
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.textures.FLOOR_PRESETS
import com.homedesign.android.domain.textures.textureFromPreset

/**
 * iOS `HomeStylePreset` — one tap re-skins walls, floors, ceilings.
 * Glass walls (translucent side colour) are left alone.
 */
enum class HomeStylePreset(val id: String, val displayName: String) {
    Modern("modern", "Modern"),
    Italian("italian", "Italian"),
    Mediterranean("mediterranean", "Mediterranean"),
    Scandinavian("scandinavian", "Scandinavian"),
    Industrial("industrial", "Industrial"),
    ;
}

fun applyHomeStyle(home: Home, preset: HomeStylePreset): Home = applyHomeStylePalette(home, preset)

private fun HomeStylePreset.wallHex(): String = when (this) {
    HomeStylePreset.Modern -> "FFF1EFEA"
    HomeStylePreset.Italian -> "FFF3ECDE"
    HomeStylePreset.Mediterranean -> "FFF6F1E7"
    HomeStylePreset.Scandinavian -> "FFF8F7F4"
    HomeStylePreset.Industrial -> "FF8E8B85"
}

private fun HomeStylePreset.trimHex(): String = when (this) {
    HomeStylePreset.Modern -> "FFE9E6DF"
    HomeStylePreset.Italian -> "FFE7DCC6"
    HomeStylePreset.Mediterranean -> "FFDCE6EC"
    HomeStylePreset.Scandinavian -> "FFFFFFFF"
    HomeStylePreset.Industrial -> "FF6F6C67"
}

private fun HomeStylePreset.ceilingHex(): String = when (this) {
    HomeStylePreset.Modern -> "FFF4F2EE"
    HomeStylePreset.Italian -> "FFF2EBDC"
    HomeStylePreset.Mediterranean -> "FFEFEFE9"
    HomeStylePreset.Scandinavian -> "FFFCFBF9"
    HomeStylePreset.Industrial -> "FF74716C"
}

private fun HomeStylePreset.floorSlug(): String = when (this) {
    HomeStylePreset.Modern -> "wood-pale"
    HomeStylePreset.Italian -> "marble-white"
    HomeStylePreset.Mediterranean -> "stone-rect"
    HomeStylePreset.Scandinavian -> "wood-beech"
    HomeStylePreset.Industrial -> "granite"
}

private fun isTranslucentHex(hex: String?): Boolean {
    if (hex.isNullOrBlank()) return false
    val h = hex.removePrefix("#")
    if (h.length != 8) return false
    return !h.startsWith("FF", ignoreCase = true)
}

private fun applyHomeStylePalette(home: Home, preset: HomeStylePreset): Home {
    val wallHex = preset.wallHex()
    val trimHex = preset.trimHex()
    val ceilingHex = preset.ceilingHex()
    val floor = FLOOR_PRESETS.find { it.slug == preset.floorSlug() }?.let { textureFromPreset(it) }
    val walls = home.walls.map { wall ->
        if (WallStyleMutation.isGlass(wall) ||
            isTranslucentHex(wall.leftSideColor) ||
            isTranslucentHex(wall.rightSideColor)
        ) {
            wall
        } else {
            wall.copy(
                leftSideColor = wallHex,
                rightSideColor = wallHex,
                topColor = trimHex,
                leftSideTexture = null,
                rightSideTexture = null,
            )
        }
    }
    val rooms = home.rooms.map { room ->
        room.copy(
            ceilingColor = if (room.ceilingVisible) ceilingHex else room.ceilingColor,
            floorTexture = floor ?: room.floorTexture,
        )
    }
    return home.copy(
        walls = walls,
        rooms = rooms,
        topologyVersion = home.topologyVersion + 1,
        styleVersion = home.styleVersion + 1,
    )
}
