package com.homedesign.android.domain.catalog

/** Picker filter / sort / recents. Ported from web `catalog/pickerLogic.ts`. */

const val RECENT_CHIP = "__recent__"
const val RECENT_CAP = 12

enum class PickerMode { Furniture, DoorsAndWindows }

enum class PickerSort { Name, SizeSmall, SizeLarge }

fun pickerEntries(
    mode: PickerMode,
    all: List<CatalogEntry> = CATALOG_ENTRIES,
): List<CatalogEntry> = when (mode) {
    PickerMode.DoorsAndWindows -> all.filter { it.doorOrWindow }
    PickerMode.Furniture -> all.filter { !it.doorOrWindow }
}

fun filterBySearch(entries: List<CatalogEntry>, query: String): List<CatalogEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return entries
    val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return entries
    return entries.filter { entry ->
        val hay = buildString {
            append(entry.name.lowercase()); append(' ')
            append(entry.category.lowercase()); append(' ')
            append(entry.id.lowercase()); append(' ')
            entry.creator?.lowercase()?.let { append(it); append(' ') }
            entry.library?.lowercase()?.let { append(it); append(' ') }
            entry.catalog.lowercase().let { append(it) }
        }
        tokens.all { token -> hay.contains(token) }
    }
}

fun categoriesFor(mode: PickerMode, entries: List<CatalogEntry>): List<String> {
    if (mode == PickerMode.DoorsAndWindows) return listOf("Doors and windows")
    val present = entries.map { it.category }.toSet()
    val preferred = FURNITURE_CATEGORIES.filter { it in present }
    val extras = present.filter { it !in FURNITURE_CATEGORIES && it != "Doors and windows" }.sorted()
    return preferred + extras
}

fun entriesInCategory(entries: List<CatalogEntry>, category: String?): List<CatalogEntry> {
    if (category.isNullOrEmpty() || category == RECENT_CHIP) return entries
    return entries.filter { it.category == category }
}

fun footprint(entry: CatalogEntry): Double = entry.width * entry.depth

fun sortEntries(entries: List<CatalogEntry>, sort: PickerSort): List<CatalogEntry> =
    when (sort) {
        PickerSort.Name -> entries.sortedBy { it.name.lowercase() }
        PickerSort.SizeSmall -> entries.sortedBy { footprint(it) }
        PickerSort.SizeLarge -> entries.sortedByDescending { footprint(it) }
    }

fun resolveRecent(recentIDs: List<String>, entries: List<CatalogEntry>): List<CatalogEntry> {
    val byId = entries.associateBy { it.id }
    return recentIDs.mapNotNull { byId[it] }
}

fun recordRecent(id: String, previous: List<String>, cap: Int = RECENT_CAP): List<String> =
    (listOf(id) + previous.filter { it != id }).take(cap)

fun visibleRows(
    mode: PickerMode,
    search: String,
    category: String?,
    sort: PickerSort,
    recentIDs: List<String>,
    all: List<CatalogEntry> = CATALOG_ENTRIES,
): List<CatalogEntry> {
    val pool = pickerEntries(mode, all)
    if (search.trim().isNotEmpty()) {
        return sortEntries(filterBySearch(pool, search), sort)
    }
    if (category == RECENT_CHIP) {
        return resolveRecent(recentIDs, pool)
    }
    if (!category.isNullOrEmpty()) {
        return sortEntries(entriesInCategory(pool, category), sort)
    }
    return sortEntries(pool, sort)
}
