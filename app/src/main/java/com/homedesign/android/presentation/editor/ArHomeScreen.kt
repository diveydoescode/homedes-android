package com.homedesign.android.presentation.editor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.homedesign.android.core.ui.HdSfIcons
import com.homedesign.android.core.ui.SfIcon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.ArCoreApk
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.model.HomePieceOfFurniture
import com.homedesign.android.domain.render3d.HomeExtrusion
import kotlin.math.min
import kotlinx.coroutines.delay

private enum class ArMode {
    Checking,
    ArCore,
    CameraSim,
    Denied,
}

/**
 * AR place-in-room. Prefers real ARCore (GLES, not Filament/SceneView — Filament stays 1.56
 * for View3D/Walk). Falls back to CameraX preview + footprint overlay when ARCore is absent.
 *
 * When [soloFurnitureId] is set (furniture selected in 2D), places only that piece's procedural mesh.
 */
@Composable
fun ArHomeScreen(
    home: Home,
    onBackToPlan: () -> Unit,
    onOpenWalk: () -> Unit,
    modifier: Modifier = Modifier,
    soloFurnitureId: String? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var mode by remember { mutableStateOf(ArMode.Checking) }
    var status by remember { mutableStateOf("Checking AR…") }
    var arView by remember { mutableStateOf<ArHomeGlView?>(null) }
    var modelScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(modelScale, arView) {
        arView?.setModelScale(modelScale)
    }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (!granted) {
            mode = ArMode.Denied
            status = "Camera permission required for AR"
        }
    }

    LaunchedEffect(cameraGranted, activity, soloFurnitureId) {
        if (!cameraGranted) {
            requestCamera.launch(Manifest.permission.CAMERA)
            return@LaunchedEffect
        }
        mode = ArMode.Checking
        status = "Checking ARCore…"
        val supported = try {
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED,
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
                -> true
                ArCoreApk.Availability.UNKNOWN_CHECKING,
                ArCoreApk.Availability.UNKNOWN_TIMED_OUT,
                -> {
                    // Brief wait then re-check once
                    kotlinx.coroutines.delay(800)
                    val again = ArCoreApk.getInstance().checkAvailability(context)
                    again == ArCoreApk.Availability.SUPPORTED_INSTALLED ||
                        again == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD ||
                        again == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
                }
                else -> false
            }
        } catch (t: Throwable) {
            Log.w("ArHome", "ARCore check failed", t)
            false
        }
        if (supported && activity != null) {
            mode = ArMode.ArCore
            status = if (soloFurnitureId != null) {
                "Move phone to find a floor, then tap to place this piece"
            } else {
                "Move phone to find a floor, then tap to place"
            }
        } else {
            mode = ArMode.CameraSim
            status = if (soloFurnitureId != null) {
                "ARCore not on this device — camera preview + piece overlay (not tracked AR)"
            } else {
                "ARCore not on this device — camera preview + plan overlay (not tracked AR)"
            }
        }
    }

    DisposableEffect(lifecycleOwner, mode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (mode == ArMode.ArCore) arView?.onHostResume()
                Lifecycle.Event.ON_PAUSE -> if (mode == ArMode.ArCore) arView?.onHostPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            arView?.onHostPause()
            arView?.destroySession()
            arView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (mode) {
            ArMode.ArCore -> {
                AndroidView(
                    factory = { ctx ->
                        ArHomeGlView(ctx).also { view ->
                            view.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            view.onStatus = { msg -> status = msg }
                            view.setHome(home, soloFurnitureId)
                            arView = view
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                view.onHostResume()
                            }
                        }
                    },
                    update = { view ->
                        view.setHome(home, soloFurnitureId)
                        view.setModelScale(modelScale)
                        view.onStatus = { msg -> status = msg }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ArMode.CameraSim -> {
                CameraSimOverlay(
                    home = home,
                    soloFurnitureId = soloFurnitureId,
                    modelScale = modelScale,
                    onStatus = { status = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ArMode.Checking -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Preparing AR…", color = Color.White)
                }
            }
            ArMode.Denied -> {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(28.dp)
                        .background(HdTheme.colors.ivory, RoundedCornerShape(20.dp))
                        .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SfIcon(
                        HdSfIcons.arkit,
                        contentDescription = null,
                        tint = HdTheme.colors.terracotta,
                        size = 48.dp,
                    )
                    Text(
                        "Camera permission needed",
                        style = HdTheme.typography.titleMedium,
                        color = HdTheme.colors.architectInk,
                    )
                    Text(
                        "AR uses the rear camera to place your plan on a real floor.",
                        style = HdTheme.typography.bodyMedium,
                        color = HdTheme.colors.architectInk.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { requestCamera.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = HdTheme.colors.terracotta),
                    ) {
                        Text("Allow camera")
                    }
                }
            }
        }

        // Status chip
        if (mode == ArMode.ArCore || mode == ArMode.CameraSim) {
            Text(
                text = status,
                style = HdTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        if (mode == ArMode.ArCore || mode == ArMode.CameraSim) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Scale",
                        style = HdTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Slider(
                        value = modelScale,
                        onValueChange = { modelScale = it },
                        valueRange = 0.25f..2.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = HdTheme.colors.terracotta,
                            activeTrackColor = HdTheme.colors.terracotta,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                    )
                    Text(
                        "${(modelScale * 100f).toInt()}%",
                        style = HdTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.widthIn(min = 40.dp),
                        textAlign = TextAlign.End,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBackToPlan) {
                        Text("Back to 2D plan", color = Color.White)
                    }
                    Button(
                        onClick = onOpenWalk,
                        colors = ButtonDefaults.buttonColors(containerColor = HdTheme.colors.architectInk),
                    ) {
                        Text("Walkthrough")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBackToPlan) {
                    Text("Back to 2D plan", color = Color.White)
                }
                Button(
                    onClick = onOpenWalk,
                    colors = ButtonDefaults.buttonColors(containerColor = HdTheme.colors.architectInk),
                ) {
                    Text("Walkthrough")
                }
            }
        }
    }
}

/** Honest non-ARCore fallback: live CameraX preview + tappable footprint overlay. */
@Composable
private fun CameraSimOverlay(
    home: Home,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    soloFurnitureId: String? = null,
    modelScale: Float = 1f,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var placed by remember { mutableStateOf(false) }
    var placeAt by remember { mutableStateOf(Offset.Zero) }
    val scene = remember(home, soloFurnitureId) {
        if (soloFurnitureId != null) HomeExtrusion.buildFurnitureOnly(home, soloFurnitureId)
        else HomeExtrusion.build(home)
    }
    val soloPiece: HomePieceOfFurniture? = remember(home, soloFurnitureId) {
        soloFurnitureId?.let { id ->
            home.furniture.find { it.id == id }
                ?: home.doorsAndWindows.find { it.piece.id == id }?.piece
        }
    }

    LaunchedEffect(soloFurnitureId) {
        onStatus(
            if (soloFurnitureId != null) {
                "Simulation: tap to place piece outline (no ARCore tracking)"
            } else {
                "Simulation: tap to place plan outline (no ARCore tracking)"
            },
        )
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener(
                        {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                )
                            } catch (t: Throwable) {
                                Log.e("ArHome", "CameraX bind failed", t)
                                onStatus("Camera preview failed")
                            }
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        placeAt = offset
                        placed = true
                        onStatus(
                            if (soloFurnitureId != null) {
                                "Piece outline placed (overlay only — not ARCore tracking)"
                            } else {
                                "Plan outline placed (overlay only — not ARCore tracking)"
                            },
                        )
                    }
                },
        ) {
            if (!placed) return@Canvas
            val piece = soloPiece
            val scaleMul = modelScale.coerceIn(0.25f, 2.5f)
            if (piece != null) {
                val scale = min(size.width, size.height) * 0.0011f * scaleMul
                val boxW = (piece.width * scale).toFloat().coerceIn(32f, size.width * 0.7f)
                val boxH = (piece.depth * scale).toFloat().coerceIn(24f, size.height * 0.7f)
                val left = placeAt.x - boxW / 2f
                val top = placeAt.y - boxH / 2f
                drawRect(
                    color = Color(0xFFB85C3C).copy(alpha = 0.4f),
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                )
                drawRect(
                    color = Color(0xFFB85C3C),
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 4f),
                )
            } else {
                val aspect = if (scene.radius > 0.01f) 1.2f else 1.4f
                val boxW = min(size.width, size.height) * 0.45f * scaleMul
                val boxH = boxW / aspect
                val left = placeAt.x - boxW / 2f
                val top = placeAt.y - boxH / 2f
                drawRect(
                    color = Color(0xFFB85C3C).copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                )
                drawRect(
                    color = Color(0xFFB85C3C),
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 4f),
                )
                drawLine(
                    Color(0xFF2C2A28).copy(alpha = 0.7f),
                    Offset(left, top + boxH / 2f),
                    Offset(left + boxW, top + boxH / 2f),
                    strokeWidth = 3f,
                )
                drawLine(
                    Color(0xFF2C2A28).copy(alpha = 0.7f),
                    Offset(left + boxW / 2f, top),
                    Offset(left + boxW / 2f, top + boxH),
                    strokeWidth = 3f,
                )
            }
        }

        Text(
            text = "Not real AR tracking — CameraX + overlay",
            style = HdTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

