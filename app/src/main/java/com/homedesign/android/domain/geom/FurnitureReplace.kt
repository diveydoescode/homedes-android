package com.homedesign.android.domain.geom

import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.model.HomePieceOfFurniture

const val PLACEHOLDER_CATALOG_ID = "homedesign#placeholder"

/**
 * Ported from web `FurnitureReplace.ts` / iOS FurnitureReplace.
 * Keeps pose (x,y,angle) and identity; adopts catalog id / size / height; clears look overrides.
 */
object FurnitureReplace {
    val placeholderCatalogID: String = PLACEHOLDER_CATALOG_ID

    fun isPlaceholder(piece: HomePieceOfFurniture): Boolean =
        piece.catalogID == PLACEHOLDER_CATALOG_ID

    fun replace(piece: HomePieceOfFurniture, entry: CatalogEntry): HomePieceOfFurniture =
        piece.copy(
            catalogID = entry.id,
            name = entry.name,
            width = entry.width,
            depth = entry.depth,
            height = entry.height,
            heightInPlan = null,
            movable = entry.movable,
            color = null,
            materialOverrides = null,
            modelRef = null,
            iconRef = null,
            modelRotation = null,
            modelMirrored = false,
            lightPower = if (entry.category == "Lights" || entry.category == "Lighting") 0.5 else null,
            lightSources = null,
            lightColor = null,
            staircaseCutOut = if (entry.category == "Staircases") true else null,
        )

    fun similarPlaceholderIDs(
        piece: HomePieceOfFurniture,
        furniture: List<HomePieceOfFurniture>,
    ): List<String> {
        if (!isPlaceholder(piece)) return emptyList()
        val label = (piece.name ?: "").trim().lowercase()
        if (label.isEmpty()) return emptyList()
        return furniture.mapNotNull { other ->
            if (other.id == piece.id) return@mapNotNull null
            if (!isPlaceholder(other)) return@mapNotNull null
            if ((other.name ?: "").trim().lowercase() != label) return@mapNotNull null
            other.id
        }
    }
}
