package com.homedesign.android.domain.geom

import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.model.HomePieceOfFurniture
import kotlin.math.abs

/**
 * Port of web `FurnitureSymbolClassifier.ts` — name keywords beat category;
 * L-sofa before sofa; lamp before table.
 */
enum class FurnitureSymbolKind {
    Bed,
    Nightstand,
    Dresser,
    Wardrobe,
    Sofa,
    SofaL,
    Armchair,
    Chair,
    Stool,
    Table,
    Desk,
    Toilet,
    Sink,
    Bathtub,
    Shower,
    Stove,
    Fridge,
    Bookshelf,
    Tv,
    Rug,
    Plant,
    Lamp,
    Chandelier,
    Mirror,
    Stairs,
    Pillar,
    Beam,
    Path,
    Railing,
    Generic,
}

enum class SymbolAxis { Width, Depth }

object FurnitureSymbolClassifier {
    private const val CACHE_LIMIT = 1024
    private val cache = LinkedHashMap<String, FurnitureSymbolKind>(64, 0.75f, true)

    fun classify(piece: HomePieceOfFurniture, entry: CatalogEntry? = null): FurnitureSymbolKind {
        val cacheKey =
            "${piece.name.orEmpty()}\u0001${piece.catalogID.orEmpty()}\u0001${entry?.name.orEmpty()}\u0001${entry?.category.orEmpty()}"
        cache[cacheKey]?.let { return it }
        val result = classifyUncached(piece, entry)
        if (cache.size >= CACHE_LIMIT) cache.clear()
        cache[cacheKey] = result
        return result
    }

    fun canonicalLongAxis(kind: FurnitureSymbolKind): SymbolAxis? =
        when (kind) {
            FurnitureSymbolKind.Sofa,
            FurnitureSymbolKind.Wardrobe,
            FurnitureSymbolKind.Dresser,
            FurnitureSymbolKind.Bookshelf,
            FurnitureSymbolKind.Desk,
            FurnitureSymbolKind.Bathtub,
            FurnitureSymbolKind.Sink,
            FurnitureSymbolKind.Tv,
            FurnitureSymbolKind.Mirror,
            FurnitureSymbolKind.Beam,
            FurnitureSymbolKind.Path,
            FurnitureSymbolKind.Railing,
            -> SymbolAxis.Width
            FurnitureSymbolKind.Bed,
            FurnitureSymbolKind.Toilet,
            -> SymbolAxis.Depth
            else -> null
        }

    fun needsQuarterTurn(
        kind: FurnitureSymbolKind,
        width: Double,
        depth: Double,
        toleranceCM: Double = 0.5,
    ): Boolean {
        val want = canonicalLongAxis(kind) ?: return false
        if (abs(width - depth) <= toleranceCM) return false
        val footprintIsWide = width > depth
        return footprintIsWide != (want == SymbolAxis.Width)
    }

    private fun classifyUncached(piece: HomePieceOfFurniture, entry: CatalogEntry?): FurnitureSymbolKind {
        val name = piece.name.orEmpty()
        val entryName = entry?.name.orEmpty()
        val catalogId = piece.catalogID.orEmpty()
        val text = " $name $entryName $catalogId ".lowercase()
        val category = (entry?.category.orEmpty()).lowercase()

        structureKind(catalogId)?.let { return it }

        if (any(text, listOf("pillar", "column"))) return FurnitureSymbolKind.Pillar
        if (any(text, listOf("railing", "banister", "handrail"))) return FurnitureSymbolKind.Railing
        if (any(text, listOf("garden path", "garden-path"))) return FurnitureSymbolKind.Path
        if (any(text, listOf("ceiling beam"))) return FurnitureSymbolKind.Beam

        if (any(text, listOf("toilet", "lavatory", "water closet", " wc "))) return FurnitureSymbolKind.Toilet
        if (any(text, listOf("bathtub", "bath tub", "jacuzzi", "whirlpool"))) return FurnitureSymbolKind.Bathtub
        if (any(text, listOf("shower"))) return FurnitureSymbolKind.Shower
        if (any(text, listOf("washbasin", "wash basin", "basin", "sink", "vanity", "lavabo"))) {
            return FurnitureSymbolKind.Sink
        }

        if (any(text, listOf("refrigerator", "fridge", "freezer"))) return FurnitureSymbolKind.Fridge
        if (any(text, listOf("stove", "cooktop", "cooker", "hob", "range", "oven", "hotplate"))) {
            return FurnitureSymbolKind.Stove
        }

        if (any(text, listOf("nightstand", "bedside", "night stand"))) return FurnitureSymbolKind.Nightstand
        if (any(text, listOf("bed", "cot", "crib", "bunk", "mattress"))) return FurnitureSymbolKind.Bed
        if (any(text, listOf("dresser", "chest of drawers", "drawers", "commode", "tallboy"))) {
            return FurnitureSymbolKind.Dresser
        }
        if (any(text, listOf("wardrobe", "closet", "armoire"))) return FurnitureSymbolKind.Wardrobe

        if (
            any(
                text,
                listOf(
                    "corner sofa",
                    "sectional",
                    "l-shaped sofa",
                    "l shaped sofa",
                    "l-sofa",
                    "chaise",
                    "modular sofa",
                ),
            )
        ) {
            return FurnitureSymbolKind.SofaL
        }
        if (any(text, listOf("sofa", "couch", "settee", "loveseat", "love seat", "divan"))) {
            return FurnitureSymbolKind.Sofa
        }
        if (any(text, listOf("armchair", "recliner", "lounge chair", "wingback"))) {
            return FurnitureSymbolKind.Armchair
        }
        if (any(text, listOf("stool", "ottoman", "pouffe", "pouf"))) return FurnitureSymbolKind.Stool
        if (any(text, listOf("chair", "seat", "bench", "pew"))) return FurnitureSymbolKind.Chair

        if (any(text, listOf("mirror", "looking glass"))) return FurnitureSymbolKind.Mirror
        if (any(text, listOf("chandelier", "pendant", "ceiling light", "ceiling lamp", "ceiling fan"))) {
            return FurnitureSymbolKind.Chandelier
        }
        if (any(text, listOf("lamp", "sconce", "lantern"))) return FurnitureSymbolKind.Lamp

        if (any(text, listOf("desk", "workstation"))) return FurnitureSymbolKind.Desk
        if (any(text, listOf("table", "console", "nightstand"))) return FurnitureSymbolKind.Table

        if (
            any(
                text,
                listOf("bookshelf", "bookcase", "shelf", "shelving", "cabinet", "cupboard", "sideboard"),
            )
        ) {
            return FurnitureSymbolKind.Bookshelf
        }
        if (any(text, listOf("television", " tv", "tv ", "monitor", "screen"))) return FurnitureSymbolKind.Tv
        if (any(text, listOf("rug", "carpet", "mat ", " mat"))) return FurnitureSymbolKind.Rug
        if (any(text, listOf("plant", "tree", "flower", "shrub", "fern", "palm"))) {
            return FurnitureSymbolKind.Plant
        }
        if (any(text, listOf("light"))) return FurnitureSymbolKind.Lamp
        if (any(text, listOf("stair", "staircase", "steps", "escalier"))) return FurnitureSymbolKind.Stairs
        if (any(text, listOf("beam"))) return FurnitureSymbolKind.Beam
        if (any(text, listOf(" path", "path "))) return FurnitureSymbolKind.Path

        if (category.contains("staircase")) return FurnitureSymbolKind.Stairs
        if (category.contains("light")) return FurnitureSymbolKind.Lamp
        return FurnitureSymbolKind.Generic
    }

    private fun structureKind(catalogId: String): FurnitureSymbolKind? {
        if (!catalogId.startsWith("structure#")) return null
        val id = catalogId.lowercase()
        return when {
            id.contains("pillar") -> FurnitureSymbolKind.Pillar
            id.contains("beam") -> FurnitureSymbolKind.Beam
            id.contains("path") -> FurnitureSymbolKind.Path
            id.contains("railing") -> FurnitureSymbolKind.Railing
            id.contains("rug") -> FurnitureSymbolKind.Rug
            id.contains("mirror") -> FurnitureSymbolKind.Mirror
            else -> FurnitureSymbolKind.Generic
        }
    }

    private fun any(text: String, keys: List<String>): Boolean = keys.any { text.contains(it) }
}
