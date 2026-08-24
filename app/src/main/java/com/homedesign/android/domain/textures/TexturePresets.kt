package com.homedesign.android.domain.textures

import com.homedesign.android.domain.model.WallTexture

const val PRESET_PREFIX = "preset:"

data class TexturePreset(
    val slug: String,
    val name: String,
    val width: Double,
    val height: Double,
    val kind: Kind,
) {
    enum class Kind { Floor, Wall }
}

val FLOOR_PRESETS: List<TexturePreset> = listOf(
    TexturePreset("parquet-brown", "Brown parquet", 100.0, 100.0, TexturePreset.Kind.Floor),
    TexturePreset("parquet-english", "English parquet", 120.0, 120.0, TexturePreset.Kind.Floor),
    TexturePreset("tile-grey", "Grey tiles", 31.5, 31.5, TexturePreset.Kind.Floor),
    TexturePreset("stone-rect", "Stone", 100.0, 100.0, TexturePreset.Kind.Floor),
    TexturePreset("wood-beech", "Beech", 40.0, 80.0, TexturePreset.Kind.Floor),
    TexturePreset("wood-pale", "Pale wood", 40.0, 80.0, TexturePreset.Kind.Floor),
    TexturePreset("wood-dark", "Dark wood", 100.0, 50.0, TexturePreset.Kind.Floor),
    TexturePreset("wood-walnut", "Walnut", 40.0, 80.0, TexturePreset.Kind.Floor),
    TexturePreset("marble-white", "White marble", 100.0, 100.0, TexturePreset.Kind.Floor),
    TexturePreset("granite", "Granite", 100.0, 100.0, TexturePreset.Kind.Floor),
)

val WALL_PRESETS: List<TexturePreset> = listOf(
    TexturePreset("wood-beech", "Beech", 40.0, 80.0, TexturePreset.Kind.Wall),
    TexturePreset("wood-pale", "Pale wood", 40.0, 80.0, TexturePreset.Kind.Wall),
    TexturePreset("wood-dark", "Dark wood", 100.0, 50.0, TexturePreset.Kind.Wall),
    TexturePreset("wood-walnut", "Walnut", 40.0, 80.0, TexturePreset.Kind.Wall),
    TexturePreset("stone-rect", "Stone", 100.0, 100.0, TexturePreset.Kind.Wall),
    TexturePreset("tile-grey", "Grey tiles", 31.5, 31.5, TexturePreset.Kind.Wall),
    TexturePreset("brick-red", "Brick", 100.0, 100.0, TexturePreset.Kind.Wall),
    TexturePreset("plaster-white", "Plaster", 100.0, 100.0, TexturePreset.Kind.Wall),
)

data class PaintSwatch(val name: String, val hex: String)

val PAINT_PRESET_PALETTE: List<PaintSwatch> = listOf(
    PaintSwatch("White", "FFF5F5F5"),
    PaintSwatch("Cream", "FFF2E8D5"),
    PaintSwatch("Beige", "FFD9C7A7"),
    PaintSwatch("Greige", "FFC9C0B4"),
    PaintSwatch("Sage", "FFA8B5A0"),
    PaintSwatch("Olive", "FF8A8B6C"),
    PaintSwatch("Terracotta", "FFC0714F"),
    PaintSwatch("Brick", "FF9E4B3B"),
    PaintSwatch("Sky", "FF9FB8CC"),
    PaintSwatch("Navy", "FF35455E"),
    PaintSwatch("Slate", "FF64707D"),
    PaintSwatch("Charcoal", "FF3A3A3C"),
)

/** Glass-tint paint used when the wall sheet Glass chip is on. */
const val GLASS_SIDE_COLOR = "FF9FB8CC"
const val GLASS_SHININESS = 0.5

const val BORDER_WIDTH_CM = 35.0

val BORDER_PRESETS = mapOf(
    "tile" to "preset:tile-grey",
    "stone" to "preset:stone-rect",
    "walnut" to "preset:wood-walnut",
)

fun presetHandle(slug: String): String = "$PRESET_PREFIX$slug"

fun slugFromHandle(handle: String?): String? {
    if (handle.isNullOrBlank() || !handle.startsWith(PRESET_PREFIX)) return null
    return handle.removePrefix(PRESET_PREFIX)
}

fun findPreset(handle: String?, preferWall: Boolean = false): TexturePreset? {
    val slug = slugFromHandle(handle) ?: return null
    return if (preferWall) {
        WALL_PRESETS.find { it.slug == slug } ?: FLOOR_PRESETS.find { it.slug == slug }
    } else {
        FLOOR_PRESETS.find { it.slug == slug } ?: WALL_PRESETS.find { it.slug == slug }
    }
}

fun textureFromPreset(preset: TexturePreset): WallTexture =
    WallTexture(
        catalogID = presetHandle(preset.slug),
        name = preset.name,
        image = presetHandle(preset.slug),
        width = preset.width,
        height = preset.height,
    )

/** Assets-relative path for Coil (`file:///android_asset/…`). */
fun presetAssetPath(preset: TexturePreset): String {
    val folder = if (preset.kind == TexturePreset.Kind.Wall) "walls" else "floors"
    return "textures/$folder/${preset.slug}.jpg"
}

fun paintSwatchSelected(stored: String?, swatch: String): Boolean {
    if (stored.isNullOrBlank()) return false
    val a = stored.removePrefix("#").uppercase().takeLast(6)
    val b = swatch.removePrefix("#").uppercase().takeLast(6)
    return a.length == 6 && a == b
}

fun argbToCss(hex: String?): String? {
    if (hex.isNullOrBlank()) return null
    val raw = hex.removePrefix("#")
    return when (raw.length) {
        8 -> "#${raw.substring(2)}"
        6 -> "#$raw"
        else -> null
    }
}
