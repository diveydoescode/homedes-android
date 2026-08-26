package com.homedesign.android.domain.catalog

/**
 * Built-in STRUCTURE catalog (iOS `StructureCatalog`): pillars, ceiling
 * beams, wall mirror, path, railing, rug. Procedural — no bundled mesh.
 * Placement special-cases give them structural semantics (floor→ceiling,
 * ceiling-hung, wall-hung).
 */
object StructureCatalog {
    const val catalogID = "structure"

    const val pillarRoundID = "structure#pillar-round"
    const val pillarSquareID = "structure#pillar-square"
    const val beamID = "structure#beam"
    const val mirrorID = "structure#mirror"
    const val pathID = "structure#garden-path"
    const val railingID = "structure#railing"
    const val rugID = "structure#rug"

    /** SH3D terracotta fill used when placing a rug. */
    const val rugColor = "FF8A5A44"

    fun isStructure(catalogID: String?): Boolean =
        catalogID?.startsWith("structure#") == true

    fun isFloorToCeiling(catalogID: String?): Boolean =
        catalogID == pillarRoundID || catalogID == pillarSquareID

    fun isCeilingHung(catalogID: String?): Boolean = catalogID == beamID

    fun isWallHung(catalogID: String?): Boolean = catalogID == mirrorID

    val pillarRound = entry(
        id = pillarRoundID,
        name = "Round pillar",
        width = 30.0,
        depth = 30.0,
        height = 250.0,
        elevation = 0.0,
    )

    val pillarSquare = entry(
        id = pillarSquareID,
        name = "Square pillar",
        width = 30.0,
        depth = 30.0,
        height = 250.0,
        elevation = 0.0,
    )

    val beam = entry(
        id = beamID,
        name = "Ceiling beam",
        width = 300.0,
        depth = 20.0,
        height = 18.0,
        elevation = null,
    )

    val mirror = entry(
        id = mirrorID,
        name = "Wall mirror",
        width = 80.0,
        depth = 4.0,
        height = 120.0,
        elevation = null,
    )

    val path = entry(
        id = pathID,
        name = "Garden path",
        width = 200.0,
        depth = 90.0,
        height = 3.0,
        elevation = 0.0,
    )

    val railing = entry(
        id = railingID,
        name = "Railing",
        width = 200.0,
        depth = 6.0,
        height = 100.0,
        elevation = 0.0,
    )

    val rug = entry(
        id = rugID,
        name = "Rug",
        width = 180.0,
        depth = 120.0,
        height = 2.0,
        elevation = 0.0,
    )

    val entries: List<CatalogEntry> = listOf(
        pillarRound,
        pillarSquare,
        beam,
        mirror,
        path,
        railing,
        rug,
    )

    fun byId(id: String): CatalogEntry? = entries.find { it.id == id }

    private fun entry(
        id: String,
        name: String,
        width: Double,
        depth: Double,
        height: Double,
        elevation: Double?,
    ): CatalogEntry = CatalogEntry(
        id = id,
        catalog = catalogID,
        library = null,
        name = name,
        category = "Structure",
        width = width,
        depth = depth,
        height = height,
        elevation = elevation,
        movable = true,
        doorOrWindow = false,
        icon = null,
    )
}
