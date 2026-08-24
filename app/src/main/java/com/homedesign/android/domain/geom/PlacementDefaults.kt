package com.homedesign.android.domain.geom

/** Port of web `PlacementDefaults.ts` — elevation rules for catalog placement. */
enum class FurnitureMount { Floor, Ceiling, Wall }

object PlacementDefaults {
    const val wallMountElevationCM = 170.0

    private val WALL_KEYWORDS = listOf(
        "wall light", "wall lamp", "sconce", "applique", "wall-mounted",
        "wall spot", "painting light", "mirror",
    )
    private val CEILING_KEYWORDS = listOf(
        "ceiling", "chandelier", "pendant", "hanging", "lustre",
        "spot", "fluorescent", "track light", "neon",
    )

    fun mount(entryName: String?): FurnitureMount {
        val n = entryName.orEmpty().lowercase()
        if (WALL_KEYWORDS.any { n.contains(it) }) return FurnitureMount.Wall
        if (CEILING_KEYWORDS.any { n.contains(it) }) return FurnitureMount.Ceiling
        return FurnitureMount.Floor
    }

    fun defaultElevation(entryName: String?, pieceHeight: Double, levelHeight: Double): Double =
        when (mount(entryName)) {
            FurnitureMount.Floor -> 0.0
            FurnitureMount.Ceiling -> maxOf(0.0, levelHeight - pieceHeight)
            FurnitureMount.Wall -> maxOf(
                0.0,
                minOf(wallMountElevationCM, levelHeight - pieceHeight),
            )
        }
}
