package com.homedesign.android.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

/** Persist-only; Stage 1 does not interpret this shape. */
typealias HomeEnvironment = JsonElement?

/** Persist-only; Stage 1 does not interpret this shape. */
typealias Compass = JsonElement?

/** Persist-only; Stage 1 does not interpret this shape. */
typealias Camera = JsonElement

/** Persist-only; Stage 1 does not interpret this shape. */
typealias ShelfUnit = JsonElement

/** Persist-only; Stage 1 does not interpret this shape. */
typealias Roof = JsonElement?

/** Persist-only; Stage 1 does not interpret this shape. */
typealias PieceMaterial = JsonElement

/** Persist-only; Stage 1 does not interpret this shape. */
typealias LightSource = JsonElement

/** Persist-only; Stage 1 does not interpret this shape. */
typealias WindowDressing = JsonElement?

/** One span of a [WallCurveProfile]: dead-straight, or a circular arc. */
@Serializable
sealed interface WallSpan {
    @Serializable
    @SerialName("straight")
    data object Straight : WallSpan

    @Serializable
    @SerialName("arc")
    data class Arc(
        val bow: Double? = null,
        val extent: Double? = null,
    ) : WallSpan
}

/**
 * Breakpoint/envelope curvature. `breaks` are interior chord params
 * strictly in (0, 1); endpoints 0/1 are implicit so
 * `spans.length === breaks.length + 1`.
 */
@Serializable
data class WallCurveProfile(
    val breaks: List<Double>? = null,
    val spans: List<WallSpan> = emptyList(),
)

@Serializable
data class Outdoor(
    val grass: Boolean,
    val fence: Boolean,
    val marginCM: Double,
)

@Serializable
data class Home(
    val version: String? = null,
    val name: String? = null,
    val wallHeight: Double,
    val activeCamera: String? = null,
    val basePlanLocked: Boolean = false,
    val properties: Map<String, String> = emptyMap(),
    val furnitureVisibleProperties: List<String> = emptyList(),
    val environment: HomeEnvironment = null,
    val compass: Compass = null,
    val cameras: List<Camera> = emptyList(),
    val selectedLevelID: String? = null,
    val levels: List<Level> = emptyList(),
    val walls: List<Wall> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val furniture: List<HomePieceOfFurniture> = emptyList(),
    val doorsAndWindows: List<HomeDoorOrWindow> = emptyList(),
    val shelfUnits: List<ShelfUnit> = emptyList(),
    val dimensionLines: List<DimensionLine> = emptyList(),
    val labels: List<PlanLabel> = emptyList(),
    val roof: Roof = null,
    val outdoor: Outdoor? = null,
    /** Runtime only — never persist. Bump on geometry change. */
    @Transient val topologyVersion: Int = 0,
    /** Runtime only — never persist. Bump on place-only. */
    @Transient val furnitureRevision: Int = 0,
    /** Runtime only — never persist. Bump on paint/texture. */
    @Transient val styleVersion: Int = 0,
    /** Runtime only — never persist. handle → asset URL/path. */
    @Transient val extractedAssetURLs: Map<String, String> = emptyMap(),
)

@Serializable
data class Level(
    val id: String,
    val name: String? = null,
    val elevation: Double,
    val floorThickness: Double,
    val height: Double,
    val elevationIndex: Int,
    val visible: Boolean,
    val viewable: Boolean,
)

@Serializable
data class Wall(
    val id: String,
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double,
    val thickness: Double,
    val height: Double,
    val atStart: String? = null,
    val atEnd: String? = null,
    val level: String? = null,
    val arcExtent: Double? = null,
    val curveProfile: WallCurveProfile? = null,
    val heightAtEnd: Double? = null,
    val pattern: String? = null,
    val cornerFillet: Double? = null,
    val leftSideColor: String? = null,
    val rightSideColor: String? = null,
    val topColor: String? = null,
    val leftSideShininess: Double? = null,
    val rightSideShininess: Double? = null,
    val leftSidePattern: String? = null,
    val rightSidePattern: String? = null,
    val leftSideTexture: WallTexture? = null,
    val rightSideTexture: WallTexture? = null,
    val leftSideBaseboard: Baseboard? = null,
    val rightSideBaseboard: Baseboard? = null,
)

@Serializable
data class Point(
    val x: Double,
    val y: Double,
)

@Serializable
data class Room(
    val id: String,
    val points: List<Point>,
    val name: String? = null,
    val areaVisible: Boolean = true,
    val floorVisible: Boolean = true,
    val ceilingVisible: Boolean = true,
    val ceilingFlat: Boolean = false,
    val level: String? = null,
    val autoDetected: Boolean = false,
    val floorColor: String? = null,
    val ceilingColor: String? = null,
    val floorTexture: WallTexture? = null,
    val ceilingTexture: WallTexture? = null,
    val borderWidthCM: Double? = null,
    val borderColor: String? = null,
    val borderTexture: WallTexture? = null,
    val ceilingStyle: CeilingStyle? = null,
)

@Serializable
enum class CeilingStyle {
    @SerialName("flat") Flat,
    @SerialName("coffered") Coffered,
    @SerialName("stained_glass") StainedGlass,
    @SerialName("dropped") Dropped,
}

@Serializable
data class HomePieceOfFurniture(
    val id: String,
    val catalogID: String? = null,
    val name: String? = null,
    val creator: String? = null,
    val license: String? = null,
    val modelRef: String? = null,
    val iconRef: String? = null,
    val x: Double,
    val y: Double,
    val elevation: Double = 0.0,
    val angle: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val width: Double,
    val depth: Double,
    val height: Double,
    val widthInPlan: Double? = null,
    val depthInPlan: Double? = null,
    val heightInPlan: Double? = null,
    val color: String? = null,
    val movable: Boolean = true,
    val visible: Boolean = true,
    val level: String? = null,
    val groupID: String? = null,
    val staircaseCutOut: Boolean? = null,
    val modelMirrored: Boolean = false,
    val modelRotation: List<Double>? = null,
    val materialOverrides: Map<String, PieceMaterial>? = null,
    val lightPower: Double? = null,
    val lightSources: List<LightSource>? = null,
    val lightColor: String? = null,
)

@Serializable
data class Sash(
    val xAxis: Double,
    val yAxis: Double,
    val width: Double,
    val startAngle: Double,
    val endAngle: Double,
)

@Serializable
data class HomeDoorOrWindow(
    val piece: HomePieceOfFurniture,
    val wallThickness: Double? = null,
    val wallDistance: Double? = null,
    val wallWidth: Double? = null,
    val wallLeft: Double? = null,
    val wallHeight: Double? = null,
    val wallTop: Double? = null,
    val wallCutOutOnBothSides: Boolean = false,
    val widthDepthDeformable: Boolean = true,
    val cutoutShape: String? = null,
    val sashes: List<Sash> = emptyList(),
    val mirroredX: Boolean = false,
    val mirroredY: Boolean = false,
    val isOpen: Boolean = false,
    val dressing: WindowDressing = null,
)

@Serializable
data class DimensionLine(
    val id: String,
    val xStart: Double,
    val yStart: Double,
    val xEnd: Double,
    val yEnd: Double,
    val offset: Double,
    val level: String? = null,
)

@Serializable
data class PlanLabel(
    val id: String,
    val x: Double,
    val y: Double,
    val text: String,
    val color: String? = null,
    val angle: Double,
    val pitch: Double? = null,
    val level: String? = null,
)

@Serializable
data class WallTexture(
    val catalogID: String? = null,
    val name: String? = null,
    val creator: String? = null,
    val image: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val scale: Double? = null,
    val xOffset: Double? = null,
    val yOffset: Double? = null,
    val angle: Double? = null,
)

@Serializable
data class Baseboard(
    val thickness: Double? = null,
    val height: Double? = null,
    val color: String? = null,
    val texture: WallTexture? = null,
)

@Serializable
enum class UnitSystem(val value: String) {
    @SerialName("mm") Millimetre("mm"),
    @SerialName("cm") Metric("cm"),
    @SerialName("ft") Imperial("ft"),
}

@Serializable
data class ProjectMeta(
    val id: String,
    val name: String,
    val levelCount: Int,
    val rooms: Int,
    val floorAreaM2: Double,
    val createdAt: String,
    val updatedAt: String,
)

data class PlanClipboardPayload(
    val pieces: List<HomePieceOfFurniture>,
    val openings: List<HomeDoorOrWindow>,
)
