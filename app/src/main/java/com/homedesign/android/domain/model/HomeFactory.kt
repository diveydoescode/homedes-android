package com.homedesign.android.domain.model

object HomeFactory {
    const val DEFAULT_WALL_HEIGHT_CM = 250.0
    const val DEFAULT_FLOOR_THICKNESS_CM = 12.0
    const val SYNTH_LEVEL_ID = "level0"

    private fun synthesisedLevel(): Level = Level(
        id = SYNTH_LEVEL_ID,
        elevation = 0.0,
        floorThickness = DEFAULT_FLOOR_THICKNESS_CM,
        height = DEFAULT_WALL_HEIGHT_CM,
        elevationIndex = 0,
        visible = true,
        viewable = true,
    )

    fun emptyHome(name: String): Home = Home(
        name = name,
        wallHeight = DEFAULT_WALL_HEIGHT_CM,
        basePlanLocked = false,
        properties = emptyMap(),
        furnitureVisibleProperties = emptyList(),
        cameras = emptyList(),
        selectedLevelID = SYNTH_LEVEL_ID,
        levels = listOf(synthesisedLevel()),
        walls = emptyList(),
        rooms = emptyList(),
        furniture = emptyList(),
        doorsAndWindows = emptyList(),
        shelfUnits = emptyList(),
        dimensionLines = emptyList(),
        labels = emptyList(),
        topologyVersion = 0,
        furnitureRevision = 0,
        styleVersion = 0,
        extractedAssetURLs = emptyMap(),
    )
}
