package com.homedesign.android.presentation.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.catalog.catalogById
import com.homedesign.android.domain.export.PlanBounds
import com.homedesign.android.domain.export.computePlanBounds
import com.homedesign.android.domain.geom.ArcWallGeometry
import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.FurnitureSymbolClassifier
import com.homedesign.android.domain.geom.FurnitureSymbolKind
import com.homedesign.android.domain.geom.HitTest
import com.homedesign.android.domain.geom.OpeningBinding
import com.homedesign.android.domain.geom.OpeningSymbol
import com.homedesign.android.domain.geom.OpeningSymbolKind
import com.homedesign.android.domain.geom.RoomGeometry
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.geom.WallSegmentation
import com.homedesign.android.domain.geom.WallStyleMutation
import com.homedesign.android.domain.geom.effectiveProfile
import com.homedesign.android.domain.geom.furnitureSnapFront
import com.homedesign.android.domain.geom.gridMinApparentPx
import com.homedesign.android.domain.editor.signedDimensionOffset
import com.homedesign.android.domain.geom.hitCurveHandlePx
import com.homedesign.android.domain.geom.hitEndpointPx
import com.homedesign.android.domain.geom.hitFurnitureHaloPx
import com.homedesign.android.domain.geom.hitWallEdgePx
import com.homedesign.android.domain.geom.minDragToCommitBoxPx
import com.homedesign.android.domain.geom.openingHoleFromBinding
import com.homedesign.android.domain.geom.roomLabelMinM2
import com.homedesign.android.domain.geom.rotateHandleHit
import com.homedesign.android.domain.geom.rotateHandlePosition
import com.homedesign.android.domain.geom.snapFurnitureAngle
import com.homedesign.android.domain.geom.snapGridDefaultCM
import com.homedesign.android.domain.geom.traceHeightCM
import com.homedesign.android.domain.geom.traceOpacity
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.DimensionLine
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.model.Wall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val EMPTY_EXTENT_CM = 1000.0
private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 10f
private val GRID_STEPS = doubleArrayOf(1.0, 5.0, 10.0, 25.0, 50.0, 100.0)

private sealed interface SelectDragKind {
    data object Pan : SelectDragKind
    data object Opening : SelectDragKind
    data object BowHandle : SelectDragKind
    data class FurnitureMove(
        val pieceId: String,
        val startX: Double,
        val startY: Double,
        val startPlan: Vec2,
    ) : SelectDragKind
    data class FurnitureRotate(
        val pieceId: String,
        val startAngle: Double,
        val startCursorAngle: Double,
        val centerX: Double,
        val centerY: Double,
    ) : SelectDragKind
    data class DimensionEnd(
        val dimId: String,
        val atStart: Boolean,
    ) : SelectDragKind
    data class DimensionOffset(
        val dimId: String,
    ) : SelectDragKind
}

private const val LONG_PRESS_MS = 450L

/** Inactive storey underlay opacity (iOS 3D ghost ~25%). */
private const val GHOST_LEVEL_ALPHA = 0.22f

@Composable
fun PlanCanvas(
    home: Home,
    selection: Selection,
    tool: EditorTool,
    preview: DrawPreview,
    unitSystem: UnitSystem = UnitSystem.Metric,
    trace: TraceUnderlayState? = null,
    /** Draw non-selected levels faded under the active plan. */
    ghostOtherLevels: Boolean = true,
    /** When true (hardware Shift/Ctrl/Meta), taps toggle furniture into multi-select. */
    additiveSelect: Boolean = false,
    /** One-shot fit to room/plan bounds (Stage this room). */
    cameraFocus: CameraFocusRequest? = null,
    onTap: (plan: Vec2, scalePxPerCm: Float, additive: Boolean) -> Unit,
    onDrawWallArm: (Vec2, Float) -> Unit,
    onDrawWallDrag: (Vec2, Float) -> Unit,
    onDrawWallCommit: (Vec2, Float) -> Unit,
    onDrawRoomDrag: (from: Vec2, to: Vec2) -> Unit,
    onDrawRoomCommit: (from: Vec2, to: Vec2) -> Unit,
    onCancelPreview: () -> Unit,
    tryBeginOpeningDrag: (plan: Vec2, scalePxPerCm: Float) -> Boolean = { _, _ -> false },
    onOpeningDrag: (plan: Vec2) -> Unit = {},
    onOpeningDragEnd: () -> Unit = {},
    tryBeginBowHandleDrag: (plan: Vec2, scalePxPerCm: Float) -> Boolean = { _, _ -> false },
    onBowHandleDrag: (plan: Vec2) -> Unit = {},
    onBowHandleDragEnd: () -> Unit = {},
    onFurnitureMovePreview: (pieceId: String, x: Double, y: Double) -> Unit = { _, _, _ -> },
    onFurnitureRotatePreview: (pieceId: String, angle: Double) -> Unit = { _, _ -> },
    onFurnitureMoveCommit: (pieceId: String, x: Double, y: Double) -> Unit = { _, _, _ -> },
    onFurnitureRotateCommit: (pieceId: String, angle: Double) -> Unit = { _, _ -> },
    onDimensionEndPreview: (dimId: String, atStart: Boolean, x: Double, y: Double) -> Unit =
        { _, _, _, _ -> },
    onDimensionOffsetPreview: (dimId: String, offset: Double) -> Unit = { _, _ -> },
    onDimensionEditCommit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val paper = HdTheme.colors.paper
    val hairline = HdTheme.colors.hairline
    val ink = HdTheme.colors.architectInk
    val selectionColor = HdTheme.colors.selection
    val cautionColor = HdTheme.colors.caution
    val roomFill = HdTheme.colors.highlight.copy(alpha = 0.55f)
    val furnitureFill = HdTheme.colors.sand
    val terracotta = HdTheme.colors.terracotta
    val density = LocalDensity.current
    val svgCache = rememberFurnitureSvgCache()

    var userZoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var viewportW by remember { mutableFloatStateOf(1f) }
    var viewportH by remember { mutableFloatStateOf(1f) }
    var fitted by remember { mutableStateOf(false) }

    val level = home.selectedLevelID
    val bounds = remember(home.topologyVersion, home.furnitureRevision, level) {
        computePlanBounds(home, level) ?: emptyBounds()
    }

    fun fitScale(): Float {
        val w = max(bounds.maxX - bounds.minX, 1.0)
        val h = max(bounds.maxY - bounds.minY, 1.0)
        val sx = viewportW / (w * 1.4)
        val sy = viewportH / (h * 1.4)
        return max(min(sx, sy).toFloat(), 0.001f)
    }

    fun totalScale(): Float = max(fitScale() * userZoom, 0.001f)

    LaunchedEffect(cameraFocus?.token) {
        val focus = cameraFocus ?: return@LaunchedEffect
        if (viewportW <= 1f || viewportH <= 1f) return@LaunchedEffect
        val homeCx = (bounds.minX + bounds.maxX) / 2.0
        val homeCy = (bounds.minY + bounds.maxY) / 2.0
        val focusW = max(focus.maxX - focus.minX, 1.0)
        val focusH = max(focus.maxY - focus.minY, 1.0)
        val focusCx = (focus.minX + focus.maxX) / 2.0
        val focusCy = (focus.minY + focus.maxY) / 2.0
        val desired = min(
            viewportW / (focusW * 1.4),
            viewportH / (focusH * 1.4),
        ).toFloat().coerceAtLeast(0.001f)
        val base = fitScale()
        userZoom = (desired / base).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val scale = max(base * userZoom, 0.001f)
        panX = (-(focusCx - homeCx) * scale).toFloat()
        panY = (-(focusCy - homeCy) * scale).toFloat()
    }

    fun planToScreen(x: Double, y: Double, scale: Float): Offset {
        val cx = ((bounds.minX + bounds.maxX) / 2.0).toFloat()
        val cy = ((bounds.minY + bounds.maxY) / 2.0).toFloat()
        return Offset(
            (x.toFloat() - cx) * scale + viewportW / 2f + panX,
            (y.toFloat() - cy) * scale + viewportH / 2f + panY,
        )
    }

    fun screenToPlan(offset: Offset, scale: Float): Vec2 {
        val cx = (bounds.minX + bounds.maxX) / 2.0
        val cy = (bounds.minY + bounds.maxY) / 2.0
        val x = (offset.x - viewportW / 2f - panX) / scale + cx
        val y = (offset.y - viewportH / 2f - panY) / scale + cy
        return vec(x, y)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(
                tool,
                home.topologyVersion,
                home.furnitureRevision,
                home.styleVersion,
                selection,
                additiveSelect,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startScreen = down.position
                    var lastScreen = startScreen
                    val scaleAtStart = totalScale()
                    val startPlan = screenToPlan(startScreen, scaleAtStart)
                    var dragged = false
                    var pinched = false
                    var longPressFired = false
                    val downAtMs = System.currentTimeMillis()
                    val roomFrom = if (tool is EditorTool.DrawRoom) startPlan else null
                    val slop = with(density) { 6.dp.toPx() }

                    var selectKind: SelectDragKind =
                        if (tool is EditorTool.Select) {
                            classifySelectDrag(
                                startPlan = startPlan,
                                scale = scaleAtStart.toDouble(),
                                selection = selection,
                                home = home,
                            )
                        } else {
                            SelectDragKind.Pan
                        }
                    var liveMoveX = 0.0
                    var liveMoveY = 0.0
                    var liveAngle = 0.0
                    when (val k = selectKind) {
                        is SelectDragKind.FurnitureMove -> {
                            liveMoveX = k.startX
                            liveMoveY = k.startY
                        }
                        is SelectDragKind.FurnitureRotate -> liveAngle = k.startAngle
                        else -> Unit
                    }

                    while (true) {
                        val remainLongPress = LONG_PRESS_MS - (System.currentTimeMillis() - downAtMs)
                        val event = if (
                            !longPressFired &&
                            !dragged &&
                            !pinched &&
                            tool is EditorTool.Select &&
                            remainLongPress > 0L
                        ) {
                            withTimeoutOrNull(remainLongPress) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }
                        if (event == null) {
                            longPressFired = true
                            onTap(startPlan, scaleAtStart, true)
                            continue
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            pinched = true
                            dragged = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            userZoom = (userZoom * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            panX += pan.x
                            panY += pan.y
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                            continue
                        }

                        val change = pressed.first()
                        lastScreen = change.position
                        val dist = hypot(
                            change.position.x - startScreen.x,
                            change.position.y - startScreen.y,
                        )
                        val pastSlop = dist > slop
                        if (longPressFired) {
                            if (change.positionChanged()) change.consume()
                            continue
                        }
                        val plan = screenToPlan(change.position, totalScale())
                        when {
                            tool is EditorTool.DrawWall && pastSlop -> {
                                if (!dragged) onDrawWallArm(startPlan, scaleAtStart)
                                dragged = true
                                onDrawWallDrag(plan, totalScale())
                            }
                            tool is EditorTool.DrawRoom && pastSlop -> {
                                dragged = true
                                onDrawRoomDrag(roomFrom ?: startPlan, plan)
                            }
                            tool is EditorTool.Select && pastSlop -> {
                                if (!dragged && selectKind is SelectDragKind.Pan) {
                                    when {
                                        tryBeginBowHandleDrag(startPlan, scaleAtStart) ->
                                            selectKind = SelectDragKind.BowHandle
                                        tryBeginOpeningDrag(startPlan, scaleAtStart) ->
                                            selectKind = SelectDragKind.Opening
                                    }
                                }
                                dragged = true
                                when (val k = selectKind) {
                                    is SelectDragKind.FurnitureMove -> {
                                        liveMoveX = k.startX + (plan.x - k.startPlan.x)
                                        liveMoveY = k.startY + (plan.y - k.startPlan.y)
                                        onFurnitureMovePreview(k.pieceId, liveMoveX, liveMoveY)
                                    }
                                    is SelectDragKind.FurnitureRotate -> {
                                        val cursor = atan2(plan.y - k.centerY, plan.x - k.centerX)
                                        liveAngle = snapFurnitureAngle(
                                            k.startAngle + (cursor - k.startCursorAngle),
                                        )
                                        onFurnitureRotatePreview(k.pieceId, liveAngle)
                                    }
                                    is SelectDragKind.DimensionEnd -> {
                                        onDimensionEndPreview(k.dimId, k.atStart, plan.x, plan.y)
                                    }
                                    is SelectDragKind.DimensionOffset -> {
                                        val dim = home.dimensionLines.find { it.id == k.dimId }
                                        if (dim != null) {
                                            val offset = signedDimensionOffset(
                                                vec(dim.xStart, dim.yStart),
                                                vec(dim.xEnd, dim.yEnd),
                                                plan,
                                            )
                                            onDimensionOffsetPreview(k.dimId, offset)
                                        }
                                    }
                                    SelectDragKind.Opening -> onOpeningDrag(plan)
                                    SelectDragKind.BowHandle -> onBowHandleDrag(plan)
                                    SelectDragKind.Pan -> {
                                        panX += change.position.x - change.previousPosition.x
                                        panY += change.position.y - change.previousPosition.y
                                    }
                                }
                            }
                            (tool is EditorTool.Dimension ||
                                tool is EditorTool.PlaceFurniture ||
                                tool is EditorTool.PlaceOpening ||
                                tool is EditorTool.PlaceLabel ||
                                tool is EditorTool.FormatPainter) && pastSlop -> {
                                dragged = true
                                panX += change.position.x - change.previousPosition.x
                                panY += change.position.y - change.previousPosition.y
                            }
                        }
                        if (change.positionChanged()) change.consume()
                    }

                    if (pinched) {
                        if (selectKind is SelectDragKind.FurnitureMove ||
                            selectKind is SelectDragKind.FurnitureRotate ||
                            selectKind is SelectDragKind.BowHandle ||
                            selectKind is SelectDragKind.DimensionEnd ||
                            selectKind is SelectDragKind.DimensionOffset
                        ) {
                            onCancelPreview()
                        }
                        if (selectKind is SelectDragKind.Opening) onOpeningDragEnd()
                        if (selectKind is SelectDragKind.BowHandle) onBowHandleDragEnd()
                        return@awaitEachGesture
                    }

                    if (longPressFired) return@awaitEachGesture

                    val endPlan = screenToPlan(lastScreen, totalScale())
                    when (tool) {
                        is EditorTool.DrawWall -> {
                            if (dragged) onDrawWallCommit(endPlan, totalScale())
                            else onTap(startPlan, totalScale(), false)
                        }
                        EditorTool.DrawRoom -> {
                            val from = roomFrom ?: startPlan
                            val dragPx = hypot(
                                (endPlan.x - from.x) * totalScale(),
                                (endPlan.y - from.y) * totalScale(),
                            )
                            if (dragPx >= minDragToCommitBoxPx) onDrawRoomCommit(from, endPlan)
                            else onCancelPreview()
                        }
                        is EditorTool.FormatPainter -> {
                            if (!dragged) onTap(startPlan, totalScale(), false)
                        }
                        EditorTool.Select -> {
                            when (val k = selectKind) {
                                is SelectDragKind.FurnitureMove -> {
                                    if (dragged) {
                                        onFurnitureMoveCommit(k.pieceId, liveMoveX, liveMoveY)
                                    } else {
                                        onTap(startPlan, totalScale(), additiveSelect)
                                    }
                                }
                                is SelectDragKind.FurnitureRotate -> {
                                    if (dragged) {
                                        onFurnitureRotateCommit(k.pieceId, liveAngle)
                                    } else {
                                        onTap(startPlan, totalScale(), additiveSelect)
                                    }
                                }
                                is SelectDragKind.DimensionEnd,
                                is SelectDragKind.DimensionOffset,
                                -> {
                                    if (dragged) onDimensionEditCommit()
                                    else onTap(startPlan, totalScale(), additiveSelect)
                                }
                                SelectDragKind.Opening -> {
                                    if (dragged) onOpeningDragEnd()
                                    else {
                                        onOpeningDragEnd()
                                        onTap(startPlan, totalScale(), false)
                                    }
                                }
                                SelectDragKind.BowHandle -> {
                                    if (dragged) onBowHandleDragEnd()
                                    else {
                                        onBowHandleDragEnd()
                                        onTap(startPlan, totalScale(), false)
                                    }
                                }
                                SelectDragKind.Pan -> {
                                    if (!dragged) {
                                        onTap(startPlan, totalScale(), additiveSelect)
                                    }
                                }
                            }
                        }
                        else -> {
                            if (!dragged) onTap(startPlan, totalScale(), false)
                        }
                    }
                }
            },
    ) {
        viewportW = size.width
        viewportH = size.height
        if (!fitted && size.width > 1f && size.height > 1f) {
            fitted = true
            userZoom = 1f
            panX = 0f
            panY = 0f
        }

        drawRect(paper)
        val scale = totalScale()
        // Trace underlay sits under the grid (web TRACE_OPACITY 0.35).
        val underlay = trace
        if (underlay != null && underlay.widthCM > 0) {
            val heightCM = traceHeightCM(
                underlay.widthCM,
                underlay.pixelWidth,
                underlay.pixelHeight,
            )
            if (heightCM > 0) {
                val tl = planToScreen(0.0, 0.0, scale)
                val br = planToScreen(underlay.widthCM, heightCM, scale)
                val dstW = (br.x - tl.x).toInt().coerceAtLeast(1)
                val dstH = (br.y - tl.y).toInt().coerceAtLeast(1)
                drawImage(
                    image = underlay.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(underlay.pixelWidth, underlay.pixelHeight),
                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                    dstSize = IntSize(dstW, dstH),
                    alpha = traceOpacity.toFloat(),
                )
            }
        }
        val gridStep = chooseGridStep(scale)
        drawGrid(hairline, gridStep, bounds) { x, y -> planToScreen(x, y, scale) }

        // Ghost inactive storeys (rooms + wall footprints only — not hit-testable).
        if (ghostOtherLevels && level != null) {
            val otherLevelIds = home.levels
                .asSequence()
                .filter { it.visible && it.id != level }
                .map { it.id }
                .toSet()
            if (otherLevelIds.isNotEmpty()) {
                drawGhostLevels(
                    home = home,
                    otherLevelIds = otherLevelIds,
                    ink = ink,
                    roomFill = roomFill,
                    scale = scale,
                    toScreen = { x, y -> planToScreen(x, y, scale) },
                )
            }
        }

        val baseWalls = home.walls.filter { level == null || it.level == level }
        val walls = when (val p = preview) {
            is DrawPreview.WallBow -> p.walls.filter { level == null || it.level == level }
            else -> baseWalls
        }
        val rooms = home.rooms.filter { level == null || it.level == level }
        val furniture = home.furniture.filter { it.visible && (level == null || it.level == level) }
        val openings = home.doorsAndWindows.filter { level == null || it.piece.level == level }
        val dims = home.dimensionLines.filter { level == null || it.level == level }
        val labels = home.labels.filter { level == null || it.level == level }
        val wallsById = walls.associateBy { it.id }
        val cutoutsByWall = WallSegmentation.cutoutsByWallID(walls, openings)

        for (room in rooms) {
            if (room.points.size < 3) continue
            val path = Path()
            val first = planToScreen(room.points[0].x, room.points[0].y, scale)
            path.moveTo(first.x, first.y)
            for (i in 1 until room.points.size) {
                val p = planToScreen(room.points[i].x, room.points[i].y, scale)
                path.lineTo(p.x, p.y)
            }
            path.close()
            val selected = selection is Selection.Room && selection.id == room.id
            val painted = parseWallFill(room.floorColor)?.copy(alpha = if (selected) 0.45f else 0.55f)
            drawPath(
                path,
                painted ?: if (selected) selectionColor.copy(alpha = 0.35f) else roomFill,
            )
        }

        // Walls: even-odd fill with opening cutouts (web hd-wall-fill parity).
        for (wall in walls) {
            val pts = if (ArcWallGeometry.isCurved(wall)) {
                ArcWallGeometry.footprint(wall)
            } else {
                WallGeometry.miteredPoints(wall, wallsById)
            }
            if (pts.size < 3) continue
            val holes = (cutoutsByWall[wall.id] ?: emptyList()).mapNotNull { cut ->
                val hole = openingHoleFromBinding(wall, cut.tStart, cut.tEnd)
                hole.takeIf { it.size >= 3 }
            }
            val path = wallPathWithHoles(pts, holes) { x, y -> planToScreen(x, y, scale) }
            val selected = when (selection) {
                is Selection.Wall -> selection.id == wall.id
                is Selection.Endpoint -> selection.wallID == wall.id
                else -> false
            }
            val glass = WallStyleMutation.isGlass(wall)
            val fillColor = parseWallFill(wall.leftSideColor) ?: ink
            drawPath(
                path,
                fillColor.copy(
                    alpha = when {
                        selected -> 0.9f
                        glass -> 0.35f
                        else -> 0.88f
                    },
                ),
            )
            // Architect selection blue stroke when selected (web #378ADD).
            drawPath(
                path,
                if (selected) selectionColor else ink.copy(alpha = if (glass) 0.55f else 0.95f),
                style = Stroke(width = if (selected) max(1.75f, 1.75f / max(scale, 0.001f)) else 1.75f),
            )
        }

        // Selection overlay + endpoint handles (web blue highlight)
        val selectedWallId = when (selection) {
            is Selection.Wall -> selection.id
            is Selection.Endpoint -> selection.wallID
            else -> null
        }
        if (selectedWallId != null) {
            val wall = wallsById[selectedWallId]
            if (wall != null) {
                val pts = if (ArcWallGeometry.isCurved(wall)) {
                    ArcWallGeometry.footprint(wall)
                } else {
                    WallGeometry.miteredPoints(wall, wallsById)
                }
                if (pts.size >= 3) {
                    val path = wallPath(pts) { x, y -> planToScreen(x, y, scale) }
                    drawPath(path, selectionColor.copy(alpha = 0.22f))
                    drawPath(path, selectionColor, style = Stroke(width = 2.5f))
                }
                val r = 5f * density.density
                val start = planToScreen(wall.startX, wall.startY, scale)
                val end = planToScreen(wall.endX, wall.endY, scale)
                val startActive = selection is Selection.Endpoint && selection.atStart
                val endActive = selection is Selection.Endpoint && !selection.atStart
                drawCircle(Color.White, radius = r + 1.5f, center = start)
                drawCircle(
                    if (startActive) selectionColor else cautionColor,
                    radius = r,
                    center = start,
                )
                drawCircle(Color.White, radius = r + 1.5f, center = end)
                drawCircle(
                    if (endActive) selectionColor else cautionColor,
                    radius = r,
                    center = end,
                )
                val hostsOpening = OpeningBinding.bind(listOf(wall), openings).isNotEmpty()
                if (!hostsOpening) {
                    val profile = effectiveProfile(wall)
                    val multi = (profile.breaks?.size ?: 0) > 0
                    val handles = if (multi) {
                        profile.spans.indices.map { i -> ArcWallGeometry.spanHandlePosition(wall, i) }
                    } else {
                        listOf(ArcWallGeometry.handleHitPosition(wall, scale.toDouble()))
                    }
                    val hr = max(5f, (hitCurveHandlePx.toFloat() * 0.35f / max(scale, 0.001f)))
                    for (h in handles) {
                        val c = planToScreen(h.x, h.y, scale)
                        drawCircle(selectionColor.copy(alpha = 0.25f), radius = hr + 4f, center = c)
                        drawCircle(Color.White, radius = hr + 1.5f, center = c)
                        drawCircle(selectionColor, radius = hr, center = c)
                    }
                    // Multi-span curve breakpoints (draggable diamonds via bow-handle hit).
                    val bpR = max(4f, hr * 0.85f)
                    for (bp in ArcWallGeometry.breakpointPositions(wall)) {
                        val c = planToScreen(bp.point.x, bp.point.y, scale)
                        drawCircle(cautionColor.copy(alpha = 0.3f), radius = bpR + 3f, center = c)
                        drawCircle(Color.White, radius = bpR + 1.25f, center = c)
                        drawCircle(cautionColor, radius = bpR, center = c)
                    }
                }
            }
        }

        val binds = OpeningBinding.bind(walls, openings).associateBy { it.openingID }

        for (opening in openings) {
            val piece = opening.piece
            val selected = when (selection) {
                is Selection.Opening -> selection.id == piece.id
                is Selection.OpeningHandle -> selection.id == piece.id
                else -> false
            }
            val activeSide = (selection as? Selection.OpeningHandle)
                ?.takeIf { it.id == piece.id }
                ?.side
            val color = if (selected) selectionColor else ink.copy(alpha = 0.9f)
            val stroke = max(1.5f, 1.5f * density.density)
            val bind = binds[piece.id]
            val wall = bind?.let { wallsById[it.wallID] }

            if (selected && bind != null && wall != null) {
                val hole = openingHoleFromBinding(wall, bind.tStart, bind.tEnd)
                if (hole.size >= 3) {
                    val highlight = wallPath(hole) { x, y -> planToScreen(x, y, scale) }
                    drawPath(highlight, selectionColor.copy(alpha = 0.35f))
                    drawPath(
                        highlight,
                        selectionColor,
                        style = Stroke(width = max(1.75f, 1.75f / max(scale, 0.001f))),
                    )
                }
            } else if (bind == null) {
                // Unbound: thick body stand-in (bound openings use wall even-odd cutouts).
                val hw = piece.width / 2.0
                val c = cos(piece.angle)
                val s = sin(piece.angle)
                drawLine(
                    color = if (selected) selectionColor.copy(alpha = 0.45f) else ink.copy(alpha = 0.25f),
                    start = planToScreen(piece.x - c * hw, piece.y - s * hw, scale),
                    end = planToScreen(piece.x + c * hw, piece.y + s * hw, scale),
                    strokeWidth = max(3f, (piece.depth * scale).toFloat()),
                    cap = StrokeCap.Butt,
                )
            }

            when (OpeningSymbol.classify(opening)) {
                OpeningSymbolKind.Operable -> {
                    for (sash in opening.sashes) {
                        val arc = OpeningSymbol.sashArc(sash, opening)
                        drawSashArc(arc, scale, color, stroke) { x, y -> planToScreen(x, y, scale) }
                    }
                }
                OpeningSymbolKind.FixedWindow -> {
                    val lines = if (bind != null && wall != null) {
                        OpeningSymbol.glassLines(bind, wall)
                    } else {
                        OpeningSymbol.glassLinesUnbound(opening)
                    }
                    for (line in lines) {
                        drawLine(
                            color = color,
                            start = planToScreen(line.start.x, line.start.y, scale),
                            end = planToScreen(line.end.x, line.end.y, scale),
                            strokeWidth = stroke,
                        )
                    }
                }
            }

            if (selected) {
                drawOpeningChevron(
                    pieceX = piece.x,
                    pieceY = piece.y,
                    angle = piece.angle,
                    halfWidth = piece.width / 2.0,
                    side = Selection.Side.Start,
                    active = activeSide == Selection.Side.Start,
                    scale = scale,
                    color = selectionColor,
                ) { x, y -> planToScreen(x, y, scale) }
                drawOpeningChevron(
                    pieceX = piece.x,
                    pieceY = piece.y,
                    angle = piece.angle,
                    halfWidth = piece.width / 2.0,
                    side = Selection.Side.End,
                    active = activeSide == Selection.Side.End,
                    scale = scale,
                    color = selectionColor,
                ) { x, y -> planToScreen(x, y, scale) }
            }
        }

        val multiFurnitureIds = (selection as? Selection.MultiFurniture)?.ids?.toSet().orEmpty()
        for (raw in furniture) {
            val piece = applyFurniturePreview(raw, preview, furniture)
            val selected = when (selection) {
                is Selection.Furniture -> selection.id == piece.id
                is Selection.MultiFurniture -> piece.id in multiFurnitureIds
                else -> false
            }
            val entry = piece.catalogID?.let { catalogById(it) }
            val w = piece.widthInPlan ?: piece.width
            val d = piece.depthInPlan ?: piece.depth
            val symbolPaths = svgCache.artForPiece(piece, entry, w, d)
            if (symbolPaths != null) {
                val center = planToScreen(piece.x, piece.y, scale)
                val strokeColor = terracotta.copy(alpha = 0.95f)
                withTransform({
                    translate(center.x, center.y)
                    rotate(Math.toDegrees(piece.angle).toFloat(), pivot = Offset.Zero)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    for (ap in symbolPaths) {
                        drawPath(
                            path = ap.asComposePath(),
                            color = strokeColor,
                            style = Stroke(
                                width = 1.75f / scale,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                }
                if (selected) {
                    val corners = FurnitureGeometry.cornerPoints(piece)
                    val path = Path()
                    val p0 = planToScreen(corners[0].x, corners[0].y, scale)
                    path.moveTo(p0.x, p0.y)
                    for (i in 1 until corners.size) {
                        val p = planToScreen(corners[i].x, corners[i].y, scale)
                        path.lineTo(p.x, p.y)
                    }
                    path.close()
                    drawPath(path, selectionColor.copy(alpha = 0.28f))
                    drawPath(
                        path,
                        selectionColor,
                        style = Stroke(width = max(1.75f, 1.75f / max(scale, 0.001f))),
                    )
                }
            } else {
                val corners = FurnitureGeometry.cornerPoints(piece)
                val path = Path()
                val p0 = planToScreen(corners[0].x, corners[0].y, scale)
                path.moveTo(p0.x, p0.y)
                for (i in 1 until corners.size) {
                    val p = planToScreen(corners[i].x, corners[i].y, scale)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                // Architect selection blue fill+stroke (web FurnitureBox #378ADD).
                // Kind tint from FurnitureSymbolClassifier when SVG symbol art is absent.
                val kind = FurnitureSymbolClassifier.classify(piece, entry)
                val kindFill = symbolKindFill(kind, furnitureFill, terracotta)
                val fill = if (selected) selectionColor.copy(alpha = 0.35f) else kindFill
                val strokeColor = if (selected) selectionColor else terracotta.copy(alpha = 0.95f)
                val roundPillar = kind == FurnitureSymbolKind.Pillar &&
                    (
                        piece.catalogID?.contains("round", ignoreCase = true) == true ||
                            (piece.name ?: entry?.name).orEmpty().contains("round", ignoreCase = true)
                    )
                if (roundPillar) {
                    val center = planToScreen(piece.x, piece.y, scale)
                    withTransform({
                        translate(center.x, center.y)
                        rotate(Math.toDegrees(piece.angle).toFloat(), pivot = Offset.Zero)
                    }) {
                        val rx = ((piece.widthInPlan ?: piece.width) / 2.0 * scale).toFloat()
                        val ry = ((piece.depthInPlan ?: piece.depth) / 2.0 * scale).toFloat()
                        drawOval(
                            color = fill,
                            topLeft = Offset(-rx, -ry),
                            size = Size(rx * 2f, ry * 2f),
                        )
                        drawOval(
                            color = strokeColor,
                            topLeft = Offset(-rx, -ry),
                            size = Size(rx * 2f, ry * 2f),
                            style = Stroke(
                                width = if (selected) max(1.75f, 1.75f / max(scale, 0.001f)) else 2f,
                            ),
                        )
                    }
                } else {
                    drawPath(path, fill)
                    drawPath(
                        path,
                        strokeColor,
                        style = Stroke(width = if (selected) max(1.75f, 1.75f / max(scale, 0.001f)) else 2f),
                    )
                }
                val label = entry?.name ?: piece.name.orEmpty()
                if (label.isNotBlank()) {
                    drawLabel(
                        text = label,
                        at = planToScreen(piece.x, piece.y, scale),
                        color = ink.copy(alpha = 0.7f),
                        sizeSp = 9f,
                        paper = paper,
                        chip = false,
                    )
                }
            }
        }

        // Rotate handle for selected furniture (web furniture-rotate-group)
        if (selection is Selection.Furniture) {
            val raw = furniture.find { it.id == selection.id }
            if (raw != null) {
                val piece = applyFurniturePreview(raw, preview, furniture)
                val handle = rotateHandlePosition(piece, scale.toDouble())
                val halfD = (piece.depthInPlan ?: piece.depth) / 2.0
                val front = furnitureSnapFront(piece.angle)
                val edge = vec(piece.x + front.x * halfD, piece.y + front.y * halfD)
                drawLine(
                    color = selectionColor.copy(alpha = 0.7f),
                    start = planToScreen(edge.x, edge.y, scale),
                    end = planToScreen(handle.x, handle.y, scale),
                    strokeWidth = 1.5f,
                )
                val hc = planToScreen(handle.x, handle.y, scale)
                val hr = 6f
                drawCircle(Color.White, radius = hr + 1.5f, center = hc)
                drawCircle(selectionColor, radius = hr, center = hc)
            }
        }

        for (room in rooms) {
            if (room.points.size < 3) continue
            val areaM2 = RoomGeometry.polygonArea(room) / 10_000.0
            val name = room.name.orEmpty()
            val showArea = areaM2 >= roomLabelMinM2
            if (name.isBlank() && !showArea) continue
            val c = RoomGeometry.centroid(room)
            val lines = buildList {
                if (name.isNotBlank()) add(name)
                if (showArea) add(UnitFormat.area(areaM2, unitSystem))
            }
            drawLabel(
                text = lines.joinToString("\n"),
                at = planToScreen(c.x, c.y, scale),
                color = ink.copy(alpha = 0.65f),
                sizeSp = 11f,
                paper = paper,
                chip = false,
            )
        }

        for (rawDim in dims) {
            val dim = applyDimensionPreview(rawDim, preview)
            val len = hypot(dim.xEnd - dim.xStart, dim.yEnd - dim.yStart)
            if (len < 1e-6) continue
            val nrmX = ((dim.yEnd - dim.yStart) / len) * dim.offset
            val nrmY = (-(dim.xEnd - dim.xStart) / len) * dim.offset
            val ox1 = dim.xStart + nrmX
            val oy1 = dim.yStart + nrmY
            val ox2 = dim.xEnd + nrmX
            val oy2 = dim.yEnd + nrmY
            val a = planToScreen(ox1, oy1, scale)
            val b = planToScreen(ox2, oy2, scale)
            val baseA = planToScreen(dim.xStart, dim.yStart, scale)
            val baseB = planToScreen(dim.xEnd, dim.yEnd, scale)
            val selected =
                selection is Selection.Annotation && !selection.isLabel && selection.id == dim.id
            // Extension ticks (web dim mark).
            val tickOp = if (selected) 0.7f else 0.35f
            val tickW = max(0.8f, 0.8f / max(scale, 0.001f))
            drawLine(ink.copy(alpha = tickOp), baseA, a, strokeWidth = tickW)
            drawLine(ink.copy(alpha = tickOp), baseB, b, strokeWidth = tickW)
            // Selected dim offset line uses caution orange (web #EF9F27).
            drawLine(
                color = if (selected) cautionColor else ink.copy(alpha = 0.7f),
                start = a,
                end = b,
                strokeWidth = if (selected) max(1.75f, 1.75f / max(scale, 0.001f)) else 2f,
            )
            if (selected) {
                val hr = 5f * density.density
                drawCircle(Color.White, radius = hr + 1.5f, center = a)
                drawCircle(cautionColor, radius = hr, center = a)
                drawCircle(Color.White, radius = hr + 1.5f, center = b)
                drawCircle(cautionColor, radius = hr, center = b)
            }
            val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            drawLabel(
                text = UnitFormat.length(len, unitSystem),
                at = mid,
                color = ink,
                sizeSp = 10f,
                paper = paper,
                chip = true,
            )
        }

        for (label in labels) {
            if (label.pitch != null && abs(label.pitch) >= 0.01) continue
            val selected =
                selection is Selection.Annotation && selection.isLabel && selection.id == label.id
            drawLabel(
                text = label.text,
                at = planToScreen(label.x, label.y, scale),
                color = if (selected) selectionColor else ink.copy(alpha = 0.75f),
                sizeSp = 13f,
                paper = paper,
                chip = selected,
            )
        }

        val guideDash = PathEffect.dashPathEffect(
            floatArrayOf(6f / max(scale, 0.001f), 4f / max(scale, 0.001f)),
            0f,
        )
        val guideColor = terracotta.copy(alpha = 0.95f)
        fun drawGuides(guides: List<SnapGuideLine>) {
            for (g in guides) {
                drawLine(
                    color = guideColor,
                    start = planToScreen(g.start.x, g.start.y, scale),
                    end = planToScreen(g.end.x, g.end.y, scale),
                    strokeWidth = max(1f, 1f / max(scale, 0.001f)),
                    pathEffect = guideDash,
                )
            }
        }

        when (val p = preview) {
            is DrawPreview.Wall -> {
                drawGuides(p.guides)
                val len = hypot(p.end.x - p.start.x, p.end.y - p.start.y)
                val a = planToScreen(p.start.x, p.start.y, scale)
                val b = planToScreen(p.end.x, p.end.y, scale)
                if (len > 1e-3) {
                    // Thickness-aware preview polygon (matches committed wall look)
                    val outline = WallGeometry.unjoinedOutline(
                        Wall(
                            id = "_preview",
                            startX = p.start.x,
                            startY = p.start.y,
                            endX = p.end.x,
                            endY = p.end.y,
                            thickness = p.thickness,
                            height = 250.0,
                        ),
                    )
                    val path = wallPath(outline) { x, y -> planToScreen(x, y, scale) }
                    drawPath(path, selectionColor.copy(alpha = 0.28f))
                    drawPath(path, selectionColor, style = Stroke(width = 2f))
                    drawLabel(
                        text = UnitFormat.length(len, unitSystem),
                        at = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f),
                        color = selectionColor,
                        sizeSp = 10f,
                        paper = paper,
                        chip = true,
                    )
                } else {
                    // Armed start point
                    drawCircle(selectionColor, radius = 6f, center = a)
                    drawCircle(Color.White, radius = 3f, center = a)
                }
            }
            is DrawPreview.Room -> {
                val minX = min(p.from.x, p.to.x)
                val maxX = max(p.from.x, p.to.x)
                val minY = min(p.from.y, p.to.y)
                val maxY = max(p.from.y, p.to.y)
                val path = Path()
                val c0 = planToScreen(minX, minY, scale)
                path.moveTo(c0.x, c0.y)
                listOf(maxX to minY, maxX to maxY, minX to maxY).forEach { (x, y) ->
                    val pt = planToScreen(x, y, scale)
                    path.lineTo(pt.x, pt.y)
                }
                path.close()
                drawPath(path, selectionColor.copy(alpha = 0.2f))
                drawPath(path, selectionColor, style = Stroke(width = 2f))
            }
            is DrawPreview.Dimension -> {
                val end = p.end ?: return@Canvas
                val a = planToScreen(p.start.x, p.start.y, scale)
                val b = planToScreen(end.x, end.y, scale)
                drawLine(selectionColor, a, b, strokeWidth = 2f)
                val len = hypot(end.x - p.start.x, end.y - p.start.y)
                if (len > 1e-3) {
                    drawLabel(
                        text = UnitFormat.length(len, unitSystem),
                        at = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f),
                        color = selectionColor,
                        sizeSp = 10f,
                        paper = paper,
                        chip = true,
                    )
                }
            }
            is DrawPreview.FurnitureMove -> drawGuides(p.guides)
            is DrawPreview.FurnitureRotate,
            is DrawPreview.WallBow,
            is DrawPreview.DimensionEdit,
            DrawPreview.None,
            -> Unit
        }
    }
}

private fun applyFurniturePreview(
    piece: HomePieceOfFurniture,
    preview: DrawPreview,
    allFurniture: List<HomePieceOfFurniture> = emptyList(),
): HomePieceOfFurniture {
    when {
        preview is DrawPreview.FurnitureMove && preview.pieceId == piece.id ->
            return piece.copy(x = preview.x, y = preview.y)
        preview is DrawPreview.FurnitureMove -> {
            val dragged = allFurniture.find { it.id == preview.pieceId } ?: return piece
            val gid = dragged.groupID ?: return piece
            if (piece.groupID != gid || !piece.movable) return piece
            val dx = preview.x - dragged.x
            val dy = preview.y - dragged.y
            return if (dx == 0.0 && dy == 0.0) piece
            else piece.copy(x = piece.x + dx, y = piece.y + dy)
        }
        preview is DrawPreview.FurnitureRotate && preview.pieceId == piece.id ->
            return piece.copy(angle = preview.angle)
        else -> return piece
    }
}

private fun applyDimensionPreview(dim: DimensionLine, preview: DrawPreview): DimensionLine {
    val edit = preview as? DrawPreview.DimensionEdit ?: return dim
    if (edit.dimId != dim.id) return dim
    return dim.copy(
        xStart = edit.xStart,
        yStart = edit.yStart,
        xEnd = edit.xEnd,
        yEnd = edit.yEnd,
        offset = edit.offset,
    )
}

private fun classifySelectDrag(
    startPlan: Vec2,
    scale: Double,
    selection: Selection,
    home: Home,
): SelectDragKind {
    val s = max(scale, 0.001)

    if (selection is Selection.Annotation && !selection.isLabel) {
        val dim = home.dimensionLines.find { it.id == selection.id }
        if (dim != null) {
            val dx = dim.xEnd - dim.xStart
            val dy = dim.yEnd - dim.yStart
            val len = hypot(dx, dy)
            if (len > 1e-9) {
                val nx = dy / len
                val ny = -dx / len
                val oa = vec(dim.xStart + nx * dim.offset, dim.yStart + ny * dim.offset)
                val ob = vec(dim.xEnd + nx * dim.offset, dim.yEnd + ny * dim.offset)
                val radius = hitEndpointPx / s
                val dStart = hypot(startPlan.x - oa.x, startPlan.y - oa.y)
                val dEnd = hypot(startPlan.x - ob.x, startPlan.y - ob.y)
                if (dStart <= radius) {
                    return SelectDragKind.DimensionEnd(dim.id, atStart = true)
                }
                if (dEnd <= radius) {
                    return SelectDragKind.DimensionEnd(dim.id, atStart = false)
                }
                val ox = ob.x - oa.x
                val oy = ob.y - oa.y
                val lenSq = ox * ox + oy * oy
                if (lenSq > 1e-12) {
                    val t = max(
                        0.0,
                        min(1.0, ((startPlan.x - oa.x) * ox + (startPlan.y - oa.y) * oy) / lenSq),
                    )
                    val px = oa.x + ox * t
                    val py = oa.y + oy * t
                    val bodyR = hitWallEdgePx / s
                    if (hypot(startPlan.x - px, startPlan.y - py) <= bodyR) {
                        return SelectDragKind.DimensionOffset(dim.id)
                    }
                }
            }
        }
    }

    if (selection !is Selection.Furniture) return SelectDragKind.Pan
    val level = home.selectedLevelID
    val piece = home.furniture.find {
        it.id == selection.id && it.visible && (level == null || it.level == level)
    } ?: return SelectDragKind.Pan
    if (piece.movable == false) return SelectDragKind.Pan
    if (rotateHandleHit(piece, startPlan, s)) {
        return SelectDragKind.FurnitureRotate(
            pieceId = piece.id,
            startAngle = piece.angle,
            startCursorAngle = atan2(startPlan.y - piece.y, startPlan.x - piece.x),
            centerX = piece.x,
            centerY = piece.y,
        )
    }
    val halo = hitFurnitureHaloPx / s
    if (HitTest.furnitureDistance(piece, startPlan, halo) <= 1e-9) {
        return SelectDragKind.FurnitureMove(
            pieceId = piece.id,
            startX = piece.x,
            startY = piece.y,
            startPlan = startPlan,
        )
    }
    return SelectDragKind.Pan
}

private fun DrawScope.drawSashArc(
    arc: com.homedesign.android.domain.geom.SashArc,
    scale: Float,
    color: Color,
    stroke: Float,
    toScreen: (Double, Double) -> Offset,
) {
    val radiusPx = (arc.radius * scale).toFloat()
    if (radiusPx < 1f) return
    val center = toScreen(arc.center.x, arc.center.y)
    var delta = arc.endAngle - arc.startAngle
    while (delta > PI) delta -= 2 * PI
    while (delta < -PI) delta += 2 * PI
    val sweepDeg = Math.toDegrees(delta).toFloat()
    val startDeg = Math.toDegrees(arc.startAngle).toFloat()
    // Android arcs: 0° = 3 o'clock, positive = clockwise; plan angles are +Y-down so matches.
    drawArc(
        color = color,
        startAngle = startDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = Offset(center.x - radiusPx, center.y - radiusPx),
        size = Size(radiusPx * 2, radiusPx * 2),
        style = Stroke(width = stroke),
    )
    val leaf = toScreen(
        arc.center.x + arc.radius * cos(arc.endAngle),
        arc.center.y + arc.radius * sin(arc.endAngle),
    )
    drawLine(color, center, leaf, strokeWidth = stroke)
}

private fun DrawScope.drawLabel(
    text: String,
    at: Offset,
    color: Color,
    sizeSp: Float,
    paper: Color,
    chip: Boolean,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = sizeSp * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val lines = text.split('\n')
        val lineHeight = paint.fontSpacing
        val totalH = lineHeight * lines.size
        var maxW = 0f
        for (line in lines) {
            maxW = max(maxW, paint.measureText(line))
        }
        if (chip) {
            val padX = 6f * density
            val padY = 3f * density
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = paper.toArgb() }
            canvas.nativeCanvas.drawRoundRect(
                at.x - maxW / 2f - padX,
                at.y - totalH / 2f - padY,
                at.x + maxW / 2f + padX,
                at.y + totalH / 2f + padY,
                4f * density,
                4f * density,
                bg,
            )
        }
        var y = at.y - totalH / 2f - paint.ascent()
        for (line in lines) {
            canvas.nativeCanvas.drawText(line, at.x, y, paint)
            y += lineHeight
        }
    }
}

private fun wallPath(
    pts: List<Vec2>,
    toScreen: (Double, Double) -> Offset,
): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    val p0 = toScreen(pts[0].x, pts[0].y)
    path.moveTo(p0.x, p0.y)
    for (i in 1 until pts.size) {
        val p = toScreen(pts[i].x, pts[i].y)
        path.lineTo(p.x, p.y)
    }
    path.close()
    return path
}

private fun wallPathWithHoles(
    outline: List<Vec2>,
    holes: List<List<Vec2>>,
    toScreen: (Double, Double) -> Offset,
): Path {
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    fun addClosed(pts: List<Vec2>) {
        if (pts.isEmpty()) return
        val p0 = toScreen(pts[0].x, pts[0].y)
        path.moveTo(p0.x, p0.y)
        for (i in 1 until pts.size) {
            val p = toScreen(pts[i].x, pts[i].y)
            path.lineTo(p.x, p.y)
        }
        path.close()
    }
    addClosed(outline)
    for (hole in holes) addClosed(hole)
    return path
}

/** Web OpeningMark chevron at jamb ends for resize handles. */
private fun DrawScope.drawOpeningChevron(
    pieceX: Double,
    pieceY: Double,
    angle: Double,
    halfWidth: Double,
    side: Selection.Side,
    active: Boolean,
    scale: Float,
    color: Color,
    toScreen: (Double, Double) -> Offset,
) {
    val sign = if (side == Selection.Side.Start) -1.0 else 1.0
    val c = cos(angle)
    val s = sin(angle)
    val cx = pieceX + sign * c * halfWidth
    val cy = pieceY + sign * s * halfWidth
    val grow = if (active) 1.4 else 1.0
    val size = (6.0 * grow) / max(scale.toDouble(), 0.001)
    val rot = angle + if (side == Selection.Side.Start) PI else 0.0
    val cosR = cos(rot)
    val sinR = sin(rot)
    fun local(lx: Double, ly: Double): Offset {
        val rx = lx * cosR - ly * sinR
        val ry = lx * sinR + ly * cosR
        return toScreen(cx + rx, cy + ry)
    }
    val a = local(-size * 0.5, -size)
    val b = local(size * 0.6, 0.0)
    val d = local(-size * 0.5, size)
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(d.x, d.y)
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = max(1.5f, (1.75f * grow.toFloat()) * density),
            cap = StrokeCap.Round,
        ),
    )
}

/** Accepts `#RRGGBB` or `AARRGGBB` / trailing 6 hex from Sweet Home style colours. */
private fun parseWallFill(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    val hex = raw.removePrefix("#").takeLast(6)
    if (hex.length != 6) return null
    return runCatching {
        Color(android.graphics.Color.parseColor("#$hex"))
    }.getOrNull()
}

private fun DrawScope.drawGhostLevels(
    home: Home,
    otherLevelIds: Set<String>,
    ink: Color,
    roomFill: Color,
    scale: Float,
    toScreen: (Double, Double) -> Offset,
) {
    val ghostRooms = home.rooms.filter { it.level != null && it.level in otherLevelIds }
    val ghostWalls = home.walls.filter { it.level != null && it.level in otherLevelIds }
    val wallsById = ghostWalls.associateBy { it.id }
    for (room in ghostRooms) {
        if (room.points.size < 3) continue
        val path = Path()
        val first = toScreen(room.points[0].x, room.points[0].y)
        path.moveTo(first.x, first.y)
        for (i in 1 until room.points.size) {
            val p = toScreen(room.points[i].x, room.points[i].y)
            path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, roomFill.copy(alpha = GHOST_LEVEL_ALPHA * 0.45f))
        drawPath(path, ink.copy(alpha = GHOST_LEVEL_ALPHA * 0.55f), style = Stroke(width = 1f))
    }
    for (wall in ghostWalls) {
        val pts = if (ArcWallGeometry.isCurved(wall)) {
            ArcWallGeometry.footprint(wall)
        } else {
            WallGeometry.miteredPoints(wall, wallsById)
        }
        if (pts.size < 3) continue
        val path = wallPath(pts, toScreen)
        drawPath(path, ink.copy(alpha = GHOST_LEVEL_ALPHA))
    }
}

private fun emptyBounds() = PlanBounds(
    minX = -EMPTY_EXTENT_CM / 2,
    minY = -EMPTY_EXTENT_CM / 2,
    maxX = EMPTY_EXTENT_CM / 2,
    maxY = EMPTY_EXTENT_CM / 2,
)

/** Soft kind tint for box fallback when SVG symbol art is missing. */
private fun symbolKindFill(kind: FurnitureSymbolKind, sand: Color, terracotta: Color): Color =
    when (kind) {
        FurnitureSymbolKind.Bed,
        FurnitureSymbolKind.Nightstand,
        FurnitureSymbolKind.Dresser,
        FurnitureSymbolKind.Wardrobe,
        -> terracotta.copy(alpha = 0.22f)
        FurnitureSymbolKind.Sofa,
        FurnitureSymbolKind.SofaL,
        FurnitureSymbolKind.Armchair,
        FurnitureSymbolKind.Chair,
        FurnitureSymbolKind.Stool,
        -> terracotta.copy(alpha = 0.18f)
        FurnitureSymbolKind.Toilet,
        FurnitureSymbolKind.Sink,
        FurnitureSymbolKind.Bathtub,
        FurnitureSymbolKind.Shower,
        -> Color(0xFF9FB8CC).copy(alpha = 0.35f)
        FurnitureSymbolKind.Plant,
        FurnitureSymbolKind.Rug,
        FurnitureSymbolKind.Path,
        -> Color(0xFFA8B5A0).copy(alpha = 0.40f)
        FurnitureSymbolKind.Lamp,
        FurnitureSymbolKind.Chandelier,
        -> Color(0xFFE8C547).copy(alpha = 0.28f)
        FurnitureSymbolKind.Pillar,
        FurnitureSymbolKind.Beam,
        FurnitureSymbolKind.Railing,
        -> terracotta.copy(alpha = 0.30f)
        else -> sand.copy(alpha = 0.85f)
    }

private fun chooseGridStep(scalePxPerCm: Float): Double {
    for (candidate in GRID_STEPS) {
        if (candidate * scalePxPerCm >= gridMinApparentPx) return candidate
    }
    return snapGridDefaultCM
}

private fun DrawScope.drawGrid(
    hairline: Color,
    gridStep: Double,
    bounds: PlanBounds,
    planToScreen: (Double, Double) -> Offset,
) {
    val pad = 2000.0
    val minX = bounds.minX - pad
    val maxX = bounds.maxX + pad
    val minY = bounds.minY - pad
    val maxY = bounds.maxY + pad
    val startX = kotlin.math.floor(minX / gridStep) * gridStep
    val startY = kotlin.math.floor(minY / gridStep) * gridStep
    var x = startX
    while (x <= maxX) {
        val a = planToScreen(x, minY)
        val b = planToScreen(x, maxY)
        val major = abs(x % 100.0) < 1e-6
        drawLine(
            hairline.copy(alpha = if (major) 0.55f else 0.28f),
            a,
            b,
            strokeWidth = if (major) 1.25f else 1f,
        )
        x += gridStep
    }
    var y = startY
    while (y <= maxY) {
        val a = planToScreen(minX, y)
        val b = planToScreen(maxX, y)
        val major = abs(y % 100.0) < 1e-6
        drawLine(
            hairline.copy(alpha = if (major) 0.55f else 0.28f),
            a,
            b,
            strokeWidth = if (major) 1.25f else 1f,
        )
        y += gridStep
    }
}
