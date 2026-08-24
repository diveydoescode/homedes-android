package com.homedesign.android.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.model.Home
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full-screen 3D editor pane: Filament when native libs load, else GLES extrusion.
 * Supports orbit (View3D) and first-person Walk with joystick / WASD.
 */
@Composable
fun Plan3DScreen(
    home: Home,
    cameraMode: Plan3DCameraMode = Plan3DCameraMode.Orbit,
    onBackToPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val useFilament = remember { Plan3DSurfaceView.ensureFilament() }
    var filamentView by remember { mutableStateOf<Plan3DSurfaceView?>(null) }
    var timeHour by remember { mutableFloatStateOf(10f) }
    var outdoor by remember { mutableStateOf(true) }
    var roofs by remember { mutableStateOf(true) }
    var fence by remember { mutableStateOf(false) }
    var joyForward by remember { mutableFloatStateOf(0f) }
    var joyStrafe by remember { mutableFloatStateOf(0f) }
    var keyForward by remember { mutableFloatStateOf(0f) }
    var keyStrafe by remember { mutableFloatStateOf(0f) }
    var walkSnap by remember { mutableStateOf<WalkCameraSnapshot?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun pushMove() {
        val f = (joyForward + keyForward).coerceIn(-1f, 1f)
        val s = (joyStrafe + keyStrafe).coerceIn(-1f, 1f)
        filamentView?.setWalkMove(f, s)
    }

    DisposableEffect(lifecycleOwner, useFilament) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> filamentView?.onHostResume()
                Lifecycle.Event.ON_PAUSE -> filamentView?.onHostPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            filamentView?.destroy()
            filamentView = null
        }
    }

    LaunchedEffect(cameraMode) {
        filamentView?.setCameraMode(cameraMode)
        if (cameraMode != Plan3DCameraMode.Walk) {
            joyForward = 0f
            joyStrafe = 0f
            keyForward = 0f
            keyStrafe = 0f
            filamentView?.setWalkMove(0f, 0f)
            walkSnap = null
        } else {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(timeHour) {
        filamentView?.setTimeOfDay(timeHour)
    }

    LaunchedEffect(cameraMode, useFilament) {
        if (cameraMode != Plan3DCameraMode.Walk || !useFilament) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                walkSnap = filamentView?.walkSnapshot()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (cameraMode != Plan3DCameraMode.Walk) return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp
                if (!down && !up) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.W, Key.DirectionUp -> {
                        keyForward = if (down) 1f else if (keyForward > 0f) 0f else keyForward
                        pushMove()
                        true
                    }
                    Key.S, Key.DirectionDown -> {
                        keyForward = if (down) -1f else if (keyForward < 0f) 0f else keyForward
                        pushMove()
                        true
                    }
                    Key.D, Key.DirectionRight -> {
                        keyStrafe = if (down) 1f else if (keyStrafe > 0f) 0f else keyStrafe
                        pushMove()
                        true
                    }
                    Key.A, Key.DirectionLeft -> {
                        keyStrafe = if (down) -1f else if (keyStrafe < 0f) 0f else keyStrafe
                        pushMove()
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (useFilament) {
            AndroidView(
                factory = { ctx ->
                    Plan3DSurfaceView(ctx).also { view ->
                        filamentView = view
                        view.setOutdoorEnabled(outdoor)
                        view.setRoofsEnabled(roofs)
                        view.setFenceEnabled(fence)
                        view.setTimeOfDay(timeHour)
                        view.setCameraMode(cameraMode)
                        view.setHome(home)
                        view.onHostResume()
                    }
                },
                update = { view ->
                    view.setCameraMode(cameraMode)
                    view.setTimeOfDay(timeHour)
                    view.setOutdoorEnabled(outdoor)
                    view.setRoofsEnabled(roofs)
                    view.setFenceEnabled(fence)
                    view.setHome(home)
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    view.destroy()
                    if (filamentView === view) filamentView = null
                },
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    Plan3DGlView(ctx).also { it.setHome(home) }
                },
                update = { view -> view.setHome(home) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HdTheme.colors.ivory.copy(alpha = 0.92f))
                .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.WbSunny,
                    contentDescription = null,
                    tint = HdTheme.colors.terracotta,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = formatHourLabel(timeHour),
                    style = HdTheme.typography.labelSmall,
                    color = HdTheme.colors.architectInk,
                    modifier = Modifier.width(52.dp),
                )
                Slider(
                    value = timeHour,
                    onValueChange = { timeHour = it },
                    valueRange = 0f..24f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = HdTheme.colors.terracotta,
                        activeTrackColor = HdTheme.colors.terracotta,
                    ),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = HdTheme.colors.terracotta.copy(alpha = 0.2f),
                    selectedLabelColor = HdTheme.colors.architectInk,
                )
                FilterChip(
                    selected = outdoor,
                    onClick = { outdoor = !outdoor },
                    label = { Text("Grass") },
                    colors = chipColors,
                )
                FilterChip(
                    selected = roofs,
                    onClick = { roofs = !roofs },
                    label = { Text("Roofs") },
                    colors = chipColors,
                )
                FilterChip(
                    selected = fence,
                    onClick = { fence = !fence },
                    label = { Text("Fence") },
                    colors = chipColors,
                )
            }
            if (cameraMode == Plan3DCameraMode.Walk) {
                Text(
                    text = "Drag to look · joystick or WASD to move",
                    style = HdTheme.typography.labelSmall,
                    color = HdTheme.colors.architectInk.copy(alpha = 0.65f),
                )
            }
        }

        if (cameraMode == Plan3DCameraMode.Walk && useFilament) {
            WalkMinimap(
                home = home,
                snap = walkSnap,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 168.dp, end = 16.dp),
            )
            WalkJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, bottom = 72.dp),
                onMove = { forward, strafe ->
                    joyForward = forward
                    joyStrafe = strafe
                    pushMove()
                },
            )
        }

        TextButton(
            onClick = onBackToPlan,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Text("Back to 2D plan", color = HdTheme.colors.terracotta)
        }
    }
}

private fun formatHourLabel(hour: Float): String {
    val h = hour.roundToInt().coerceIn(0, 24) % 24
    return "%02d:00".format(h)
}

@Composable
private fun WalkMinimap(
    home: Home,
    snap: WalkCameraSnapshot?,
    modifier: Modifier = Modifier,
) {
    val level = home.selectedLevelID
    val walls = remember(home, level) {
        home.walls.filter { level == null || it.level == level }
    }
    val ink = HdTheme.colors.architectInk
    val paper = HdTheme.colors.paper
    val accent = HdTheme.colors.terracotta
    val hairline = HdTheme.colors.hairline

    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(paper.copy(alpha = 0.92f))
            .border(1.dp, hairline, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 4f
            val s = snap
            val minX: Float
            val maxX: Float
            val minZ: Float
            val maxZ: Float
            if (s != null) {
                val r = (s.radius * 1.15f).coerceAtLeast(2f)
                minX = s.centerX - r
                maxX = s.centerX + r
                minZ = s.centerZ - r
                maxZ = s.centerZ + r
            } else {
                var loX = Float.POSITIVE_INFINITY
                var hiX = Float.NEGATIVE_INFINITY
                var loZ = Float.POSITIVE_INFINITY
                var hiZ = Float.NEGATIVE_INFINITY
                for (w in walls) {
                    val ax = (w.startX * 0.01).toFloat()
                    val az = (w.startY * 0.01).toFloat()
                    val bx = (w.endX * 0.01).toFloat()
                    val bz = (w.endY * 0.01).toFloat()
                    loX = minOf(loX, ax, bx); hiX = maxOf(hiX, ax, bx)
                    loZ = minOf(loZ, az, bz); hiZ = maxOf(hiZ, az, bz)
                }
                if (!loX.isFinite()) {
                    loX = -5f; hiX = 5f; loZ = -5f; hiZ = 5f
                }
                minX = loX; maxX = hiX; minZ = loZ; maxZ = hiZ
            }
            val spanX = (maxX - minX).coerceAtLeast(0.5f)
            val spanZ = (maxZ - minZ).coerceAtLeast(0.5f)
            val scale = minOf(
                (size.width - pad * 2) / spanX,
                (size.height - pad * 2) / spanZ,
            )
            fun mapX(x: Float) = pad + (x - minX) * scale
            fun mapY(z: Float) = pad + (z - minZ) * scale

            for (w in walls) {
                drawLine(
                    color = ink.copy(alpha = 0.85f),
                    start = Offset(mapX((w.startX * 0.01).toFloat()), mapY((w.startY * 0.01).toFloat())),
                    end = Offset(mapX((w.endX * 0.01).toFloat()), mapY((w.endY * 0.01).toFloat())),
                    strokeWidth = 2.5f,
                )
            }

            if (s != null) {
                val cx = mapX(s.eyeX)
                val cy = mapY(s.eyeZ)
                val yaw = Math.toRadians(s.yawDeg.toDouble())
                // Walk forward is +Z when yaw=0 → screen +Y
                val fx = sin(yaw).toFloat()
                val fz = cos(yaw).toFloat()
                val len = 10f
                val tip = Offset(cx + fx * len, cy + fz * len)
                val px = -fz
                val pz = fx
                val path = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(cx + px * 4.5f - fx * 3f, cy + pz * 4.5f - fz * 3f)
                    lineTo(cx - px * 4.5f - fx * 3f, cy - pz * 4.5f - fz * 3f)
                    close()
                }
                drawCircle(color = accent.copy(alpha = 0.35f), radius = 5f, center = Offset(cx, cy))
                drawPath(path, color = accent)
                drawPath(path, color = ink.copy(alpha = 0.5f), style = Stroke(width = 1.2f))
            }
        }
    }
}

@Composable
private fun WalkJoystick(
    onMove: (forward: Float, strafe: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = 112.dp
    val knob = 44.dp
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxTravelPx = with(LocalDensity.current) { ((size - knob) / 2f).toPx() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(HdTheme.colors.architectInk.copy(alpha = 0.28f))
            .border(1.dp, HdTheme.colors.hairline, CircleShape)
            .pointerInput(maxTravelPx) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val next = knobOffset + dragAmount
                        val len = sqrt(next.x * next.x + next.y * next.y)
                        knobOffset = if (len > maxTravelPx && len > 0f) {
                            next * (maxTravelPx / len)
                        } else {
                            next
                        }
                        val strafe = (knobOffset.x / maxTravelPx).coerceIn(-1f, 1f)
                        val forward = (-knobOffset.y / maxTravelPx).coerceIn(-1f, 1f)
                        onMove(forward, strafe)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt())
                }
                .size(knob)
                .clip(CircleShape)
                .background(HdTheme.colors.paper.copy(alpha = 0.95f))
                .border(1.dp, HdTheme.colors.terracotta, CircleShape),
        )
    }
}
