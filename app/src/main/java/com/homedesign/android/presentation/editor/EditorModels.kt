package com.homedesign.android.presentation.editor

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import com.homedesign.android.domain.geom.TRACE_DEFAULT_CM
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.model.Selection

sealed interface EditorTool {
    data object Select : EditorTool
    data class DrawWall(val thickness: Double = defaultWallThicknessCM) : EditorTool
    data object DrawRoom : EditorTool
    /** Drag an AABB on the plan → custom HomePieceOfFurniture (iOS drawFurnitureBox). */
    data object DrawFurnitureBox : EditorTool
    data object Dimension : EditorTool
    /** [stamp] true keeps PlaceFurniture after each tap (web stamp chip). */
    data class PlaceFurniture(val catalogId: String?, val stamp: Boolean = true) : EditorTool
    data class PlaceOpening(val kind: com.homedesign.android.domain.geom.OpeningKind) : EditorTool
    /** Copy wall finishes onto the next tapped wall(s). */
    data class FormatPainter(val sourceWallID: String) : EditorTool
    /** Tap the plan, then type the annotation text. */
    data object PlaceLabel : EditorTool
    /** Next tap drops the walkthrough pegman and enters Walk. */
    data object PlaceWalker : EditorTool
}

/** Pending custom-furniture rect awaiting a name (plan cm). */
data class PendingFurnitureBox(
    val centerX: Double,
    val centerY: Double,
    val width: Double,
    val depth: Double,
)

/**
 * First-person walk eye on the plan.
 * [x]/[y] are plan cm (Y-down). [angle] is walk yaw in radians
 * (0 looks along plan +Y / world +Z; world XZ = plan XY / 100).
 */
data class WalkPose(
    val x: Double,
    val y: Double,
    val angle: Double,
) {
    val eyeXMeters: Float get() = (x * 0.01).toFloat()
    val eyeZMeters: Float get() = (y * 0.01).toFloat()
    val yawDeg: Float get() = Math.toDegrees(angle).toFloat()
}

/** One-shot plan camera fit request (Stage this room / Fit). */
data class CameraFocusRequest(
    val token: Long,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

/** iOS `DeleteToast` — transient undo pill after delete. */
data class DeleteToast(
    val id: Long = System.currentTimeMillis(),
    val message: String,
)

/** Editor chrome view mode. View3D/Walk reuse Filament; AR uses ARCore+GLES (or CameraX sim). */
enum class EditorViewMode {
    Plan2D,
    View3D,
    Walk,
    AR,
    /** Side-by-side 2D + 3D on large width (≥900dp). */
    Split,
}

/** Temporary dashed snap / alignment cue in plan cm. */
data class SnapGuideLine(val start: Vec2, val end: Vec2)

/** In-progress gesture preview for draw tools / furniture edit. */
sealed interface DrawPreview {
    data object None : DrawPreview
    data class Wall(
        val start: Vec2,
        val end: Vec2,
        val thickness: Double = defaultWallThicknessCM,
        val guides: List<SnapGuideLine> = emptyList(),
    ) : DrawPreview
    data class Room(val from: Vec2, val to: Vec2) : DrawPreview
    /** Live custom-furniture AABB while dragging. */
    data class FurnitureBox(val from: Vec2, val to: Vec2) : DrawPreview
    data class Dimension(val start: Vec2, val end: Vec2?) : DrawPreview
    /** Live furniture drag (wall snap applied on commit). */
    data class FurnitureMove(
        val pieceId: String,
        val x: Double,
        val y: Double,
        val guides: List<SnapGuideLine> = emptyList(),
    ) : DrawPreview
    /** Live furniture rotate (angle snap soft-applied while dragging). */
    data class FurnitureRotate(val pieceId: String, val angle: Double) : DrawPreview
    /** Live wall bow (ephemeral walls while dragging the curve handle). */
    data class WallBow(val walls: List<com.homedesign.android.domain.model.Wall>) : DrawPreview
    /** Live wall endpoint / body move (ephemeral walls + room vertex cascade). */
    data class WallEdit(
        val walls: List<com.homedesign.android.domain.model.Wall>,
        val rooms: List<com.homedesign.android.domain.model.Room>,
        val guides: List<SnapGuideLine> = emptyList(),
    ) : DrawPreview
    /** Live dimension endpoint / offset drag. */
    data class DimensionEdit(
        val dimId: String,
        val xStart: Double,
        val yStart: Double,
        val xEnd: Double,
        val yEnd: Double,
        val offset: Double,
    ) : DrawPreview
    /** Live plan-label drag. */
    data class LabelMove(
        val labelId: String,
        val x: Double,
        val y: Double,
    ) : DrawPreview
}

/** Photo under plan canvas (~35% opacity). Session + optional on-disk path. */
data class TraceUnderlayState(
    val image: ImageBitmap,
    val widthCM: Double = TRACE_DEFAULT_CM,
    val pixelWidth: Int,
    val pixelHeight: Int,
    /** App-private file path when persisted for this project. */
    val filePath: String? = null,
)

data class EditorUiState(
    val projectId: String = "",
    val title: String = "Untitled",
    val savedLabel: String = "All changes saved",
    val home: Home = HomeFactory.emptyHome("Untitled"),
    val selection: Selection = Selection.None,
    val tool: EditorTool = EditorTool.Select,
    val viewMode: EditorViewMode = EditorViewMode.Plan2D,
    val preview: DrawPreview = DrawPreview.None,
    val unitSystem: com.homedesign.android.domain.model.UnitSystem =
        com.homedesign.android.domain.model.UnitSystem.Millimetre,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val toast: String? = null,
    /** iOS DeleteToastPill — shown above the dock; Undo pops last snapshot. */
    val deleteToast: DeleteToast? = null,
    val exportUri: Uri? = null,
    val exportMime: String? = null,
    val exportLabel: String? = null,
    /** Newest-first furniture catalog IDs for the picker Recent chip. */
    val recentFurnitureIds: List<String> = emptyList(),
    val trace: TraceUnderlayState? = null,
    val hasClipboard: Boolean = false,
    /** Draw non-active storeys faded on the plan (iOS ghost / cull). */
    val ghostOtherLevels: Boolean = true,
    /** First-run tip banner; false after dismiss (DataStore). */
    val showEditorTip: Boolean = false,
    /** PlanCanvas zooms/pans to these bounds when [CameraFocusRequest.token] changes. */
    val cameraFocus: CameraFocusRequest? = null,
    /** Plan-cm point waiting for the New label dialog (PlaceLabel tool). */
    val pendingLabelPoint: Vec2? = null,
    /** Custom furniture box awaiting a name after AABB drag. */
    val pendingFurnitureBox: PendingFurnitureBox? = null,
    /** Constrain wall draw / endpoint drag to 8 principal rays. */
    val orthoLock: Boolean = true,
    /** Last pegman drop; Walk camera starts here when set. */
    val walkPose: WalkPose? = null,
)
