package com.homedesign.android.presentation.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homedesign.android.core.ui.Haptics
import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.catalog.catalogById
import com.homedesign.android.domain.io.SH3DReader
import com.homedesign.android.domain.editor.EditorDocument
import com.homedesign.android.domain.editor.FurnitureClipboard
import com.homedesign.android.domain.editor.FurnitureClipboardPayload
import com.homedesign.android.domain.editor.applyAddChainWall
import com.homedesign.android.domain.editor.applyAddDimension
import com.homedesign.android.domain.editor.applyAddOpening
import com.homedesign.android.domain.editor.applyDimensionLength
import com.homedesign.android.domain.editor.applyExteriorDimensionChain
import com.homedesign.android.domain.editor.applyFurnitureRotate
import com.homedesign.android.domain.editor.applyFurnitureSize
import com.homedesign.android.domain.editor.applyGroup
import com.homedesign.android.domain.editor.applyOpeningFlip
import com.homedesign.android.domain.editor.applyOpeningResize
import com.homedesign.android.domain.editor.applyOpeningSlide
import com.homedesign.android.domain.editor.applyOpeningWidth
import com.homedesign.android.domain.editor.applyAlign
import com.homedesign.android.domain.editor.applyDistribute
import com.homedesign.android.domain.editor.applyMirrorPlan
import com.homedesign.android.domain.editor.applyMirrorSelection
import com.homedesign.android.domain.editor.applyPlaceFurniture
import com.homedesign.android.domain.editor.applyPlaceLabel
import com.homedesign.android.domain.editor.applyRotatePlan
import com.homedesign.android.domain.editor.applyRenameFurniture
import com.homedesign.android.domain.editor.applyRenameRoom
import com.homedesign.android.domain.editor.applyReplaceFurniture
import com.homedesign.android.domain.editor.applyRoomSize
import com.homedesign.android.domain.editor.applyAddCurvePoint
import com.homedesign.android.domain.editor.applyMoveCurveBreakpoint
import com.homedesign.android.domain.editor.applySpanBow
import com.homedesign.android.domain.editor.applyUngroup
import com.homedesign.android.domain.editor.previewMoveCurveBreakpoint
import com.homedesign.android.domain.editor.applyCeilingColor
import com.homedesign.android.domain.editor.applyCeilingStyle
import com.homedesign.android.domain.editor.applyCeilingTexture
import com.homedesign.android.domain.editor.applyCeilingVisible
import com.homedesign.android.domain.editor.applyClearCeilingTexture
import com.homedesign.android.domain.editor.applyClearFloorTexture
import com.homedesign.android.domain.editor.applyClearWallSideTexture
import com.homedesign.android.domain.editor.DEFAULT_BASEBOARD
import com.homedesign.android.domain.editor.applyFloorColor
import com.homedesign.android.domain.editor.applyFloorTexture
import com.homedesign.android.domain.editor.applyFloorTextureValue
import com.homedesign.android.domain.editor.applyMatchWallProperties
import com.homedesign.android.domain.editor.applyRoomBorder
import com.homedesign.android.domain.editor.applyStageRoomLighting
import com.homedesign.android.domain.editor.applyWallBaseboard
import com.homedesign.android.domain.editor.applyWallBow
import com.homedesign.android.domain.editor.applyWallGlass
import com.homedesign.android.domain.editor.applyWallHeight
import com.homedesign.android.domain.editor.applyWallLength
import com.homedesign.android.domain.editor.applyWallPattern
import com.homedesign.android.domain.editor.applyWallSideColor
import com.homedesign.android.domain.editor.applyWallSideTexture
import com.homedesign.android.domain.editor.applyWallSideTextureValue
import com.homedesign.android.domain.editor.applyWallThickness
import com.homedesign.android.domain.geom.BorderKind
import com.homedesign.android.domain.model.CeilingStyle
import com.homedesign.android.domain.textures.TexturePreset
import com.homedesign.android.domain.textures.UserTextureStore
import com.homedesign.android.domain.editor.commitFurnitureMove
import com.homedesign.android.domain.editor.commitRectangleRoom
import com.homedesign.android.domain.editor.deleteSelection
import com.homedesign.android.domain.editor.previewSpanBow
import com.homedesign.android.domain.editor.previewWallBow
import com.homedesign.android.domain.editor.toggleFurnitureInSelection
import com.homedesign.android.domain.editor.wallsOnLevel
import com.homedesign.android.domain.export.PlanThumbnail
import com.homedesign.android.domain.export.exportDXF
import com.homedesign.android.domain.export.exportPDF
import com.homedesign.android.domain.geom.AlignEdge
import com.homedesign.android.domain.geom.AlignmentAxis
import com.homedesign.android.domain.geom.AngleSnap
import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.DimensionFaceMode
import com.homedesign.android.domain.geom.DistributeAxis
import com.homedesign.android.domain.geom.FurnitureReplace
import com.homedesign.android.domain.geom.FurnitureSnap
import com.homedesign.android.domain.geom.HitTest
import com.homedesign.android.domain.geom.LevelMutation
import com.homedesign.android.domain.geom.OpeningBinding
import com.homedesign.android.domain.geom.OpeningKind
import com.homedesign.android.domain.geom.PlanAxis
import com.homedesign.android.domain.geom.PlanRotation
import com.homedesign.android.domain.geom.ResizeSide
import com.homedesign.android.domain.geom.RoomContainment
import com.homedesign.android.domain.geom.SnapEngine
import com.homedesign.android.domain.geom.SnapTarget
import com.homedesign.android.domain.geom.TRACE_DEFAULT_CM
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.clampTraceWidthCM
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.geom.dist
import com.homedesign.android.domain.geom.exteriorThicknessCM
import com.homedesign.android.domain.geom.furnitureSnapToWallCM
import com.homedesign.android.domain.geom.hitEndpointPx
import com.homedesign.android.domain.geom.hitFurnitureHaloPx
import com.homedesign.android.domain.geom.hitWallCoarsePx
import com.homedesign.android.domain.geom.interiorThicknessCM
import com.homedesign.android.domain.geom.minDrawnWallCM
import com.homedesign.android.domain.geom.projectTOnWall
import com.homedesign.android.domain.geom.resolveDimensionSnap
import com.homedesign.android.domain.geom.spanBow
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.settings.SettingsRepository
import com.homedesign.android.domain.sketch.SketchImagePrep
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val document = EditorDocument()
    private var projectId: String = ""
    private var dirty = false
    private var autosaveJob: Job? = null
    private var wallDrawStart: Vec2? = null
    private var dimensionStart: Vec2? = null
    private var openingDrag: OpeningDragGesture? = null
    private var bowDrag: BowDragGesture? = null
    private var breakpointDrag: BreakpointDragGesture? = null
    private var clipboard: FurnitureClipboardPayload? = null

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        unitSystem = settings.unitSystem,
                        recentFurnitureIds = settings.recentFurnitureCatalogIds,
                        showEditorTip = !settings.editorTipDismissed,
                    )
                }
            }
        }
    }

    fun setUnitSystem(unit: UnitSystem) {
        viewModelScope.launch { settingsRepository.setUnitSystem(unit) }
    }

    fun dismissEditorTip() {
        viewModelScope.launch { settingsRepository.setEditorTipDismissed(true) }
    }

    fun recordRecentFurniture(catalogId: String) {
        viewModelScope.launch { settingsRepository.recordRecentFurniture(catalogId) }
    }

    fun selectLevel(levelId: String) {
        val home = document.home
        if (home.selectedLevelID == levelId) return
        if (home.levels.none { it.id == levelId && it.visible }) return
        wallDrawStart = null
        dimensionStart = null
        document.setSelection(Selection.None)
        // View-only switch — not undoable (iOS FloorSelector).
        document.replaceHome(
            home.copy(
                selectedLevelID = levelId,
                topologyVersion = home.topologyVersion + 1,
            ),
            recordUndo = false,
        )
        _state.update { it.copy(preview = DrawPreview.None) }
        markDirty(coalesce = false)
        publish()
    }

    fun addFloorOnTop() {
        val home = document.home
        val levels = LevelMutation.addLevelOnTop(home.levels, home.wallHeight)
        val newLevel = levels.lastOrNull() ?: return
        wallDrawStart = null
        dimensionStart = null
        document.setSelection(Selection.None)
        document.replaceHome(
            home.copy(
                levels = levels,
                selectedLevelID = newLevel.id,
                topologyVersion = home.topologyVersion + 1,
            ),
            coalesce = false,
        )
        _state.update { it.copy(preview = DrawPreview.None) }
        markDirty(coalesce = false)
        publish()
        _events.tryEmit("Added ${newLevel.name ?: "floor"}")
    }

    fun setGhostOtherLevels(enabled: Boolean) {
        _state.update { it.copy(ghostOtherLevels = enabled) }
    }

    fun toggleGhostOtherLevels() {
        _state.update { it.copy(ghostOtherLevels = !it.ghostOtherLevels) }
    }

    fun load(projectId: String) {
        if (projectId.isBlank()) return
        this.projectId = projectId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, projectId = projectId, trace = null) }
            try {
                val home = projectRepository.loadHome(projectId)
                val meta = projectRepository.getProject(projectId)
                document.replaceHome(home, recordUndo = false)
                document.undo.reset(home)
                document.setSelection(Selection.None)
                dirty = false
                wallDrawStart = null
                dimensionStart = null
                projectRepository.touch(projectId)
                settingsRepository.setLastProjectId(projectId)
                settingsRepository.setEditorSessionDirty(false)
                publish(savedLabel = "All changes saved", title = meta?.name ?: home.name ?: "Untitled")
                restoreTraceFromDisk(projectId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Failed to load project")
                }
            }
        }
    }

    fun setTool(tool: EditorTool) {
        // Switching Interior/Exterior thickness while drawing must keep the chain.
        val keepWallChain =
            tool is EditorTool.DrawWall && _state.value.tool is EditorTool.DrawWall
        if (keepWallChain) {
            _state.update { it.copy(tool = tool) }
            return
        }
        wallDrawStart = null
        dimensionStart = null
        document.setSelection(Selection.None)
        _state.update {
            it.copy(
                tool = tool,
                selection = Selection.None,
                preview = DrawPreview.None,
                pendingLabelPoint = null,
            )
        }
    }

    fun setViewMode(mode: EditorViewMode) {
        _state.update { it.copy(viewMode = mode) }
    }

    fun clearSelection() {
        document.setSelection(Selection.None)
        publish()
    }

    fun undo() {
        if (!document.undoOnce()) return
        markDirty(coalesce = false)
        publish()
    }

    fun redo() {
        if (!document.redoOnce()) return
        markDirty(coalesce = false)
        publish()
    }

    fun deleteSelected() {
        val result = deleteSelection(document.home, document.selection)
        if (result.toast == null && result.selection == document.selection) return
        document.replaceHome(result.home)
        document.setSelection(result.selection)
        markDirty(coalesce = false)
        publish(toast = result.toast)
    }

    fun copySelection() {
        val payload = FurnitureClipboard.encodeSelection(document.home, document.selection) ?: return
        clipboard = payload
        _state.update { it.copy(hasClipboard = true, toast = "Copied") }
    }

    fun pasteClipboard() {
        val payload = clipboard ?: return
        val (next, ids) = FurnitureClipboard.applyPaste(document.home, payload)
        if (ids.isEmpty()) return
        document.replaceHome(next)
        document.setSelection(selectionForFurnitureIds(ids))
        markDirty(coalesce = false)
        publish(toast = "Pasted")
    }

    fun duplicateSelection() {
        val (next, ids) = FurnitureClipboard.applyDuplicate(document.home, document.selection)
        if (ids.isEmpty()) return
        document.replaceHome(next)
        document.setSelection(selectionForFurnitureIds(ids))
        markDirty(coalesce = false)
        publish()
    }

    fun alignSelection(edge: AlignEdge) {
        val ids = selectedFurnitureIds() ?: return
        val next = applyAlign(document.home, ids, edge)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    fun distributeSelection(axis: DistributeAxis) {
        val ids = selectedFurnitureIds() ?: return
        val next = applyDistribute(document.home, ids, axis)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    fun groupSelection() {
        val ids = selectedFurnitureIds() ?: return
        val next = applyGroup(document.home, ids)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    fun ungroupSelection() {
        val ids = selectedFurnitureIds() ?: return
        val next = applyUngroup(document.home, ids)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    fun mirrorSelection(axis: PlanAxis) {
        val ids = selectedFurnitureIds() ?: return
        val next = applyMirrorSelection(document.home, ids, axis)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish(toast = "Mirrored")
    }

    fun mirrorPlan(axis: PlanAxis) {
        val next = applyMirrorPlan(document.home, axis)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.None)
        markDirty(coalesce = false)
        publish(toast = if (axis == PlanAxis.Vertical) "Mirrored L↔R" else "Mirrored T↔B")
    }

    fun rotatePlan(rotation: PlanRotation) {
        val next = applyRotatePlan(document.home, rotation)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.None)
        markDirty(coalesce = false)
        publish(toast = "Rotated plan")
    }

    fun setDimensionLength(lengthCM: Double) {
        val sel = document.selection as? Selection.Annotation ?: return
        if (sel.isLabel) return
        val next = applyDimensionLength(document.home, sel.id, lengthCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun onDimensionEndPreview(dimId: String, atStart: Boolean, x: Double, y: Double) {
        val dim = document.home.dimensionLines.find { it.id == dimId } ?: return
        val walls = wallsOnLevel(document.home)
        val snap = SnapEngine.snap(vec(x, y), walls, snapRadiusCM = 25.0)
        val point = resolveDimensionSnap(snap, vec(x, y), walls, DimensionFaceMode.Outer)
        _state.update {
            it.copy(
                preview = DrawPreview.DimensionEdit(
                    dimId = dimId,
                    xStart = if (atStart) point.x else dim.xStart,
                    yStart = if (atStart) point.y else dim.yStart,
                    xEnd = if (atStart) dim.xEnd else point.x,
                    yEnd = if (atStart) dim.yEnd else point.y,
                    offset = dim.offset,
                ),
            )
        }
    }

    fun onDimensionOffsetPreview(dimId: String, offset: Double) {
        val dim = document.home.dimensionLines.find { it.id == dimId } ?: return
        _state.update {
            it.copy(
                preview = DrawPreview.DimensionEdit(
                    dimId = dimId,
                    xStart = dim.xStart,
                    yStart = dim.yStart,
                    xEnd = dim.xEnd,
                    yEnd = dim.yEnd,
                    offset = offset,
                ),
            )
        }
    }

    fun commitDimensionEdit() {
        val edit = _state.value.preview as? DrawPreview.DimensionEdit ?: run {
            cancelPreview()
            return
        }
        val dim = document.home.dimensionLines.find { it.id == edit.dimId }
        _state.update { it.copy(preview = DrawPreview.None) }
        if (dim == null) return
        if (edit.xStart == dim.xStart && edit.yStart == dim.yStart &&
            edit.xEnd == dim.xEnd && edit.yEnd == dim.yEnd &&
            edit.offset == dim.offset
        ) {
            return
        }
        val next = document.home.copy(
            dimensionLines = document.home.dimensionLines.map { d ->
                if (d.id != edit.dimId) d
                else d.copy(
                    xStart = edit.xStart,
                    yStart = edit.yStart,
                    xEnd = edit.xEnd,
                    yEnd = edit.yEnd,
                    offset = edit.offset,
                )
            },
            topologyVersion = document.home.topologyVersion + 1,
        )
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    fun setRoomSize(widthCM: Double?, depthCM: Double?) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyRoomSize(document.home, id, widthCM, depthCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun toggleStampMode() {
        val tool = _state.value.tool as? EditorTool.PlaceFurniture ?: return
        _state.update { it.copy(tool = tool.copy(stamp = !tool.stamp)) }
    }

    fun replaceFurniture(entry: CatalogEntry) {
        val pieceId = (document.selection as? Selection.Furniture)?.id ?: return
        val piece = document.home.furniture.find { it.id == pieceId } ?: return
        val also = FurnitureReplace.similarPlaceholderIDs(piece, document.home.furniture)
        val next = applyReplaceFurniture(document.home, pieceId, entry, also)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.Furniture(pieceId))
        markDirty(coalesce = false)
        publish()
    }

    fun setTraceFromUri(uri: Uri) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                decodeTraceBitmap(uri)
            } ?: run {
                _events.tryEmit("Could not load image")
                return@launch
            }
            val (bitmap, path) = prepared
            val image = bitmap.asImageBitmap()
            _state.update {
                it.copy(
                    trace = TraceUnderlayState(
                        image = image,
                        widthCM = TRACE_DEFAULT_CM,
                        pixelWidth = bitmap.width,
                        pixelHeight = bitmap.height,
                        filePath = path,
                    ),
                )
            }
        }
    }

    fun setTraceWidthCM(widthCM: Double) {
        _state.update { state ->
            val t = state.trace ?: return@update state
            state.copy(trace = t.copy(widthCM = clampTraceWidthCM(widthCM)))
        }
    }

    fun clearTrace() {
        val path = _state.value.trace?.filePath
        _state.update { it.copy(trace = null) }
        if (path != null && projectId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { File(path).delete() }
            }
        }
    }

    fun setWallThickness(interior: Boolean) {
        val sel = document.selection
        val wallId = when (sel) {
            is Selection.Wall -> sel.id
            is Selection.Endpoint -> sel.wallID
            else -> return
        }
        val thickness = if (interior) interiorThicknessCM else exteriorThicknessCM
        val next = applyWallThickness(document.home, wallId, thickness)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallLength(lengthCM: Double) {
        val sel = document.selection
        val wallId = when (sel) {
            is Selection.Wall -> sel.id
            is Selection.Endpoint -> sel.wallID
            else -> return
        }
        val next = applyWallLength(document.home, wallId, lengthCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallHeight(heightCM: Double) {
        val wallId = selectedWallId() ?: return
        val next = applyWallHeight(document.home, wallId, heightCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallSideColor(side: String, hex: String) {
        val wallId = selectedWallId() ?: return
        val next = applyWallSideColor(document.home, wallId, side, hex)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallSidePreset(side: String, preset: TexturePreset) {
        val wallId = selectedWallId() ?: return
        val next = applyWallSideTexture(document.home, wallId, side, preset)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun clearWallSideTexture(side: String) {
        val wallId = selectedWallId() ?: return
        val next = applyClearWallSideTexture(document.home, wallId, side)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallPattern(hatched: Boolean) {
        val wallId = selectedWallId() ?: return
        val next = applyWallPattern(document.home, wallId, hatched)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallGlass(glass: Boolean) {
        val wallId = selectedWallId() ?: return
        val next = applyWallGlass(document.home, wallId, glass)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallBaseboardEnabled(side: String, enabled: Boolean) {
        val wallId = selectedWallId() ?: return
        val wall = document.home.walls.find { it.id == wallId } ?: return
        val current = if (side == "right") wall.rightSideBaseboard else wall.leftSideBaseboard
        val nextBoard = when {
            !enabled -> null
            current != null -> current
            else -> DEFAULT_BASEBOARD
        }
        val next = applyWallBaseboard(document.home, wallId, side, nextBoard)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallBaseboardHeight(side: String, heightCM: Double) {
        val wallId = selectedWallId() ?: return
        val wall = document.home.walls.find { it.id == wallId } ?: return
        val current = (if (side == "right") wall.rightSideBaseboard else wall.leftSideBaseboard)
            ?: DEFAULT_BASEBOARD
        val clamped = heightCM.coerceIn(2.0, 200.0)
        val next = applyWallBaseboard(
            document.home,
            wallId,
            side,
            current.copy(height = clamped),
        )
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setWallBaseboardThickness(side: String, thicknessCM: Double) {
        val wallId = selectedWallId() ?: return
        val wall = document.home.walls.find { it.id == wallId } ?: return
        val current = (if (side == "right") wall.rightSideBaseboard else wall.leftSideBaseboard)
            ?: DEFAULT_BASEBOARD
        val clamped = thicknessCM.coerceIn(0.5, 20.0)
        val next = applyWallBaseboard(
            document.home,
            wallId,
            side,
            current.copy(thickness = clamped),
        )
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun startFormatPainter() {
        val wallId = selectedWallId() ?: return
        wallDrawStart = null
        dimensionStart = null
        _state.update {
            it.copy(
                tool = EditorTool.FormatPainter(wallId),
                preview = DrawPreview.None,
                toast = "Tap walls to paint",
            )
        }
    }

    fun stageSelectedRoom() {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val room = document.home.rooms.find { it.id == id } ?: return
        val xs = room.points.map { it.x }
        val ys = room.points.map { it.y }
        if (xs.isEmpty() || ys.isEmpty()) return
        val pad = 40.0
        val focus = CameraFocusRequest(
            token = System.currentTimeMillis(),
            minX = (xs.minOrNull() ?: 0.0) - pad,
            minY = (ys.minOrNull() ?: 0.0) - pad,
            maxX = (xs.maxOrNull() ?: 0.0) + pad,
            maxY = (ys.maxOrNull() ?: 0.0) + pad,
        )
        val next = applyStageRoomLighting(document.home, id)
        if (next !== document.home) {
            document.replaceHome(next)
            markDirty()
        }
        _state.update {
            it.copy(cameraFocus = focus, toast = "Room staged")
        }
        publish()
    }

    fun importWallTextureFromUri(side: String, uri: Uri) {
        val wallId = selectedWallId() ?: return
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) {
                UserTextureStore.importFromUri(appContext, uri)
            } ?: run {
                publish(toast = "Could not import texture")
                return@launch
            }
            val texture = UserTextureStore.toWallTexture(entry)
            val next = applyWallSideTextureValue(document.home, wallId, side, texture)
            if (next === document.home) return@launch
            document.replaceHome(next)
            markDirty()
            publish(toast = "Texture applied")
        }
    }

    fun importFloorTextureFromUri(uri: Uri) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) {
                UserTextureStore.importFromUri(appContext, uri)
            } ?: run {
                publish(toast = "Could not import texture")
                return@launch
            }
            val texture = UserTextureStore.toWallTexture(entry)
            val next = applyFloorTextureValue(document.home, id, texture)
            if (next === document.home) return@launch
            document.replaceHome(next)
            markDirty()
            publish(toast = "Floor texture applied")
        }
    }

    fun straightenWall() {
        val wallId = selectedWallId() ?: return
        val next = applyWallBow(document.home, wallId, 0.0)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    /** Insert a curve breakpoint at mid-chord (or mid of longest span). */
    fun addCurvePoint() {
        val wallId = selectedWallId() ?: return
        val wall = document.home.walls.find { it.id == wallId } ?: return
        val openings = document.home.doorsAndWindows.filter {
            document.home.selectedLevelID == null || it.piece.level == document.home.selectedLevelID
        }
        if (OpeningBinding.bind(listOf(wall), openings).isNotEmpty()) return
        val profile = com.homedesign.android.domain.geom.effectiveProfile(wall)
        val params = com.homedesign.android.domain.geom.spanParams(profile)
        var bestT = 0.5
        var bestLen = 0.0
        for (i in profile.spans.indices) {
            val frac = params.getOrElse(i + 1) { 1.0 } - params.getOrElse(i) { 0.0 }
            if (frac > bestLen) {
                bestLen = frac
                bestT = (params.getOrElse(i) { 0.0 } + params.getOrElse(i + 1) { 1.0 }) * 0.5
            }
        }
        val next = applyAddCurvePoint(document.home, wallId, bestT)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.Wall(wallId))
        markDirty(coalesce = false)
        publish()
    }

    fun setFloorColor(hex: String) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyFloorColor(document.home, id, hex)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setFloorPreset(preset: TexturePreset) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyFloorTexture(document.home, id, preset)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun clearFloorTexture() {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyClearFloorTexture(document.home, id)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setCeilingColor(hex: String) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyCeilingColor(document.home, id, hex)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setCeilingPreset(preset: TexturePreset) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyCeilingTexture(document.home, id, preset)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun clearCeilingTexture() {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyClearCeilingTexture(document.home, id)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setRoomBorder(kind: BorderKind) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyRoomBorder(document.home, id, kind)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setCeilingVisible(visible: Boolean) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyCeilingVisible(document.home, id, visible)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setCeilingStyle(style: CeilingStyle?) {
        val id = (document.selection as? Selection.Room)?.id ?: return
        val next = applyCeilingStyle(document.home, id, style)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    private fun selectedWallId(): String? = when (val sel = document.selection) {
        is Selection.Wall -> sel.id
        is Selection.Endpoint -> sel.wallID
        else -> null
    }

    fun setOpeningWidth(widthCM: Double) {
        val id = when (val sel = document.selection) {
            is Selection.Opening -> sel.id
            is Selection.OpeningHandle -> sel.id
            else -> return
        }
        val next = applyOpeningWidth(document.home, id, widthCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setFurnitureWidth(widthCM: Double) {
        val id = (document.selection as? Selection.Furniture)?.id ?: return
        val next = applyFurnitureSize(document.home, id, widthCM = widthCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setFurnitureDepth(depthCM: Double) {
        val id = (document.selection as? Selection.Furniture)?.id ?: return
        val next = applyFurnitureSize(document.home, id, depthCM = depthCM)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun setFurnitureAngleDeg(degrees: Double) {
        val id = (document.selection as? Selection.Furniture)?.id ?: return
        val next = applyFurnitureRotate(document.home, id, degrees * PI / 180.0)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun renameSelection(name: String) {
        val next = when (val sel = document.selection) {
            is Selection.Room -> applyRenameRoom(document.home, sel.id, name)
            is Selection.Furniture -> applyRenameFurniture(document.home, sel.id, name)
            else -> return
        }
        if (next === document.home) return
        document.replaceHome(next)
        markDirty()
        publish()
    }

    fun flipOpeningHinge() = flipOpening('x')

    fun flipOpeningSwing() = flipOpening('y')

    fun toggleOpeningOpen() {
        val id = when (val sel = document.selection) {
            is Selection.Opening -> sel.id
            is Selection.OpeningHandle -> sel.id
            else -> return
        }
        val home = document.home
        val nextDoors = home.doorsAndWindows.map { d ->
            if (d.piece.id == id) d.copy(isOpen = !d.isOpen) else d
        }
        if (nextDoors === home.doorsAndWindows) return
        document.replaceHome(home.copy(doorsAndWindows = nextDoors))
        markDirty(coalesce = false)
        publish()
    }

    private fun flipOpening(axis: Char) {
        val id = when (val sel = document.selection) {
            is Selection.Opening -> sel.id
            is Selection.OpeningHandle -> sel.id
            else -> return
        }
        val next = applyOpeningFlip(document.home, id, axis)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    /**
     * Select-tool drag on a selected opening: resize handle or slide along wall.
     * Returns true when the gesture is claimed (caller should not pan).
     */
    fun tryBeginOpeningDrag(plan: Vec2, scalePxPerCm: Float): Boolean {
        val sel = document.selection
        val openingId = when (sel) {
            is Selection.Opening -> sel.id
            is Selection.OpeningHandle -> sel.id
            else -> return false
        }
        val scale = max(scalePxPerCm.toDouble(), 0.001)
        val home = document.home
        val level = home.selectedLevelID
        val walls = home.walls.filter { level == null || it.level == level }
        val opening = home.doorsAndWindows.find {
            it.piece.id == openingId && (level == null || it.piece.level == level)
        } ?: return false

        val host = OpeningBinding.bind(walls, listOf(opening)).firstOrNull() ?: return false
        val wall = walls.find { it.id == host.wallID } ?: return false

        val handleR = 16.0 / scale
        val handle = HitTest.closestOpeningHandle(plan, opening, handleR)
        if (handle != null) {
            val side = if (handle.side == "start") ResizeSide.Start else ResizeSide.End
            openingDrag = OpeningDragGesture.Resize(opening.piece.id, wall.id, side)
            document.setSelection(
                Selection.OpeningHandle(
                    opening.piece.id,
                    if (side == ResizeSide.Start) Selection.Side.Start else Selection.Side.End,
                ),
            )
            publish()
            return true
        }
        val body = HitTest.closestOpening(plan, listOf(opening), 32.0 / scale)
        if (body != null) {
            val t = projectTOnWall(wall, plan.x, plan.y)
            val centerT = projectTOnWall(wall, opening.piece.x, opening.piece.y)
            openingDrag = OpeningDragGesture.Slide(
                openingID = opening.piece.id,
                wallID = wall.id,
                initialCenterT = centerT,
                startProjectedT = t,
            )
            return true
        }
        return false
    }

    fun updateOpeningDrag(plan: Vec2) {
        val gesture = openingDrag ?: return
        val next = when (gesture) {
            is OpeningDragGesture.Slide -> {
                val wall = document.home.walls.find { it.id == gesture.wallID } ?: return
                val tNow = projectTOnWall(wall, plan.x, plan.y)
                val t = gesture.initialCenterT + (tNow - gesture.startProjectedT)
                applyOpeningSlide(document.home, gesture.openingID, gesture.wallID, t)
            }
            is OpeningDragGesture.Resize -> {
                val wall = document.home.walls.find { it.id == gesture.wallID } ?: return
                val tNow = projectTOnWall(wall, plan.x, plan.y)
                applyOpeningResize(document.home, gesture.openingID, gesture.wallID, gesture.side, tNow)
            }
        }
        if (next === document.home) return
        document.replaceHome(next, coalesce = true)
        markDirty(coalesce = true)
        publish()
    }

    fun endOpeningDrag() {
        val gesture = openingDrag ?: return
        openingDrag = null
        document.setSelection(Selection.Opening(gesture.openingID))
        publish()
    }

    fun onPlanTap(plan: Vec2, scalePxPerCm: Float, additive: Boolean = false) {
        when (val tool = _state.value.tool) {
            EditorTool.Select -> selectAt(plan, scalePxPerCm, additive = additive)
            is EditorTool.DrawWall -> onDrawWallTap(plan, tool.thickness)
            EditorTool.DrawRoom -> Unit // drag-based
            EditorTool.Dimension -> onDimensionTap(plan)
            is EditorTool.PlaceFurniture -> placeFurniture(tool.catalogId, plan, stamp = tool.stamp)
            is EditorTool.PlaceOpening -> placeOpening(tool.kind, plan, scalePxPerCm)
            is EditorTool.FormatPainter -> onFormatPainterTap(plan, scalePxPerCm, tool.sourceWallID)
            EditorTool.PlaceLabel -> beginPlaceLabel(plan)
            EditorTool.PlaceWalker -> placeWalker(plan)
        }
    }

    private fun placeWalker(plan: Vec2) {
        val pose = WalkPose(x = plan.x, y = plan.y, angle = walkHeadingAt(plan))
        document.setSelection(Selection.None)
        Haptics.commit(appContext)
        wallDrawStart = null
        dimensionStart = null
        _state.update {
            it.copy(
                walkPose = pose,
                tool = EditorTool.Select,
                viewMode = EditorViewMode.Walk,
                selection = Selection.None,
                preview = DrawPreview.None,
                pendingLabelPoint = null,
            )
        }
    }

    /** Face the farthest corner of the containing room (iOS pegman drop). */
    private fun walkHeadingAt(plan: Vec2): Double {
        val level = document.home.selectedLevelID
        val rooms = document.home.rooms.filter { level == null || it.level == level }
        val room = rooms.firstOrNull { RoomContainment.pointInRoom(it, plan) } ?: return 0.0
        var farX = plan.x
        var farY = plan.y
        var best = -1.0
        for (p in room.points) {
            val d2 = (p.x - plan.x) * (p.x - plan.x) + (p.y - plan.y) * (p.y - plan.y)
            if (d2 > best) {
                best = d2
                farX = p.x
                farY = p.y
            }
        }
        val dx = farX - plan.x
        val dy = farY - plan.y
        if (best < 1e-9) return 0.0
        // Walk yaw 0 = world +Z = plan +Y, so atan2(x, y).
        return atan2(dx, dy)
    }

    private fun beginPlaceLabel(plan: Vec2) {
        _state.update { it.copy(pendingLabelPoint = plan) }
    }

    fun confirmPlaceLabel(text: String) {
        val point = _state.value.pendingLabelPoint ?: return
        _state.update { it.copy(pendingLabelPoint = null) }
        val next = applyPlaceLabel(document.home, point.x, point.y, text)
        if (next === document.home) return
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish()
    }

    fun cancelPlaceLabel() {
        if (_state.value.pendingLabelPoint == null) return
        _state.update { it.copy(pendingLabelPoint = null) }
    }

    private fun onFormatPainterTap(plan: Vec2, scalePxPerCm: Float, sourceWallID: String) {
        val scale = max(scalePxPerCm.toDouble(), 0.001)
        val walls = wallsOnLevel(document.home)
        val hit = HitTest.closestWall(plan, walls, hitWallCoarsePx / scale) ?: return
        if (hit.wallID == sourceWallID) return
        val next = applyMatchWallProperties(document.home, sourceWallID, hit.wallID)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.Wall(hit.wallID))
        markDirty()
        publish(toast = "Style applied")
    }

    private fun currentWallThickness(): Double =
        (_state.value.tool as? EditorTool.DrawWall)?.thickness ?: defaultWallThicknessCM

    /** Arm the wall start without committing (used by drag-to-draw). */
    fun onDrawWallArm(plan: Vec2, scalePxPerCm: Float = 1f) {
        if (wallDrawStart != null) return
        val walls = wallsOnLevel(document.home)
        val resolved = resolveDrawPoint(plan, walls, scalePxPerCm, from = null)
        wallDrawStart = resolved.point
        val t = currentWallThickness()
        _state.update {
            it.copy(preview = DrawPreview.Wall(resolved.point, resolved.point, t, resolved.guides))
        }
    }

    fun onDrawWallDrag(plan: Vec2, scalePxPerCm: Float = 1f) {
        val start = wallDrawStart ?: run {
            val walls = wallsOnLevel(document.home)
            val resolved = resolveDrawPoint(plan, walls, scalePxPerCm, from = null)
            wallDrawStart = resolved.point
            resolved.point
        }
        val walls = wallsOnLevel(document.home)
        val resolved = resolveDrawPoint(plan, walls, scalePxPerCm, from = start)
        val t = currentWallThickness()
        _state.update {
            it.copy(preview = DrawPreview.Wall(start, resolved.point, t, resolved.guides))
        }
    }

    fun onDrawWallCommit(plan: Vec2, thickness: Double = defaultWallThicknessCM, scalePxPerCm: Float = 1f) {
        val start = wallDrawStart ?: return
        val walls = wallsOnLevel(document.home)
        val end = resolveDrawPoint(plan, walls, scalePxPerCm, from = start).point
        wallDrawStart = null
        _state.update { it.copy(preview = DrawPreview.None) }
        if (dist(start, end) < minDrawnWallCM) return
        val next = applyAddChainWall(document.home, start, end, thickness)
        document.replaceHome(next)
        wallDrawStart = end // chain next segment from this endpoint
        Haptics.commit(appContext)
        markDirty(coalesce = false)
        publish()
        _state.update { it.copy(preview = DrawPreview.Wall(end, end, thickness)) }
    }

    fun applyExteriorDims() {
        val next = applyExteriorDimensionChain(document.home)
        if (next === document.home) {
            _events.tryEmit("No exterior envelope to dimension")
            return
        }
        document.replaceHome(next)
        markDirty(coalesce = false)
        publish(toast = "Exterior dimensions added")
    }

    fun onDrawRoomDrag(from: Vec2, to: Vec2) {
        _state.update { it.copy(preview = DrawPreview.Room(from, to)) }
    }

    fun onDrawRoomCommit(from: Vec2, to: Vec2) {
        _state.update { it.copy(preview = DrawPreview.None) }
        val next = commitRectangleRoom(document.home, from, to)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.None)
        setTool(EditorTool.Select)
        markDirty(coalesce = false)
        publish()
    }

    fun cancelPreview() {
        wallDrawStart = null
        dimensionStart = null
        bowDrag = null
        breakpointDrag = null
        _state.update { it.copy(preview = DrawPreview.None) }
    }

    fun onFurnitureMovePreview(pieceId: String, x: Double, y: Double) {
        val guides = furnitureMoveGuides(pieceId, x, y)
        _state.update { it.copy(preview = DrawPreview.FurnitureMove(pieceId, x, y, guides)) }
    }

    fun onFurnitureRotatePreview(pieceId: String, angle: Double) {
        _state.update { it.copy(preview = DrawPreview.FurnitureRotate(pieceId, angle)) }
    }

    fun tryBeginBowHandleDrag(plan: Vec2, scalePxPerCm: Float): Boolean {
        val wallId = when (val sel = document.selection) {
            is Selection.Wall -> sel.id
            is Selection.Endpoint -> sel.wallID
            else -> return false
        }
        val home = document.home
        val wall = home.walls.find { it.id == wallId } ?: return false
        val openings = home.doorsAndWindows.filter {
            home.selectedLevelID == null || it.piece.level == home.selectedLevelID
        }
        if (OpeningBinding.bind(listOf(wall), openings).isNotEmpty()) return false
        val scale = max(scalePxPerCm.toDouble(), 0.001)
        val handleR = com.homedesign.android.domain.geom.hitCurveHandlePx / scale
        // Prefer breakpoint dots over bow handles.
        val breaks = ArcWallGeometry.breakpointPositions(wall)
        for (i in breaks.indices) {
            if (dist(plan, breaks[i].point) <= handleR) {
                breakpointDrag = BreakpointDragGesture(wallId, i)
                return true
            }
        }
        val profile = com.homedesign.android.domain.geom.effectiveProfile(wall)
        val multi = (profile.breaks?.size ?: 0) > 0
        if (multi) {
            for (i in profile.spans.indices) {
                val handle = ArcWallGeometry.spanHandlePosition(wall, i)
                if (dist(plan, handle) <= handleR) {
                    bowDrag = BowDragGesture(wallId, i, useProfile = true)
                    return true
                }
            }
        } else {
            val handle = ArcWallGeometry.handleHitPosition(wall, scale)
            if (dist(plan, handle) <= handleR) {
                bowDrag = BowDragGesture(wallId, 0, useProfile = wall.curveProfile != null)
                return true
            }
        }
        return false
    }

    fun updateBowHandleDrag(plan: Vec2) {
        val bp = breakpointDrag
        if (bp != null) {
            val wall = document.home.walls.find { it.id == bp.wallID } ?: return
            val t = com.homedesign.android.domain.geom.WallCurveMutation.chordT(wall, plan)
            breakpointDrag = bp.copy(lastT = t)
            val (walls, _) = previewMoveCurveBreakpoint(
                document.home,
                bp.wallID,
                bp.breakIndex,
                t,
            )
            _state.update { it.copy(preview = DrawPreview.WallBow(walls)) }
            return
        }
        val gesture = bowDrag ?: return
        val wall = document.home.walls.find { it.id == gesture.wallID } ?: return
        val (walls, _) = if (gesture.useProfile || wall.curveProfile != null) {
            val bow = ArcWallGeometry.spanBowFromHandle(wall, gesture.spanIndex, plan)
            previewSpanBow(document.home, gesture.wallID, gesture.spanIndex, bow)
        } else {
            val extent = ArcWallGeometry.bowFromHandle(wall, plan)
            previewWallBow(document.home, gesture.wallID, extent)
        }
        _state.update { it.copy(preview = DrawPreview.WallBow(walls)) }
    }

    fun endBowHandleDrag() {
        val bp = breakpointDrag
        if (bp != null) {
            breakpointDrag = null
            _state.update { it.copy(preview = DrawPreview.None) }
            val t = bp.lastT ?: return
            val next = applyMoveCurveBreakpoint(document.home, bp.wallID, bp.breakIndex, t)
            if (next === document.home) return
            document.replaceHome(next)
            document.setSelection(Selection.Wall(bp.wallID))
            markDirty(coalesce = false)
            publish()
            return
        }
        val gesture = bowDrag ?: return
        bowDrag = null
        val preview = _state.value.preview as? DrawPreview.WallBow
        _state.update { it.copy(preview = DrawPreview.None) }
        val bowed = preview?.walls?.find { it.id == gesture.wallID } ?: return
        val next = if (bowed.curveProfile != null) {
            val profile = com.homedesign.android.domain.geom.effectiveProfile(bowed)
            val span = profile.spans.getOrNull(gesture.spanIndex)
            val bow = if (span != null) spanBow(span) else 0.0
            applySpanBow(document.home, gesture.wallID, gesture.spanIndex, bow)
        } else {
            applyWallBow(document.home, gesture.wallID, bowed.arcExtent ?: 0.0)
        }
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.Wall(gesture.wallID))
        markDirty(coalesce = false)
        publish()
    }

    fun commitFurnitureMoveGesture(pieceId: String, x: Double, y: Double) {
        _state.update { it.copy(preview = DrawPreview.None) }
        val next = commitFurnitureMove(document.home, pieceId, x, y)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.Furniture(pieceId))
        markDirty(coalesce = false)
        publish()
    }

    fun commitFurnitureRotateGesture(pieceId: String, angle: Double) {
        _state.update { it.copy(preview = DrawPreview.None) }
        val next = applyFurnitureRotate(document.home, pieceId, angle)
        if (next === document.home) return
        document.replaceHome(next)
        document.setSelection(Selection.Furniture(pieceId))
        markDirty(coalesce = false)
        publish()
    }

    fun flushSave() {
        autosaveJob?.cancel()
        if (!dirty || projectId.isBlank()) return
        viewModelScope.launch { saveNow() }
    }

    fun exportPdf() = export { home, units -> exportPDF(home, units) }

    fun exportDxf() = export { home, units -> exportDXF(home, units) }

    fun exportHomedesign() {
        viewModelScope.launch {
            flushSaveSuspend()
            val bytes = HomedesignZip.encode(document.home)
            val file = writeExportFile(
                "${sanitize(document.home.name)}.homedesign",
                bytes,
            )
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
            _state.update {
                it.copy(
                    exportUri = uri,
                    exportMime = "application/zip",
                    exportLabel = file.name,
                )
            }
            _events.tryEmit("Exported ${file.name}")
        }
    }

    /** Write current plan bytes into a user-picked CreateDocument URI (Save copy). */
    fun saveCopyToUri(uri: Uri) {
        viewModelScope.launch {
            flushSaveSuspend()
            try {
                val bytes = HomedesignZip.encode(document.home)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Could not open output")
                }
                _events.tryEmit("Saved copy")
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Save copy failed")
            }
        }
    }

    /**
     * Decode a `.homedesign` from storage into a new project and return its id.
     * Used by the editor File → Open flow.
     */
    fun openHomedesignFromUri(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read file")
                }
                val home = HomedesignZip.decode(
                    bytes,
                    HomedesignZip.embeddedTextureDirectory(appContext.filesDir),
                )
                val meta = projectRepository.createFromHome(home)
                onOpened(meta.id)
                _events.tryEmit("Opened ${meta.name}")
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Could not open .homedesign")
            }
        }
    }

    fun suggestedSaveFilename(): String =
        "${sanitize(document.home.name)}.homedesign"

    fun openSh3dFromUri(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read file")
                }
                val home = withContext(Dispatchers.Default) {
                    SH3DReader.read(
                        bytes,
                        cacheDirectory = File(appContext.filesDir, "HomeMeshes/${java.util.UUID.randomUUID()}"),
                    )
                }
                val meta = projectRepository.createFromHome(home)
                onOpened(meta.id)
                _events.tryEmit("Opened ${meta.name}")
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Could not open .sh3d")
            }
        }
    }

    /** @deprecated use [openSh3dFromUri] */
    fun notifySh3dUnavailable() {
        _events.tryEmit("Pick an .sh3d file to import")
    }

    fun consumeExport() {
        _state.update { it.copy(exportUri = null, exportMime = null, exportLabel = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    override fun onCleared() {
        autosaveJob?.cancel()
        if (dirty && projectId.isNotBlank()) {
            // Best-effort; ViewModel scope is cancelling — use runBlocking carefully avoided.
            // Flush is also called from screen ON_STOP.
        }
        super.onCleared()
    }

    private fun onDrawWallTap(plan: Vec2, thickness: Double) {
        val start = wallDrawStart
        if (start == null) {
            onDrawWallArm(plan)
        } else if (dist(start, plan) < minDrawnWallCM) {
            // Tap near current endpoint ends the chain (web parity).
            cancelPreview()
            setTool(EditorTool.Select)
        } else {
            onDrawWallCommit(plan, thickness)
        }
    }

    private fun placeOpening(kind: OpeningKind, plan: Vec2, scalePxPerCm: Float) {
        val scale = max(scalePxPerCm.toDouble(), 0.001)
        val home = document.home
        val level = home.selectedLevelID
        val walls = home.walls.filter { level == null || it.level == level }
        val hit = HitTest.closestWall(plan, walls, hitWallCoarsePx / scale) ?: return
        val wall = walls.find { it.id == hit.wallID } ?: return
        val t = projectTOnWall(wall, plan.x, plan.y)
        val next = applyAddOpening(home, kind, wall.id, t)
        if (next === home) return
        document.replaceHome(next)
        val added = next.doorsAndWindows.lastOrNull()?.piece?.id
        if (added != null) document.setSelection(Selection.Opening(added))
        setTool(EditorTool.Select)
        markDirty(coalesce = false)
        publish()
    }

    private fun onDimensionTap(plan: Vec2) {
        val walls = wallsOnLevel(document.home)
        val snap = SnapEngine.snap(plan, walls, snapRadiusCM = 25.0)
        val point = resolveDimensionSnap(snap, plan, walls, DimensionFaceMode.Outer)
        val start = dimensionStart
        if (start == null) {
            dimensionStart = point
            _state.update { it.copy(preview = DrawPreview.Dimension(point, null)) }
        } else {
            dimensionStart = null
            _state.update { it.copy(preview = DrawPreview.None) }
            val next = applyAddDimension(document.home, start, point)
            if (next !== document.home) {
                document.replaceHome(next)
                markDirty(coalesce = false)
                publish()
            }
            setTool(EditorTool.Select)
        }
    }

    private data class ResolvedDraw(
        val point: Vec2,
        val guides: List<SnapGuideLine>,
    )

    private fun resolveDrawPoint(
        planPt: Vec2,
        walls: List<com.homedesign.android.domain.model.Wall>,
        scalePxPerCm: Float,
        from: Vec2?,
    ): ResolvedDraw {
        val radius = 24.0 / max(scalePxPerCm.toDouble(), 0.001)
        val snap = SnapEngine.snap(planPt, walls, radius)
        var point = snap.snappedPoint
        if (from != null && snap.target !is SnapTarget.WallEndpoint) {
            point = AngleSnap.snapWallEnd(from, point, walls)
        }
        val faceTarget = snap.target as? SnapTarget.WallCentreLine
        if (from != null && faceTarget != null) {
            val host = walls.find { it.id == faceTarget.wallID }
            if (host != null) point = SnapEngine.nearFaceButtPoint(from, point, host)
        }
        val guides = mutableListOf<SnapGuideLine>()
        if (snap.target is SnapTarget.None || snap.target is SnapTarget.Grid) {
            val aligned = SnapEngine.alignmentGuides(point, walls, radius)
            if (aligned.guides.isNotEmpty()) {
                point = aligned.point
                for (g in aligned.guides) {
                    guides.add(SnapGuideLine(g.anchor, point))
                }
            }
        } else if (from != null) {
            // Angle-snap cue: dashed line along preferred axis when end moved.
            val raw = AngleSnap.snapWallEnd(from, planPt, walls)
            if (dist(raw, planPt) > 1e-6) {
                guides.add(SnapGuideLine(from, point))
            }
        }
        if (snap.target is SnapTarget.WallEndpoint ||
            snap.target is SnapTarget.WallMidpoint ||
            snap.target is SnapTarget.WallCentreLine
        ) {
            guides.add(SnapGuideLine(snap.snappedPoint, point))
        }
        return ResolvedDraw(point, guides)
    }

    private fun furnitureMoveGuides(pieceId: String, x: Double, y: Double): List<SnapGuideLine> {
        val piece = document.home.furniture.find { it.id == pieceId } ?: return emptyList()
        val walls = wallsOnLevel(document.home)
        val guides = mutableListOf<SnapGuideLine>()
        val centre = vec(x, y)
        val aligned = SnapEngine.alignmentGuides(centre, walls, 25.0)
        for (g in aligned.guides) {
            val end = when (g.axis) {
                AlignmentAxis.Vertical -> vec(g.anchor.x, y)
                AlignmentAxis.Horizontal -> vec(x, g.anchor.y)
            }
            guides.add(SnapGuideLine(g.anchor, end))
        }
        val proposed = piece.copy(x = x, y = y)
        FurnitureSnap.snapToWallKeepAngle(proposed, walls, furnitureSnapToWallCM)?.let { snapped ->
            // Cue toward nearest wall face centreline projection.
            val face = vec(snapped.x, snapped.y)
            if (dist(centre, face) > 1e-3) {
                guides.add(SnapGuideLine(centre, face))
            }
        }
        return guides
    }

    private fun placeFurniture(catalogId: String?, plan: Vec2, stamp: Boolean) {
        val entry = catalogId?.let { catalogById(it) } ?: return
        val next = applyPlaceFurniture(document.home, entry, plan.x, plan.y)
        document.replaceHome(next)
        if (stamp) {
            markDirty(coalesce = false)
            publish()
        } else {
            setTool(EditorTool.Select)
            markDirty(coalesce = false)
            publish()
        }
    }

    private fun selectAt(plan: Vec2, scalePxPerCm: Float, additive: Boolean = false) {
        val scale = max(scalePxPerCm.toDouble(), 0.001)
        val home = document.home
        val level = home.selectedLevelID
        val walls = home.walls.filter { level == null || it.level == level }
        val rooms = home.rooms.filter { level == null || it.level == level }
        val furniture = home.furniture.filter { it.visible && (level == null || it.level == level) }
        val openings = home.doorsAndWindows.filter { level == null || it.piece.level == level }
        val dims = home.dimensionLines.filter { level == null || it.level == level }
        val labels = home.labels.filter { level == null || it.level == level }

        val epTol = hitEndpointPx / scale
        val wallTol = hitWallCoarsePx / scale
        val furnHalo = hitFurnitureHaloPx / scale

        fun selected() {
            Haptics.commit(appContext)
            publish()
        }

        if (additive) {
            HitTest.closestFurniture(plan, furniture, furnHalo)?.let {
                document.setSelection(toggleFurnitureInSelection(document.selection, it.id))
                selected()
                return
            }
            return
        }

        // Web selectionHit: when an opening is already selected, prefer its resize handles.
        val currentOpeningId = when (val sel = document.selection) {
            is Selection.Opening -> sel.id
            is Selection.OpeningHandle -> sel.id
            else -> null
        }
        if (currentOpeningId != null) {
            val opening = openings.find { it.piece.id == currentOpeningId }
            if (opening != null) {
                HitTest.closestOpeningHandle(plan, opening, wallTol)?.let { handle ->
                    document.setSelection(
                        Selection.OpeningHandle(
                            handle.id,
                            if (handle.side == "start") Selection.Side.Start else Selection.Side.End,
                        ),
                    )
                    selected()
                    return
                }
            }
        }

        HitTest.closestEndpoint(plan, walls, epTol)?.let {
            document.setSelection(Selection.Endpoint(it.wallID, it.atStart))
            selected()
            return
        }
        HitTest.closestFurniture(plan, furniture, furnHalo)?.let {
            document.setSelection(Selection.Furniture(it.id))
            selected()
            return
        }
        HitTest.closestOpening(plan, openings, wallTol)?.let {
            document.setSelection(Selection.Opening(it.id))
            selected()
            return
        }
        HitTest.closestWall(plan, walls, wallTol)?.let {
            document.setSelection(Selection.Wall(it.wallID))
            selected()
            return
        }
        HitTest.closestDimension(plan, dims, wallTol)?.let {
            document.setSelection(Selection.Annotation(it.id, isLabel = false))
            selected()
            return
        }
        HitTest.closestLabel(plan, labels, wallTol)?.let {
            document.setSelection(Selection.Annotation(it.id, isLabel = true))
            selected()
            return
        }
        HitTest.roomContaining(plan, rooms)?.let {
            document.setSelection(Selection.Room(it.id))
            selected()
            return
        }
        document.setSelection(Selection.None)
        publish()
    }

    private fun selectedFurnitureIds(): List<String>? = when (val sel = document.selection) {
        is Selection.Furniture -> listOf(sel.id)
        is Selection.MultiFurniture -> sel.ids
        else -> null
    }

    private fun selectionForFurnitureIds(ids: List<String>): Selection = when {
        ids.isEmpty() -> Selection.None
        ids.size == 1 -> Selection.Furniture(ids.first())
        else -> Selection.MultiFurniture(ids)
    }

    private fun markDirty(coalesce: Boolean = true) {
        dirty = true
        _state.update { it.copy(savedLabel = "Saving…") }
        viewModelScope.launch {
            if (projectId.isNotBlank()) {
                settingsRepository.setLastProjectId(projectId)
                settingsRepository.setEditorSessionDirty(true)
            }
        }
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(3_000)
            saveNow()
        }
        // coalesce reserved for future undo grouping hints
        @Suppress("UNUSED_EXPRESSION")
        coalesce
    }

    private suspend fun saveNow() {
        if (!dirty || projectId.isBlank()) return
        try {
            val thumb = withContext(Dispatchers.Default) {
                runCatching { PlanThumbnail.renderJpeg(document.home) }.getOrNull()
            }
            projectRepository.saveHome(projectId, document.home, thumbnailJpegBytes = thumb)
            dirty = false
            settingsRepository.setEditorSessionDirty(false)
            _state.update { it.copy(savedLabel = "All changes saved", title = document.home.name ?: it.title) }
        } catch (e: Exception) {
            _state.update { it.copy(savedLabel = "Save failed", toast = e.message) }
        }
    }

    private suspend fun flushSaveSuspend() {
        autosaveJob?.cancel()
        if (dirty) saveNow()
    }

    private fun export(block: (com.homedesign.android.domain.model.Home, UnitSystem) -> com.homedesign.android.domain.export.ExportFile?) {
        viewModelScope.launch {
            flushSaveSuspend()
            val units = settingsRepository.getSettings().unitSystem
            val file = block(document.home, units)
            if (file == null) {
                _events.tryEmit("Nothing to export")
                return@launch
            }
            val out = writeExportFile(file.filename, file.bytes)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                out,
            )
            val mime = when {
                file.filename.endsWith(".pdf", true) -> "application/pdf"
                file.filename.endsWith(".dxf", true) -> "application/dxf"
                else -> "application/octet-stream"
            }
            _state.update {
                it.copy(exportUri = uri, exportMime = mime, exportLabel = file.filename)
            }
            _events.tryEmit("Exported ${file.filename}")
        }
    }

    private fun writeExportFile(filename: String, bytes: ByteArray): File {
        val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeBytes(bytes)
        return file
    }

    private fun sanitize(name: String?): String =
        (name ?: "plan").trim().ifEmpty { "plan" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)

    private fun traceFileFor(projectId: String): File =
        File(File(appContext.filesDir, "traces").apply { mkdirs() }, "$projectId.jpg")

    private fun decodeTraceBitmap(uri: Uri): Pair<Bitmap, String?>? {
        val upright = SketchImagePrep.decodeUpright(appContext, uri) ?: return null
        val scaled = scaleTraceBitmap(upright)
        if (scaled !== upright) upright.recycle()
        val path = if (projectId.isNotBlank()) {
            val out = traceFileFor(projectId)
            runCatching {
                FileOutputStream(out).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                }
                out.absolutePath
            }.getOrNull()
        } else {
            null
        }
        return scaled to path
    }

    private fun scaleTraceBitmap(src: Bitmap, maxEdge: Int = 2048): Bitmap {
        val w = src.width
        val h = src.height
        val longEdge = max(w, h)
        if (longEdge <= maxEdge) return src
        val scale = maxEdge.toFloat() / longEdge.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private suspend fun restoreTraceFromDisk(projectId: String) {
        val file = traceFileFor(projectId)
        if (!file.exists()) return
        val bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(file.absolutePath)
        } ?: return
        val image = bitmap.asImageBitmap()
        _state.update {
            it.copy(
                trace = TraceUnderlayState(
                    image = image,
                    widthCM = TRACE_DEFAULT_CM,
                    pixelWidth = bitmap.width,
                    pixelHeight = bitmap.height,
                    filePath = file.absolutePath,
                ),
            )
        }
    }

    private fun publish(
        savedLabel: String? = null,
        title: String? = null,
        toast: String? = null,
    ) {
        _state.update {
            it.copy(
                loading = false,
                home = document.home,
                selection = document.selection,
                canUndo = document.undo.canUndo,
                canRedo = document.undo.canRedo,
                title = title ?: document.home.name ?: it.title,
                savedLabel = savedLabel ?: it.savedLabel,
                toast = toast ?: it.toast,
                error = null,
                hasClipboard = clipboard != null,
            )
        }
    }
}

private sealed interface OpeningDragGesture {
    val openingID: String

    data class Slide(
        override val openingID: String,
        val wallID: String,
        val initialCenterT: Double,
        val startProjectedT: Double,
    ) : OpeningDragGesture

    data class Resize(
        override val openingID: String,
        val wallID: String,
        val side: ResizeSide,
    ) : OpeningDragGesture
}

private data class BowDragGesture(
    val wallID: String,
    val spanIndex: Int,
    val useProfile: Boolean,
)

private data class BreakpointDragGesture(
    val wallID: String,
    val breakIndex: Int,
    val lastT: Double? = null,
)
