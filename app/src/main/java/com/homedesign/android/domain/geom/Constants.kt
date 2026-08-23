package com.homedesign.android.domain.geom

import kotlin.math.PI
import kotlin.math.max

const val defaultWallThicknessCM = 7.5
const val interiorThicknessCM = 10.0
const val exteriorThicknessCM = 20.0
const val defaultWallHeightCM = 250.0
const val levelFloorThicknessCM = 12.0
const val minDrawnWallCM = 1.0
const val minSplitSegmentCM = 2.0
const val minRectRoomSideCM = 10.0
const val minRoomResizeCM = 20.0
const val maxRoomResizeCM = 10000.0
const val joinEpsilonCM = 1.0
const val roomDetectEpsilonCM = 1.0
const val roomAreaEpsilonCM2 = 1.0
const val roomInsetDefaultCM = 3.75
const val roomCentroidMatchCM = 100.0
const val roomVertexFollowCM = 15.0
const val roomContainmentPasses = 8
const val roomContainmentMarginCM = 1.0
const val roomLabelMinM2 = 0.2
const val snapGridDefaultCM = 25.0
val angleSnapRad = PI / 15.0
const val angleSnapWallReferenceCM = 200.0
const val mitreVerticalSlope = 4000.0
const val mitreNearParallelRatio = 1.004

/** `2 * max(thickness)` — computed per join, not a fixed number. */
fun mitreLimit(thicknessA: Double, thicknessB: Double): Double =
    2.0 * max(thicknessA, thicknessB)

const val openingBindPerpCM = 30.0
val openingBindAngleRad = PI / 36.0
const val openingOvershootT = 0.05
const val openingMinWidthCM = 30.0
const val dimensionFaceGapCM = 35.0
const val furnitureSnapToWallCM = 25.0
const val furnitureFacingNearCM = 40.0
const val furnitureFacingMarginCM = 25.0
const val wallClearanceMarginCM = 2.0
const val tJunctionParallelReject = 0.05
const val alignMagnetPx = 10.0
const val hitEndpointPx = 28.0
const val hitWallCoarsePx = 32.0
const val hitWallEdgePx = 12.0
const val hitFurnitureHaloPx = 10.0
const val hitCurveHandlePx = 32.0
const val rotateSnapDeg = 15.0
const val rotateSnapWindowDeg = 3.0
const val viewRotationSoftSnapRad = 0.09
const val curveStraightEpsilonRad = 0.005
const val curveSampleDeg = 5.0
val filletTessellationRad = PI / 15.0
const val undoCoalesceS = 0.8
const val undoDepth = 30
const val autosaveDebounceS = 3.0
const val deleteToastS = 5.0
const val pasteOffsetCM = 40.0
const val traceOpacity = 0.35
const val cornerFilletDefaultCM = 8.0
const val gridMinApparentPx = 14.0
const val minDragToCommitBoxPx = 6.0
const val maxFurnitureDimensionCM = 100000.0
