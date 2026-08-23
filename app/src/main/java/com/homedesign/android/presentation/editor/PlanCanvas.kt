package com.homedesign.android.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.export.PlanBounds
import com.homedesign.android.domain.export.computePlanBounds
import com.homedesign.android.domain.geom.FurnitureGeometry
import com.homedesign.android.domain.geom.Vec2
import com.homedesign.android.domain.geom.WallGeometry
import com.homedesign.android.domain.geom.gridMinApparentPx
import com.homedesign.android.domain.geom.minDragToCommitBoxPx
import com.homedesign.android.domain.geom.snapGridDefaultCM
import com.homedesign.android.domain.geom.vec
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.Selection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val EMPTY_EXTENT_CM = 1000.0
private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 10f
private val GRID_STEPS = doubleArrayOf(1.0, 5.0, 10.0, 25.0, 50.0, 100.0)

@Composable
fun PlanCanvas(
    home: Home,
    selection: Selection,
    tool: EditorTool,
    preview: DrawPreview,
    onTap: (plan: Vec2, scalePxPerCm: Float) -> Unit,
    onDrawWallArm: (Vec2) -> Unit,
    onDrawWallDrag: (Vec2) -> Unit,
    onDrawWallCommit: (Vec2) -> Unit,
    onDrawRoomDrag: (from: Vec2, to: Vec2) -> Unit,
    onDrawRoomCommit: (from: Vec2, to: Vec2) -> Unit,
    onCancelPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = HdTheme.colors.paper
    val hairline = HdTheme.colors.hairline
    val ink = HdTheme.colors.architectInk
    val selectionColor = HdTheme.colors.selection
    val roomFill = HdTheme.colors.highlight.copy(alpha = 0.55f)
    val furnitureFill = HdTheme.colors.sand
    val density = LocalDensity.current

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
            .pointerInput(tool, home.topologyVersion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startScreen = down.position
                    var lastScreen = startScreen
                    val scaleAtStart = totalScale()
                    val startPlan = screenToPlan(startScreen, scaleAtStart)
                    var dragged = false
                    var pinched = false
                    val roomFrom = if (tool is EditorTool.DrawRoom) startPlan else null
                    val slop = with(density) { 6.dp.toPx() }

                    while (true) {
                        val event = awaitPointerEvent()
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
                        val plan = screenToPlan(change.position, totalScale())
                        when {
                            tool is EditorTool.DrawWall && pastSlop -> {
                                if (!dragged) onDrawWallArm(startPlan)
                                dragged = true
                                onDrawWallDrag(plan)
                            }
                            tool is EditorTool.DrawRoom && pastSlop -> {
                                dragged = true
                                onDrawRoomDrag(roomFrom ?: startPlan, plan)
                            }
                            (tool is EditorTool.Select ||
                                tool is EditorTool.Dimension ||
                                tool is EditorTool.PlaceFurniture) && pastSlop -> {
                                dragged = true
                                panX += change.position.x - change.previousPosition.x
                                panY += change.position.y - change.previousPosition.y
                            }
                        }
                        if (change.positionChanged()) change.consume()
                    }

                    if (pinched) return@awaitEachGesture

                    val endPlan = screenToPlan(lastScreen, totalScale())
                    when (tool) {
                        is EditorTool.DrawWall -> {
                            if (dragged) onDrawWallCommit(endPlan)
                            else onTap(startPlan, totalScale())
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
                        else -> {
                            if (!dragged) onTap(startPlan, totalScale())
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
        val gridStep = chooseGridStep(scale)
        drawGrid(hairline, gridStep, bounds) { x, y -> planToScreen(x, y, scale) }

        val walls = home.walls.filter { level == null || it.level == level }
        val rooms = home.rooms.filter { level == null || it.level == level }
        val furniture = home.furniture.filter { it.visible && (level == null || it.level == level) }
        val openings = home.doorsAndWindows.filter { level == null || it.piece.level == level }
        val dims = home.dimensionLines.filter { level == null || it.level == level }
        val wallsById = walls.associateBy { it.id }

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
            drawPath(path, if (selected) selectionColor.copy(alpha = 0.35f) else roomFill)
        }

        for (wall in walls) {
            val pts = WallGeometry.miteredPoints(wall, wallsById)
            if (pts.size < 4) continue
            val path = Path()
            val p0 = planToScreen(pts[0].x, pts[0].y, scale)
            path.moveTo(p0.x, p0.y)
            for (i in 1 until pts.size) {
                val p = planToScreen(pts[i].x, pts[i].y, scale)
                path.lineTo(p.x, p.y)
            }
            path.close()
            val selected = when (selection) {
                is Selection.Wall -> selection.id == wall.id
                is Selection.Endpoint -> selection.wallID == wall.id
                else -> false
            }
            drawPath(path, if (selected) selectionColor else ink)
        }

        for (opening in openings) {
            val piece = opening.piece
            val hw = piece.width / 2.0
            val c = cos(piece.angle)
            val s = sin(piece.angle)
            val a = planToScreen(piece.x - c * hw, piece.y - s * hw, scale)
            val b = planToScreen(piece.x + c * hw, piece.y + s * hw, scale)
            val selected = selection is Selection.Opening && selection.id == piece.id
            drawLine(
                color = if (selected) selectionColor else ink.copy(alpha = 0.85f),
                start = a,
                end = b,
                strokeWidth = max(3f, (piece.depth * scale).toFloat()),
                cap = StrokeCap.Butt,
            )
        }

        for (piece in furniture) {
            val corners = FurnitureGeometry.cornerPoints(piece)
            val path = Path()
            val p0 = planToScreen(corners[0].x, corners[0].y, scale)
            path.moveTo(p0.x, p0.y)
            for (i in 1 until corners.size) {
                val p = planToScreen(corners[i].x, corners[i].y, scale)
                path.lineTo(p.x, p.y)
            }
            path.close()
            val selected = selection is Selection.Furniture && selection.id == piece.id
            drawPath(path, if (selected) selectionColor.copy(alpha = 0.45f) else furnitureFill)
            drawPath(
                path,
                if (selected) selectionColor else ink.copy(alpha = 0.7f),
                style = Stroke(width = 2f),
            )
        }

        for (dim in dims) {
            val len = hypot(dim.xEnd - dim.xStart, dim.yEnd - dim.yStart)
            if (len < 1e-6) continue
            val nrmX = ((dim.yEnd - dim.yStart) / len) * dim.offset
            val nrmY = (-(dim.xEnd - dim.xStart) / len) * dim.offset
            val a = planToScreen(dim.xStart + nrmX, dim.yStart + nrmY, scale)
            val b = planToScreen(dim.xEnd + nrmX, dim.yEnd + nrmY, scale)
            val selected =
                selection is Selection.Annotation && !selection.isLabel && selection.id == dim.id
            drawLine(
                color = if (selected) selectionColor else ink.copy(alpha = 0.65f),
                start = a,
                end = b,
                strokeWidth = 2f,
            )
        }

        when (val p = preview) {
            is DrawPreview.Wall -> {
                val a = planToScreen(p.start.x, p.start.y, scale)
                val b = planToScreen(p.end.x, p.end.y, scale)
                drawLine(selectionColor, a, b, strokeWidth = 3f, cap = StrokeCap.Round)
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
            }
            DrawPreview.None -> Unit
        }
    }
}

private fun emptyBounds() = PlanBounds(
    minX = -EMPTY_EXTENT_CM / 2,
    minY = -EMPTY_EXTENT_CM / 2,
    maxX = EMPTY_EXTENT_CM / 2,
    maxY = EMPTY_EXTENT_CM / 2,
)

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
