package com.homedesign.android.domain.editor

import com.homedesign.android.domain.geom.undoCoalesceS
import com.homedesign.android.domain.geom.undoDepth
import com.homedesign.android.domain.model.Home

fun interface UndoClock {
    fun now(): Long
}

/**
 * Snapshot undo/redo. Depth 30, coalesce 0.8 s, current state always on top,
 * ignore counter bumps while restoring.
 */
class UndoStack(
    private val clock: UndoClock = UndoClock { System.currentTimeMillis() },
    private val maxDepth: Int = undoDepth,
    coalesceS: Double = undoCoalesceS,
) {
    private val undoStack = mutableListOf<HomeSnapshot>()
    private val redoStack = mutableListOf<HomeSnapshot>()
    private var isRestoring = false
    private var lastPush = Long.MIN_VALUE
    private val coalesceMs = (coalesceS * 1000).toLong()

    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val restoring: Boolean get() = isRestoring
    /** Snapshot count including the current state on top. */
    val depth: Int get() = undoStack.size

    fun reset(home: Home) {
        undoStack.clear()
        undoStack.add(takeSnapshot(home))
        redoStack.clear()
        lastPush = Long.MIN_VALUE
        isRestoring = false
    }

    /** Record the post-change state. Coalesces bursts inside 0.8 s. */
    fun recordChange(home: Home, coalesce: Boolean = true) {
        if (isRestoring) return
        val now = clock.now()
        if (coalesce && now - lastPush < coalesceMs && undoStack.size > 1) {
            undoStack[undoStack.lastIndex] = takeSnapshot(home)
        } else {
            undoStack.add(takeSnapshot(home))
            if (undoStack.size > maxDepth) {
                val drop = undoStack.size - maxDepth
                repeat(drop) { undoStack.removeAt(0) }
            }
        }
        redoStack.clear()
        lastPush = now
    }

    fun undo(home: Home): Home? {
        if (!canUndo) return null
        val current = undoStack.removeLastOrNull() ?: return null
        redoStack.add(current)
        val previous = undoStack.lastOrNull() ?: return null
        return restore(previous, home)
    }

    fun redo(home: Home): Home? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.add(next)
        return restore(next, home)
    }

    private fun restore(snapshot: HomeSnapshot, home: Home): Home {
        isRestoring = true
        return try {
            applySnapshot(home, snapshot)
        } finally {
            isRestoring = false
        }
    }
}
