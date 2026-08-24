package com.homedesign.android.domain.geom

import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Level
import java.util.UUID

/**
 * Storey mutations — iOS `LevelMutation` / SH3D "add level" semantics.
 * New slab sits on the previous storey's ceiling:
 * elevation = max(elevation + height) + default floor thickness.
 */
object LevelMutation {
    fun addLevelOnTop(
        levels: List<Level>,
        defaultHeight: Double,
        id: String = UUID.randomUUID().toString(),
    ): List<Level> {
        val stackTop = levels.maxOfOrNull { it.elevation + it.height }
        val elevation = if (stackTop != null) {
            stackTop + HomeFactory.DEFAULT_FLOOR_THICKNESS_CM
        } else {
            0.0
        }
        val nextIndex = (levels.maxOfOrNull { it.elevationIndex } ?: -1) + 1
        val level = Level(
            id = id,
            name = "Floor ${levels.size + 1}",
            elevation = elevation,
            floorThickness = HomeFactory.DEFAULT_FLOOR_THICKNESS_CM,
            height = defaultHeight,
            elevationIndex = nextIndex,
            visible = true,
            viewable = true,
        )
        return levels + level
    }

    /** Visible levels, highest first (elevator menu order). */
    fun orderedVisible(levels: List<Level>): List<Level> =
        levels
            .filter { it.visible }
            .sortedWith(
                compareByDescending<Level> { it.elevationIndex }
                    .thenByDescending { it.elevation },
            )

    /** Elevator-style short label (G / B / A / 1…). */
    fun elevatorLabel(level: Level): String {
        val name = level.name?.lowercase().orEmpty()
        when {
            name.contains("ground") -> return "G"
            name.contains("basement") || name.contains("cellar") -> return "B"
            name.contains("attic") -> return "A"
        }
        val raw = level.name
        if (raw != null) {
            val digits = raw.takeWhile { it.isDigit() }
            if (digits.isNotEmpty()) return digits
        }
        return "${level.elevationIndex + 1}"
    }

    fun menuLabel(level: Level): String {
        val short = elevatorLabel(level)
        val full = level.name?.takeIf { it.isNotBlank() } ?: "Floor ${level.elevationIndex + 1}"
        return "$short — $full"
    }
}
