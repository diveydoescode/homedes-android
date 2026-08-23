package com.homedesign.android.presentation.editor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homedesign.android.domain.catalog.catalogById
import com.homedesign.android.domain.editor.EditorDocument
import com.homedesign.android.domain.editor.applyAddChainWall
import com.homedesign.android.domain.editor.applyAddDimension
import com.homedesign.android.domain.editor.applyPlaceFurniture
import com.homedesign.android.domain.editor.applyRenameFurniture
import com.homedesign.android.domain.editor.applyRenameRoom
import com.homedesign.android.domain.editor.applyWallThickness
import com.homedesign.android.domain.editor.commitRectangleRoom
import com.homedesign.android.domain.editor.deleteSelection
import com.homedesign.android.domain.export.exportDXF
import com.homedesign.android.domain.export.exportPDF
import com.homedesign.android.domain.geom.AngleSnap
import com.homedesign.android.domain.geom.HitTest
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.geom.exteriorThicknessCM
import com.homedesign.android.domain.geom.hitEndpointPx
import com.homedesign.android.domain.geom.hitFurnitureHaloPx
import com.homedesign.android.domain.geom.hitWallCoarsePx
import com.homedesign.android.domain.geom.interiorThicknessCM
import com.homedesign.android.domain.geom.minDrawnWallCM
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun load(projectId: String) {
        if (projectId.isBlank()) return
        this.projectId = projectId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, projectId = projectId) }
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
                publish(savedLabel = "All changes saved", title = meta?.name ?: home.name ?: "Untitled")
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Failed to load project")
                }
            }
        }
    }

    fun setTool(tool: EditorTool) {
        wallDrawStart = null
        dimensionStart = null
        document.setSelection(Selection.None)
        _state.update {
            it.copy(tool = tool, selection = Selection.None, preview = DrawPreview.None)
        }
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

    fun onPlanTap(plan: Vec2, scalePxPerCm: Float) {
        when (val tool = _state.value.tool) {
            EditorTool.Select -> selectAt(plan, scalePxPerCm)
            is EditorTool.DrawWall -> onDrawWallTap(plan, tool.thickness)
            EditorTool.DrawRoom -> Unit // drag-based
            EditorTool.Dimension -> onDimensionTap(plan)
            is EditorTool.PlaceFurniture -> placeFurniture(tool.catalogId, plan)
        }
    }

    /** Arm the wall start without committing (used by drag-to-draw). */
    fun onDrawWallArm(plan: Vec2) {
        if (wallDrawStart != null) return
        wallDrawStart = plan
        _state.update { it.copy(preview = DrawPreview.Wall(plan, plan)) }
    }

    fun onDrawWallDrag(plan: Vec2) {
        val start = wallDrawStart ?: run {
            wallDrawStart = plan
            plan
        }
        val snapped = AngleSnap.snapWallEnd(start, plan, document.home.walls)
        _state.update { it.copy(preview = DrawPreview.Wall(start, snapped)) }
    }

    fun onDrawWallCommit(plan: Vec2, thickness: Double = defaultWallThicknessCM) {
        val start = wallDrawStart ?: return
        val end = AngleSnap.snapWallEnd(start, plan, document.home.walls)
        wallDrawStart = null
        _state.update { it.copy(preview = DrawPreview.None) }
        if (com.homedesign.android.domain.geom.dist(start, end) < minDrawnWallCM) return
        val next = applyAddChainWall(document.home, start, end, thickness)
        document.replaceHome(next)
        wallDrawStart = end // chain next segment from this endpoint
        markDirty(coalesce = false)
        publish()
        _state.update { it.copy(preview = DrawPreview.Wall(end, end)) }
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
        _state.update { it.copy(preview = DrawPreview.None) }
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
            wallDrawStart = plan
            _state.update { it.copy(preview = DrawPreview.Wall(plan, plan)) }
        } else {
            onDrawWallCommit(plan, thickness)
        }
    }

    private fun onDimensionTap(plan: Vec2) {
        val start = dimensionStart
        if (start == null) {
            dimensionStart = plan
            _state.update { it.copy(preview = DrawPreview.Dimension(plan, null)) }
        } else {
            dimensionStart = null
            _state.update { it.copy(preview = DrawPreview.None) }
            val next = applyAddDimension(document.home, start, plan)
            if (next !== document.home) {
                document.replaceHome(next)
                markDirty(coalesce = false)
                publish()
            }
            setTool(EditorTool.Select)
        }
    }

    private fun placeFurniture(catalogId: String?, plan: Vec2) {
        val entry = catalogId?.let { catalogById(it) } ?: return
        val next = applyPlaceFurniture(document.home, entry, plan.x, plan.y)
        document.replaceHome(next)
        setTool(EditorTool.Select)
        markDirty(coalesce = false)
        publish()
    }

    private fun selectAt(plan: Vec2, scalePxPerCm: Float) {
        val scale = max(scalePxPerCm.toDouble(), 0.001)
        val home = document.home
        val level = home.selectedLevelID
        val walls = home.walls.filter { level == null || it.level == level }
        val rooms = home.rooms.filter { level == null || it.level == level }
        val furniture = home.furniture.filter { it.visible && (level == null || it.level == level) }
        val openings = home.doorsAndWindows.filter { level == null || it.piece.level == level }
        val dims = home.dimensionLines.filter { level == null || it.level == level }

        val epTol = hitEndpointPx / scale
        val wallTol = hitWallCoarsePx / scale
        val furnHalo = hitFurnitureHaloPx / scale

        HitTest.closestEndpoint(plan, walls, epTol)?.let {
            document.setSelection(Selection.Endpoint(it.wallID, it.atStart))
            publish()
            return
        }
        HitTest.closestFurniture(plan, furniture, furnHalo)?.let {
            document.setSelection(Selection.Furniture(it.id))
            publish()
            return
        }
        HitTest.closestOpening(plan, openings, wallTol)?.let {
            document.setSelection(Selection.Opening(it.id))
            publish()
            return
        }
        HitTest.closestWall(plan, walls, wallTol)?.let {
            document.setSelection(Selection.Wall(it.wallID))
            publish()
            return
        }
        HitTest.closestDimension(plan, dims, wallTol)?.let {
            document.setSelection(Selection.Annotation(it.id, isLabel = false))
            publish()
            return
        }
        HitTest.roomContaining(plan, rooms)?.let {
            document.setSelection(Selection.Room(it.id))
            publish()
            return
        }
        document.setSelection(Selection.None)
        publish()
    }

    private fun markDirty(coalesce: Boolean = true) {
        dirty = true
        _state.update { it.copy(savedLabel = "Saving…") }
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
            projectRepository.saveHome(projectId, document.home)
            dirty = false
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
            val settings = settingsRepository.settings.first()
            val units = if (settings.useMetric) UnitSystem.Metric else UnitSystem.Imperial
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
            )
        }
    }
}
