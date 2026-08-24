package com.homedesign.android.domain.catalog

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogFile(
    val id: String? = null,
    val name: String? = null,
    val entries: List<CatalogJsonEntry> = emptyList(),
)

@Serializable
private data class CatalogJsonEntry(
    val id: String,
    val catalog: String = "generic",
    val library: String? = null,
    val name: String,
    val category: String,
    val width: Double,
    val depth: Double,
    val height: Double,
    val elevation: Double? = null,
    val movable: Boolean = true,
    @SerialName("doorOrWindow") val doorOrWindow: Boolean = false,
    val creator: String? = null,
    val icon: String? = null,
)

private val catalogJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** SVG thumbs shipped in assets for a subset of common pieces. */
private val ICON_BY_ID: Map<String, String> = mapOf(
    "Blend Swap CC-0#bed1" to "svg/bed.svg",
    "Blend Swap CC-0#sofa2" to "svg/sofa.svg",
    "Blend Swap CC-0#L_shaped_sofa" to "svg/sofa_l.svg",
    "Blend Swap CC-0#armchair2" to "svg/armchair.svg",
    "Blend Swap CC-0#chair" to "svg/chair.svg",
    "Blend Swap CC-0#stool2" to "svg/stool.svg",
    "Blend Swap CC-0#table" to "svg/table.svg",
    "Blend Swap CC-0#lbDesk" to "svg/desk.svg",
    "Blend Swap CC-BY#kitchenSink" to "svg/sink.svg",
    "Blend Swap CC-0#toiletsUnit" to "svg/toilet.svg",
    "Blend Swap CC-BY#bath_jay_hardy" to "svg/bath.svg",
    "Blend Swap CC-0#showerStall" to "svg/shower.svg",
    "Blend Swap CC-0#bookcase" to "svg/bookcase.svg",
    "Blend Swap CC-BY#wardrobe" to "svg/wardrobe.svg",
    "Blend Swap CC-0#wardrobeWithSlidingDoors" to "svg/wardrobe.svg",
    "Blend Swap CC-0#decorativePlant" to "svg/plant.svg",
    "Scopia#door" to "svg/door.svg",
    "OlaKristianHoff#window_double_2x3_frame_sill" to "svg/window.svg",
    "SeberRider#doubleFrenchWindow2" to "svg/window.svg",
)

private val ICON_BY_CATEGORY_HINT: List<Pair<Regex, String>> = listOf(
    Regex("bed", RegexOption.IGNORE_CASE) to "svg/bed.svg",
    Regex("sofa|couch", RegexOption.IGNORE_CASE) to "svg/sofa.svg",
    Regex("armchair", RegexOption.IGNORE_CASE) to "svg/armchair.svg",
    Regex("\\bchair\\b", RegexOption.IGNORE_CASE) to "svg/chair.svg",
    Regex("stool|ottoman", RegexOption.IGNORE_CASE) to "svg/stool.svg",
    Regex("desk", RegexOption.IGNORE_CASE) to "svg/desk.svg",
    Regex("table", RegexOption.IGNORE_CASE) to "svg/table.svg",
    Regex("wardrobe|closet|cupboard", RegexOption.IGNORE_CASE) to "svg/wardrobe.svg",
    Regex("bookcase|bookshelf", RegexOption.IGNORE_CASE) to "svg/bookcase.svg",
    Regex("toilet|wc", RegexOption.IGNORE_CASE) to "svg/toilet.svg",
    Regex("bath|tub", RegexOption.IGNORE_CASE) to "svg/bath.svg",
    Regex("shower", RegexOption.IGNORE_CASE) to "svg/shower.svg",
    Regex("sink|basin|vanity", RegexOption.IGNORE_CASE) to "svg/sink.svg",
    Regex("plant", RegexOption.IGNORE_CASE) to "svg/plant.svg",
    Regex("\\bdoor\\b", RegexOption.IGNORE_CASE) to "svg/door.svg",
    Regex("window", RegexOption.IGNORE_CASE) to "svg/window.svg",
    Regex("stove|cooker|hob", RegexOption.IGNORE_CASE) to "svg/stove.svg",
)

object CatalogLoader {
    const val ASSET_PATH = "catalog/generic.json"

    fun loadFromAssets(context: Context): List<CatalogEntry>? {
        return try {
            context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                val file = catalogJson.decodeFromString(CatalogFile.serializer(), reader.readText())
                if (file.entries.isEmpty()) null
                else file.entries.map { it.toEntry() }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun installFromAssets(context: Context): Int {
        val loaded = loadFromAssets(context) ?: return 0
        installCatalogEntries(loaded)
        return loaded.size
    }
}

private fun CatalogJsonEntry.toEntry(): CatalogEntry {
    val mappedCategory = remapCategory(category, doorOrWindow)
    val icon = resolveIcon(id, name, ICON_BY_ID[id])
    return CatalogEntry(
        id = id,
        catalog = catalog,
        library = library,
        name = name,
        category = mappedCategory,
        width = width,
        depth = depth,
        height = height,
        elevation = elevation,
        movable = movable,
        doorOrWindow = doorOrWindow,
        creator = creator,
        icon = icon,
    )
}

private fun resolveIcon(id: String, name: String, preferred: String?): String? {
    preferred?.let { return it }
    for ((re, path) in ICON_BY_CATEGORY_HINT) {
        if (re.containsMatchIn(name) || re.containsMatchIn(id)) return path
    }
    return null
}

/** Map SH3D library categories onto picker chips where we already have chips. */
internal fun remapCategory(raw: String, doorOrWindow: Boolean): String {
    if (doorOrWindow) return "Doors and windows"
    return when (raw.trim().lowercase()) {
        "bathroom", "bath" -> "Bath"
        "living room", "living", "seating" -> "Seating"
        "lights", "lighting", "light" -> "Lighting"
        "bedroom" -> "Bedroom"
        "kitchen" -> "Kitchen"
        "office" -> "Office"
        "exterior" -> "Exterior"
        "miscellaneous" -> "Miscellaneous"
        "staircases", "staircase" -> "Staircases"
        "vehicles" -> "Vehicles"
        "characters" -> "Characters"
        "eteks" -> "eTeks"
        "tables", "table" -> "Tables"
        "storage" -> "Storage"
        "decor", "decoration" -> "Decor"
        "doors and windows", "door", "window" -> "Doors and windows"
        else -> raw.trim().ifEmpty { "Miscellaneous" }
    }
}
