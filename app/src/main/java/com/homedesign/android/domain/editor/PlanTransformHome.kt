package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.PlanAxis
import com.homedesign.android.domain.geom.PlanRotation
import com.homedesign.android.domain.geom.PlanTransform
import com.homedesign.android.domain.model.Home

/** Active-floor wrappers around [PlanTransform] (web `state/planTransform.ts`). */

private fun payloadOf(home: Home): PlanTransform.Payload {
    val level = home.selectedLevelID
    return PlanTransform.Payload(
        walls = wallsOnLevel(home),
        rooms = roomsOnLevel(home),
        furniture = home.furniture.filter { it.level == level },
        openings = home.doorsAndWindows.filter { it.piece.level == level },
        dimensionLines = home.dimensionLines.filter { it.level == level },
        labels = home.labels.filter { it.level == level },
        compass = home.compass,
    )
}

private fun applyPayload(home: Home, out: PlanTransform.Payload): Home {
    val level = home.selectedLevelID
    return home.copy(
        walls = home.walls.filter { it.level != level } + out.walls,
        rooms = home.rooms.filter { it.level != level } + out.rooms,
        furniture = home.furniture.filter { it.level != level } + out.furniture,
        doorsAndWindows = home.doorsAndWindows.filter { it.piece.level != level } + out.openings,
        dimensionLines = home.dimensionLines.filter { it.level != level } + out.dimensionLines,
        labels = home.labels.filter { it.level != level } + out.labels,
        compass = out.compass,
        topologyVersion = home.topologyVersion + 1,
    )
}

fun applyMirrorPlan(home: Home, axis: PlanAxis): Home {
    val payload = payloadOf(home)
    val pivot = PlanTransform.center(payload) ?: return home
    return applyPayload(home, PlanTransform.mirror(payload, axis, pivot))
}

fun applyRotatePlan(home: Home, rotation: PlanRotation): Home {
    val payload = payloadOf(home)
    val pivot = PlanTransform.center(payload) ?: return home
    return applyPayload(home, PlanTransform.rotate(payload, rotation, pivot))
}

/** Furniture-only mirror about the selection centre (Swift F12). */
fun applyMirrorSelection(home: Home, ids: List<String>, axis: PlanAxis): Home {
    val pieces = home.furniture.filter { it.id in ids }
    if (pieces.isEmpty()) return home
    val sub = PlanTransform.Payload(furniture = pieces)
    val pivot = PlanTransform.center(sub) ?: return home
    val out = PlanTransform.mirror(sub, axis, pivot)
    val byId = out.furniture.associateBy { it.id }
    return home.copy(
        furniture = home.furniture.map { byId[it.id] ?: it },
        topologyVersion = home.topologyVersion + 1,
    )
}
