package com.homedesign.android.domain.catalog

/**
 * Subset of the iOS CatalogRegistry entry used by the picker / replace / opening model-swap.
 */
data class CatalogEntry(
    val id: String,
    val catalog: String,
    val library: String? = null,
    val name: String,
    /** Picker chip category (Bedroom / Seating / …). */
    val category: String,
    val width: Double,
    val depth: Double,
    val height: Double,
    val elevation: Double? = null,
    val movable: Boolean = true,
    val doorOrWindow: Boolean = false,
    val creator: String? = null,
    /** Optional asset path used as the row thumbnail (symbol art). */
    val icon: String? = null,
)
