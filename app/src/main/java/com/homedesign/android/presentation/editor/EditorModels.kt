package com.homedesign.android.presentation.editor

import android.net.Uri
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Selection

sealed interface EditorTool {
    data object Select : EditorTool
    data class DrawWall(val thickness: Double = defaultWallThicknessCM) : EditorTool
    data object DrawRoom : EditorTool
    data object Dimension : EditorTool
    data class PlaceFurniture(val catalogId: String?) : EditorTool
}

/** In-progress gesture preview for draw tools. */
sealed interface DrawPreview {
    data object None : DrawPreview
    data class Wall(val start: Vec2, val end: Vec2) : DrawPreview
    data class Room(val from: Vec2, val to: Vec2) : DrawPreview
    data class Dimension(val start: Vec2, val end: Vec2?) : DrawPreview
}

data class EditorUiState(
    val projectId: String = "",
    val title: String = "Untitled",
    val savedLabel: String = "All changes saved",
    val home: Home = HomeFactory.emptyHome("Untitled"),
    val selection: Selection = Selection.None,
    val tool: EditorTool = EditorTool.Select,
    val preview: DrawPreview = DrawPreview.None,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val toast: String? = null,
    val exportUri: Uri? = null,
    val exportMime: String? = null,
    val exportLabel: String? = null,
)
