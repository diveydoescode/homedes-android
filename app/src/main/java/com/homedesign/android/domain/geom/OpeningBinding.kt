package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeDoorOrWindow
import com.homedesign.android.domain.model.Wall
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class OpeningBind(
    val openingID: String,
    val wallID: String,
    val tStart: Double,
    val tEnd: Double,
)

private const val JUNCTION_TOLERANCE_CM = 1.0
private const val OVERHANG_EPSILON = 1e-6

object OpeningBinding {
    val perpendicularToleranceCM: Double = openingBindPerpCM
    val angleToleranceRadians: Double = openingBindAngleRad
    val overshootT: Double = openingOvershootT

    fun bind(
        walls: List<Wall>,
        openings: List<HomeDoorOrWindow>,
        perpendicularToleranceCM: Double = openingBindPerpCM,
        angleToleranceRadians: Double = openingBindAngleRad,
    ): List<OpeningBind> {
        if (walls.isEmpty() || openings.isEmpty()) return emptyList()
        val sinThreshold = sin(angleToleranceRadians)
        val result = mutableListOf<OpeningBind>()
        for (opening in openings) {
            result.addAll(bindSingle(opening, walls, perpendicularToleranceCM, sinThreshold))
        }
        return result
    }
}

private fun bindSingle(
    opening: HomeDoorOrWindow,
    walls: List<Wall>,
    perpendicularToleranceCM: Double,
    sinThreshold: Double,
): List<OpeningBind> {
    val piece = opening.piece
    val center = vec(piece.x, piece.y)
    val halfWidth = piece.width / 2.0
    val openingAngle = piece.angle
    val axis = vec(cos(openingAngle), sin(openingAngle))
    val leftEdge = sub(center, scale(axis, halfWidth))
    val rightEdge = add(center, scale(axis, halfWidth))

    var bestWall: Wall? = null
    var bestTLeftRaw = 0.0
    var bestTRightRaw = 0.0
    var bestTStart = 0.0
    var bestTEnd = 0.0
    var bestPerpDist = Double.POSITIVE_INFINITY

    for (wall in walls) {
        val a = vec(wall.startX, wall.startY)
        val b = vec(wall.endX, wall.endY)
        val ab = sub(b, a)
        val abLenSq = lengthSq(ab)
        if (abLenSq <= 1e-9) continue

        val wallAngle = atan2(ab.y, ab.x)
        val sinDelta = abs(sin(openingAngle - wallAngle))
        if (sinDelta >= sinThreshold) continue

        val tCenter = dot(sub(center, a), ab) / abLenSq
        if (tCenter < -openingOvershootT || tCenter > 1 + openingOvershootT) continue

        val clampedT = max(0.0, min(1.0, tCenter))
        val projection = add(a, scale(ab, clampedT))
        val perpDist = length(sub(center, projection))
        if (perpDist >= perpendicularToleranceCM) continue

        if (perpDist < bestPerpDist) {
            val tLeft = dot(sub(leftEdge, a), ab) / abLenSq
            val tRight = dot(sub(rightEdge, a), ab) / abLenSq
            bestPerpDist = perpDist
            bestWall = wall
            bestTLeftRaw = tLeft
            bestTRightRaw = tRight
            bestTStart = max(0.0, min(1.0, min(tLeft, tRight)))
            bestTEnd = max(0.0, min(1.0, max(tLeft, tRight)))
        }
    }

    val wall = bestWall ?: return emptyList()
    val bindings = mutableListOf(
        OpeningBind(
            openingID = piece.id,
            wallID = wall.id,
            tStart = bestTStart,
            tEnd = bestTEnd,
        ),
    )

    val tLo = min(bestTLeftRaw, bestTRightRaw)
    val tHi = max(bestTLeftRaw, bestTRightRaw)
    overhangNeighbourBinding(
        opening,
        wall,
        -tLo,
        tHi - 1,
        center,
        leftEdge,
        rightEdge,
        walls,
        perpendicularToleranceCM,
        sinThreshold,
    )?.let { bindings.add(it) }
    return bindings
}

private fun overhangNeighbourBinding(
    opening: HomeDoorOrWindow,
    bestWall: Wall,
    overhangPastStart: Double,
    overhangPastEnd: Double,
    center: Vec2,
    leftEdge: Vec2,
    rightEdge: Vec2,
    walls: List<Wall>,
    perpendicularToleranceCM: Double,
    sinThreshold: Double,
): OpeningBind? {
    val overhangsStart = overhangPastStart > OVERHANG_EPSILON
    val overhangsEnd = overhangPastEnd > OVERHANG_EPSILON
    if (!overhangsStart && !overhangsEnd) return null

    val overhungEndpoint =
        if (overhangPastStart >= overhangPastEnd) {
            vec(bestWall.startX, bestWall.startY)
        } else {
            vec(bestWall.endX, bestWall.endY)
        }

    val openingAngle = opening.piece.angle
    val tolSq = JUNCTION_TOLERANCE_CM * JUNCTION_TOLERANCE_CM

    for (wall in walls) {
        if (wall.id == bestWall.id) continue
        val a = vec(wall.startX, wall.startY)
        val b = vec(wall.endX, wall.endY)
        val ab = sub(b, a)
        val abLenSq = lengthSq(ab)
        if (abLenSq <= 1e-9) continue

        val dStart = sub(a, overhungEndpoint)
        val dEnd = sub(b, overhungEndpoint)
        if (dot(dStart, dStart) > tolSq && dot(dEnd, dEnd) > tolSq) continue

        val wallAngle = atan2(ab.y, ab.x)
        if (abs(sin(openingAngle - wallAngle)) >= sinThreshold) continue

        val tCenter = dot(sub(center, a), ab) / abLenSq
        if (tCenter < -openingOvershootT || tCenter > 1 + openingOvershootT) continue
        val clampedCenterT = max(0.0, min(1.0, tCenter))
        val perpDist = length(sub(center, add(a, scale(ab, clampedCenterT))))
        if (perpDist >= perpendicularToleranceCM) continue

        val tLeft = dot(sub(leftEdge, a), ab) / abLenSq
        val tRight = dot(sub(rightEdge, a), ab) / abLenSq
        val tStart = max(0.0, min(1.0, min(tLeft, tRight)))
        val tEnd = max(0.0, min(1.0, max(tLeft, tRight)))
        if (tEnd - tStart <= OVERHANG_EPSILON) continue

        return OpeningBind(
            openingID = opening.piece.id,
            wallID = wall.id,
            tStart = tStart,
            tEnd = tEnd,
        )
    }
    return null
}

private var memoTopology: Int? = null
private var memoWalls: List<Wall>? = null
private var memoOpenings: List<HomeDoorOrWindow>? = null
private var memoResult: List<OpeningBind>? = null

fun bindForHome(home: Home): List<OpeningBind> {
    if (
        memoTopology == home.topologyVersion &&
        memoWalls === home.walls &&
        memoOpenings === home.doorsAndWindows &&
        memoResult != null
    ) {
        return memoResult!!
    }
    val result = OpeningBinding.bind(home.walls, home.doorsAndWindows)
    memoTopology = home.topologyVersion
    memoWalls = home.walls
    memoOpenings = home.doorsAndWindows
    memoResult = result
    return result
}

fun bindingsForWall(bindings: List<OpeningBind>, wallID: String): List<OpeningBind> =
    bindings.filter { it.wallID == wallID }

fun primaryBindingForOpening(bindings: List<OpeningBind>, openingID: String): OpeningBind? =
    bindings.find { it.openingID == openingID }
