package com.homedesign.android.domain.editor

import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.HomeEnvironment
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Level
import com.homedesign.android.domain.model.PlanLabel
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.ShelfUnit
import com.homedesign.android.domain.model.Wall
import com.homedesign.android.domain.model.DefaultHomeJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** User-editable Home fields — not revision counters. */
data class HomeSnapshot(
    val wallHeight: Double,
    val walls: List<Wall>,
    val rooms: List<Room>,
    val furniture: List<HomePieceOfFurniture>,
    val doorsAndWindows: List<HomeDoorOrWindow>,
    val shelfUnits: List<ShelfUnit>,
    val levels: List<Level>,
    val selectedLevelID: String?,
    val dimensionLines: List<DimensionLine>,
    val labels: List<PlanLabel>,
    val environment: HomeEnvironment,
    val extractedAssetURLs: Map<String, String>,
)

private val cloneJson = Json(DefaultHomeJson) {
    encodeDefaults = true
}

private inline fun <reified T> deepClone(value: T): T {
    val encoded = cloneJson.encodeToString(value)
    return cloneJson.decodeFromString(encoded)
}

private fun cloneElements(list: List<JsonElement>): List<JsonElement> =
    list.map { cloneJson.parseToJsonElement(cloneJson.encodeToString(JsonElement.serializer(), it)) }

private fun cloneJsonElement(value: JsonElement?): JsonElement? =
    value?.let { cloneJson.parseToJsonElement(cloneJson.encodeToString(JsonElement.serializer(), it)) }

fun takeSnapshot(home: Home): HomeSnapshot = HomeSnapshot(
    wallHeight = home.wallHeight,
    walls = deepClone(home.walls),
    rooms = deepClone(home.rooms),
    furniture = deepClone(home.furniture),
    doorsAndWindows = deepClone(home.doorsAndWindows),
    shelfUnits = cloneElements(home.shelfUnits),
    levels = deepClone(home.levels),
    selectedLevelID = home.selectedLevelID,
    dimensionLines = deepClone(home.dimensionLines),
    labels = deepClone(home.labels),
    environment = cloneJsonElement(home.environment),
    extractedAssetURLs = home.extractedAssetURLs.toMap(),
)

/** Restore snapshot fields and bump topology so observers fire once. */
fun applySnapshot(home: Home, snapshot: HomeSnapshot): Home = home.copy(
    wallHeight = snapshot.wallHeight,
    walls = deepClone(snapshot.walls),
    rooms = deepClone(snapshot.rooms),
    furniture = deepClone(snapshot.furniture),
    doorsAndWindows = deepClone(snapshot.doorsAndWindows),
    shelfUnits = cloneElements(snapshot.shelfUnits),
    levels = deepClone(snapshot.levels),
    selectedLevelID = snapshot.selectedLevelID,
    dimensionLines = deepClone(snapshot.dimensionLines),
    labels = deepClone(snapshot.labels),
    environment = cloneJsonElement(snapshot.environment),
    extractedAssetURLs = snapshot.extractedAssetURLs.toMap(),
    topologyVersion = home.topologyVersion + 1,
)

fun wallsOnLevel(home: Home): List<Wall> {
    val level = home.selectedLevelID
    return home.walls.filter { it.level == level }
}

fun roomsOnLevel(home: Home): List<Room> {
    val level = home.selectedLevelID
    return home.rooms.filter { it.level == level }
}
