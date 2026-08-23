package com.homedesign.android.domain.editor

import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Room
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.Wall

/**
 * Holds the working [Home] plus selection and an [UndoStack].
 * Mutation helpers bump topologyVersion / furnitureRevision / styleVersion.
 */
class EditorDocument(
    home: Home = HomeFactory.emptyHome("Untitled"),
    selection: Selection = Selection.None,
    val undo: UndoStack = UndoStack(),
) {
    var home: Home = home
        private set

    var selection: Selection = selection
        private set

    init {
        undo.reset(this.home)
    }

    fun replaceHome(next: Home, recordUndo: Boolean = true, coalesce: Boolean = true) {
        home = next
        if (recordUndo) undo.recordChange(home, coalesce)
    }

    fun setSelection(next: Selection) {
        selection = next
    }

    fun bumpTopology(walls: List<Wall> = home.walls, rooms: List<Room> = home.rooms): Home {
        val next = home.copy(
            walls = walls,
            rooms = rooms,
            topologyVersion = home.topologyVersion + 1,
        )
        home = next
        return next
    }

    fun bumpFurniture(nextHome: Home): Home {
        val next = nextHome.copy(furnitureRevision = nextHome.furnitureRevision + 1)
        home = next
        return next
    }

    fun bumpStyle(nextHome: Home): Home {
        val next = nextHome.copy(styleVersion = nextHome.styleVersion + 1)
        home = next
        return next
    }

    fun undoOnce(): Boolean {
        val previous = undo.undo(home) ?: return false
        home = previous
        selection = Selection.None
        return true
    }

    fun redoOnce(): Boolean {
        val next = undo.redo(home) ?: return false
        home = next
        selection = Selection.None
        return true
    }
}
