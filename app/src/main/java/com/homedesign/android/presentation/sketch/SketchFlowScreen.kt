package com.homedesign.android.presentation.sketch

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homedesign.android.core.ui.components.InkPlateButton
import com.homedesign.android.core.ui.components.JourneyEyebrow
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.data.remote.DrawingType
import com.homedesign.android.data.remote.JobStatusDto
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.io.decodeHome
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.settings.SettingsRepository
import com.homedesign.android.domain.sketch.PendingSketchJob
import com.homedesign.android.domain.sketch.PollLoopDeps
import com.homedesign.android.domain.sketch.SketchConstants
import com.homedesign.android.domain.sketch.SketchConvertClient
import com.homedesign.android.domain.sketch.SketchCopy
import com.homedesign.android.domain.sketch.SketchFlowError
import com.homedesign.android.domain.sketch.SketchImagePrep
import com.homedesign.android.domain.sketch.flowErrorFromApi
import com.homedesign.android.domain.sketch.pollUntilTerminal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context as AndroidContext

sealed interface SketchPhase {
    data object Picker : SketchPhase
    data class Crop(val uri: Uri) : SketchPhase
    data class Progress(val headline: String, val phase: String) : SketchPhase
    data class Error(
        val message: String,
        val retryable: Boolean,
        val retryCta: String = SketchCopy.RETRY_CTA,
    ) : SketchPhase
}

data class SketchUiState(
    val phase: SketchPhase = SketchPhase.Picker,
    val drawingType: DrawingType = DrawingType.SKETCH,
    val convertInFlight: Boolean = false,
)

@HiltViewModel
class SketchFlowViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val convertClient: SketchConvertClient,
    @ApplicationContext private val appContext: AndroidContext,
) : ViewModel() {
    private val _state = MutableStateFlow(SketchUiState())
    val state: StateFlow<SketchUiState> = _state.asStateFlow()

    private var convertJob: Job? = null
    private var activeJobId: String? = null
    private var userCancelled = false

    init {
        viewModelScope.launch { maybeResumePending() }
    }

    fun setDrawingType(type: DrawingType) {
        _state.update { it.copy(drawingType = type) }
    }

    fun goPicker() {
        convertJob?.cancel()
        convertJob = null
        userCancelled = false
        activeJobId = null
        _state.update {
            it.copy(phase = SketchPhase.Picker, convertInFlight = false)
        }
    }

    fun openCrop(uri: Uri) {
        _state.update { it.copy(phase = SketchPhase.Crop(uri)) }
    }

    fun confirmCrop(
        uri: Uri,
        cropInDisplayed: RectF?,
        displayedWidth: Float,
        displayedHeight: Float,
    ) {
        startConvert(uri, cropInDisplayed, displayedWidth, displayedHeight, resumeJobId = null)
    }

    fun cancelConvert() {
        userCancelled = true
        val id = activeJobId
        convertJob?.cancel()
        convertJob = null
        viewModelScope.launch {
            if (id != null) convertClient.cancel(id)
            settingsRepository.setPendingSketchJob(null)
            _state.update {
                it.copy(
                    convertInFlight = false,
                    phase = SketchPhase.Error(
                        message = SketchCopy.CANCELLED_MESSAGE,
                        retryable = true,
                    ),
                )
            }
        }
    }

    fun importDownloadedArchive(
        bytes: ByteArray,
        onOpened: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val home = try {
                    HomedesignZip.decode(bytes)
                } catch (_: Exception) {
                    decodeHome(bytes)
                }
                val meta = projectRepository.createFromHome(
                    home = home,
                    name = home.name ?: "Sketch import",
                )
                onOpened(meta.id)
            } catch (e: Exception) {
                onError(e.message ?: "Could not open the converted plan")
            }
        }
    }

    private suspend fun maybeResumePending() {
        if (_state.value.convertInFlight) return
        val pending = settingsRepository.getPendingSketchJob() ?: return
        resumeJob(pending.jobId)
    }

    private fun resumeJob(jobId: String) {
        startConvert(
            uri = null,
            cropInDisplayed = null,
            displayedWidth = 0f,
            displayedHeight = 0f,
            resumeJobId = jobId,
        )
    }

    private fun startConvert(
        uri: Uri?,
        cropInDisplayed: RectF?,
        displayedWidth: Float,
        displayedHeight: Float,
        resumeJobId: String?,
    ) {
        convertJob?.cancel()
        userCancelled = false
        _state.update {
            it.copy(
                convertInFlight = true,
                phase = SketchPhase.Progress(
                    SketchCopy.Reel.READING_HEADLINE,
                    SketchCopy.Reel.READING_PHASE,
                ),
            )
        }
        convertJob = viewModelScope.launch {
            try {
                val jobId = if (resumeJobId != null) {
                    activeJobId = resumeJobId
                    resumeJobId
                } else {
                    requireNotNull(uri) { "uri required for new convert" }
                    val jpeg = SketchImagePrep.prepareJpeg(
                        context = appContext,
                        uri = uri,
                        cropInDisplayed = cropInDisplayed,
                        displayedWidth = displayedWidth,
                        displayedHeight = displayedHeight,
                    )
                    val started = convertClient.start(jpeg, _state.value.drawingType)
                    activeJobId = started.jobId
                    settingsRepository.setPendingSketchJob(
                        PendingSketchJob(
                            jobId = started.jobId,
                            startedAt = System.currentTimeMillis(),
                        ),
                    )
                    onJobStatus(started)
                    started.jobId
                }

                val terminal = pollUntilTerminal(
                    jobId,
                    PollLoopDeps(
                        poll = { convertClient.poll(it) },
                        cancel = { convertClient.cancel(it) },
                        onStatus = { onJobStatus(it) },
                        isCancelled = { userCancelled },
                    ),
                )
                if (userCancelled) return@launch

                _state.update {
                    it.copy(
                        phase = SketchPhase.Progress(
                            SketchCopy.Reel.BUILDING_HEADLINE,
                            SketchCopy.Reel.FURNITURE_PHASE,
                        ),
                    )
                }
                val bytes = convertClient.download(terminal.jobId)
                settingsRepository.setPendingSketchJob(null)
                activeJobId = null
                _state.update { it.copy(convertInFlight = false) }
                // Delivery is handled by the composable via pendingOpenBytes event
                pendingBytes = bytes
                _deliveryTick.update { it + 1 }
            } catch (e: Exception) {
                if (userCancelled) return@launch
                settingsRepository.setPendingSketchJob(null)
                activeJobId = null
                val err = flowErrorFromApi(e, convertClient.serviceHost)
                // Silent fallthrough to picker on failed resume (web parity)
                if (resumeJobId != null) {
                    _state.update {
                        it.copy(convertInFlight = false, phase = SketchPhase.Picker)
                    }
                } else {
                    showError(err)
                }
            }
        }
    }

    @Volatile
    private var pendingBytes: ByteArray? = null
    private val _deliveryTick = MutableStateFlow(0)
    val deliveryTick: StateFlow<Int> = _deliveryTick.asStateFlow()

    fun consumePendingBytes(): ByteArray? {
        val bytes = pendingBytes
        pendingBytes = null
        return bytes
    }

    private fun onJobStatus(status: JobStatusDto) {
        val elapsedHint = status.progress ?: 0.0
        val (headline, phase) = when {
            status.status == "queued" || status.status == "pending" ->
                SketchCopy.Reel.READING_HEADLINE to SketchCopy.Reel.READING_PHASE
            elapsedHint < 0.45 ->
                SketchCopy.Reel.DRAWING_HEADLINE to SketchCopy.Reel.WALLS_PHASE
            elapsedHint < 0.8 ->
                SketchCopy.Reel.BUILDING_HEADLINE to SketchCopy.Reel.FURNITURE_PHASE
            else ->
                SketchCopy.Reel.STEADY_HEADLINE to SketchCopy.Reel.STEADY_PHASE
        }
        _state.update {
            it.copy(phase = SketchPhase.Progress(headline, phase))
        }
    }

    private fun showError(err: SketchFlowError) {
        _state.update {
            it.copy(
                convertInFlight = false,
                phase = SketchPhase.Error(
                    message = err.message,
                    retryable = err.isRetryable,
                    retryCta = err.retryCta,
                ),
            )
        }
    }
}

@Composable
fun SketchFlowScreen(
    onClose: () -> Unit,
    onOpened: (projectId: String) -> Unit,
    viewModel: SketchFlowViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsState()
    val deliveryTick by viewModel.deliveryTick.collectAsState()
    val context = LocalContext.current
    var importError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deliveryTick) {
        if (deliveryTick == 0) return@LaunchedEffect
        val bytes = viewModel.consumePendingBytes() ?: return@LaunchedEffect
        importError = null
        viewModel.importDownloadedArchive(
            bytes = bytes,
            onOpened = onOpened,
            onError = { importError = it },
        )
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        if (ok && uri != null) viewModel.openCrop(uri)
    }
    val pickGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.openCrop(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createSketchCaptureUri(context)
            cameraUri = uri
            takePicture.launch(uri)
        }
    }

    when (val p = ui.phase) {
        SketchPhase.Picker -> SketchPickerScreen(
            drawingType = ui.drawingType,
            onDrawingType = viewModel::setDrawingType,
            onClose = onClose,
            onTakePhoto = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val uri = createSketchCaptureUri(context)
                    cameraUri = uri
                    takePicture.launch(uri)
                } else {
                    requestCamera.launch(Manifest.permission.CAMERA)
                }
            },
            onLibrary = { pickGallery.launch("image/*") },
        )
        is SketchPhase.Crop -> SketchCropScreen(
            uri = p.uri,
            onCancel = viewModel::goPicker,
            onUsePhoto = { crop, dw, dh ->
                viewModel.confirmCrop(p.uri, crop, dw, dh)
            },
        )
        is SketchPhase.Progress -> SketchProgressScreen(
            headline = p.headline,
            phaseCopy = p.phase,
            onCancel = viewModel::cancelConvert,
        )
        is SketchPhase.Error -> SketchErrorScreen(
            message = p.message,
            retryable = p.retryable,
            retryCta = p.retryCta,
            onRetry = viewModel::goPicker,
            onClose = onClose,
        )
    }

    // Import decode failure after a successful download
    importError?.let { msg ->
        SketchErrorScreen(
            message = msg,
            retryable = true,
            onRetry = {
                importError = null
                viewModel.goPicker()
            },
            onClose = onClose,
        )
    }
}

private fun createSketchCaptureUri(context: AndroidContext): Uri {
    val dir = File(context.cacheDir, "sketch").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
fun SketchPickerScreen(
    drawingType: DrawingType,
    onDrawingType: (DrawingType) -> Unit,
    onClose: () -> Unit,
    onTakePhoto: () -> Unit,
    onLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JourneyEyebrow("AI · Sketch import")
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = HdTheme.colors.ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = buildAnnotatedString {
                append("Photograph your ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                    append("floor plan")
                }
            },
            style = HdTheme.typography.displaySmall,
            color = HdTheme.colors.ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "We read the walls, rooms, doors and windows, then build it as an editable design.",
            style = HdTheme.typography.bodyMedium,
            color = HdTheme.colors.stone,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Fit the whole plan in frame",
                "Flat and evenly lit, no glare",
                "Keep written dimensions readable",
            ).forEach { tip ->
                Text("·  $tip", style = HdTheme.typography.bodySmall, color = HdTheme.colors.graphite)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeChip(
                title = "Hand sketch",
                subtitle = "Drawn by hand",
                selected = drawingType == DrawingType.SKETCH,
                onClick = { onDrawingType(DrawingType.SKETCH) },
                modifier = Modifier.weight(1f),
            )
            TypeChip(
                title = "CAD plan",
                subtitle = "Printed or exported",
                selected = drawingType == DrawingType.CAD,
                onClick = { onDrawingType(DrawingType.CAD) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.weight(1f))
        SourceRow(
            title = "Take photo",
            subtitle = "Point at the plan and shoot",
            camera = true,
            onClick = onTakePhoto,
        )
        Spacer(Modifier.height(10.dp))
        SourceRow(
            title = "Choose from library",
            subtitle = "Use an existing photo",
            camera = false,
            onClick = onLibrary,
        )
        Spacer(Modifier.height(12.dp))
    }
}

private enum class CropHandle { Move, TL, TR, BL, BR }

@Composable
fun SketchCropScreen(
    uri: Uri,
    onCancel: () -> Unit,
    onUsePhoto: (crop: RectF, displayedW: Float, displayedH: Float) -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        SketchImagePrep.decodeUpright(context, uri)
    }
    if (bitmap == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(HdTheme.colors.paper)
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(SketchCopy.HEIC_UNREADABLE, color = HdTheme.colors.ink)
            Spacer(Modifier.height(16.dp))
            Text(
                "Cancel",
                color = HdTheme.colors.stone,
                modifier = Modifier.clickable(onClick = onCancel).padding(8.dp),
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        val density = LocalDensity.current
        val stageW = with(density) { maxWidth.toPx() }
        val stageH = with(density) { maxHeight.toPx() } - with(density) { 120.dp.toPx() }
        val fitted = remember(bitmap, stageW, stageH) {
            fittedFrame(bitmap.width.toFloat(), bitmap.height.toFloat(), stageW, stageH)
        }
        var crop by remember(fitted) {
            mutableStateOf(
                RectF(fitted.left, fitted.top, fitted.right, fitted.bottom),
            )
        }
        var dragMode by remember { mutableStateOf<CropHandle?>(null) }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cancel",
                    style = HdTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.clickable(onClick = onCancel).padding(8.dp),
                )
                Text("Crop", style = HdTheme.typography.titleMedium, color = Color.White)
                Text(
                    text = "Use photo",
                    style = HdTheme.typography.labelLarge,
                    color = HdTheme.colors.terracotta,
                    modifier = Modifier
                        .clickable {
                            val local = RectF(
                                crop.left - fitted.left,
                                crop.top - fitted.top,
                                crop.right - fitted.left,
                                crop.bottom - fitted.top,
                            )
                            onUsePhoto(local, fitted.width(), fitted.height())
                        }
                        .padding(8.dp),
                )
            }

            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            val hitSlop = SketchConstants.CROP_HANDLE_PX / 2f + SketchConstants.CROP_HIT_SLOP_PX
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(fitted, crop) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                dragMode = hitCropHandle(pos, crop, hitSlop)
                            },
                            onDragEnd = { dragMode = null },
                            onDragCancel = { dragMode = null },
                            onDrag = { change, amount ->
                                change.consume()
                                val mode = dragMode ?: return@detectDragGestures
                                crop = applyCropDrag(mode, crop, amount, fitted)
                            },
                        )
                    },
            ) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(fitted.left.roundToInt(), fitted.top.roundToInt()),
                    dstSize = IntSize(
                        fitted.width().roundToInt().coerceAtLeast(1),
                        fitted.height().roundToInt().coerceAtLeast(1),
                    ),
                )
                // Dim outside crop
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset.Zero, Size(size.width, crop.top))
                drawRect(dim, Offset(0f, crop.bottom), Size(size.width, size.height - crop.bottom))
                drawRect(dim, Offset(0f, crop.top), Size(crop.left, crop.height()))
                drawRect(
                    dim,
                    Offset(crop.right, crop.top),
                    Size(size.width - crop.right, crop.height()),
                )
                // Rule of thirds
                val guide = Color.White.copy(alpha = 0.35f)
                val w3 = crop.width() / 3f
                val h3 = crop.height() / 3f
                drawLine(guide, Offset(crop.left + w3, crop.top), Offset(crop.left + w3, crop.bottom), 1f)
                drawLine(guide, Offset(crop.left + 2 * w3, crop.top), Offset(crop.left + 2 * w3, crop.bottom), 1f)
                drawLine(guide, Offset(crop.left, crop.top + h3), Offset(crop.right, crop.top + h3), 1f)
                drawLine(guide, Offset(crop.left, crop.top + 2 * h3), Offset(crop.right, crop.top + 2 * h3), 1f)
                drawRect(
                    Color.White,
                    Offset(crop.left, crop.top),
                    Size(crop.width(), crop.height()),
                    style = Stroke(width = 2f),
                )
                val hs = SketchConstants.CROP_HANDLE_PX / 2f
                listOf(
                    Offset(crop.left, crop.top),
                    Offset(crop.right, crop.top),
                    Offset(crop.left, crop.bottom),
                    Offset(crop.right, crop.bottom),
                ).forEach { c ->
                    drawRect(
                        Color.White,
                        topLeft = Offset(c.x - hs, c.y - hs),
                        size = Size(hs * 2, hs * 2),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                InkPlateButton(
                    label = "Use photo",
                    onClick = {
                        val local = RectF(
                            crop.left - fitted.left,
                            crop.top - fitted.top,
                            crop.right - fitted.left,
                            crop.bottom - fitted.top,
                        )
                        onUsePhoto(local, fitted.width(), fitted.height())
                    },
                )
            }
        }
    }
}

private fun fittedFrame(imgW: Float, imgH: Float, stageW: Float, stageH: Float): RectF {
    if (imgW <= 0f || imgH <= 0f || stageW <= 0f || stageH <= 0f) {
        return RectF(0f, 0f, stageW, stageH)
    }
    val scale = min(stageW / imgW, stageH / imgH)
    val w = imgW * scale
    val h = imgH * scale
    val left = (stageW - w) / 2f
    val top = (stageH - h) / 2f
    return RectF(left, top, left + w, top + h)
}

private fun hitCropHandle(pos: Offset, crop: RectF, slop: Float): CropHandle {
    fun near(x: Float, y: Float) = (pos.x - x) * (pos.x - x) + (pos.y - y) * (pos.y - y) <= slop * slop
    return when {
        near(crop.left, crop.top) -> CropHandle.TL
        near(crop.right, crop.top) -> CropHandle.TR
        near(crop.left, crop.bottom) -> CropHandle.BL
        near(crop.right, crop.bottom) -> CropHandle.BR
        crop.contains(pos.x, pos.y) -> CropHandle.Move
        else -> CropHandle.Move
    }
}

private fun applyCropDrag(mode: CropHandle, crop: RectF, amount: Offset, bounds: RectF): RectF {
    val minSide = SketchConstants.CROP_MIN_SIDE_PX
    val next = RectF(crop)
    when (mode) {
        CropHandle.Move -> {
            val dx = amount.x.coerceIn(bounds.left - next.left, bounds.right - next.right)
            val dy = amount.y.coerceIn(bounds.top - next.top, bounds.bottom - next.bottom)
            next.offset(dx, dy)
        }
        CropHandle.TL -> {
            next.left = (next.left + amount.x).coerceIn(bounds.left, next.right - minSide)
            next.top = (next.top + amount.y).coerceIn(bounds.top, next.bottom - minSide)
        }
        CropHandle.TR -> {
            next.right = (next.right + amount.x).coerceIn(next.left + minSide, bounds.right)
            next.top = (next.top + amount.y).coerceIn(bounds.top, next.bottom - minSide)
        }
        CropHandle.BL -> {
            next.left = (next.left + amount.x).coerceIn(bounds.left, next.right - minSide)
            next.bottom = (next.bottom + amount.y).coerceIn(next.top + minSide, bounds.bottom)
        }
        CropHandle.BR -> {
            next.right = (next.right + amount.x).coerceIn(next.left + minSide, bounds.right)
            next.bottom = (next.bottom + amount.y).coerceIn(next.top + minSide, bounds.bottom)
        }
    }
    return next
}

@Composable
fun SketchProgressScreen(
    headline: String,
    phaseCopy: String,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        JourneyEyebrow("AI · Sketch import")
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = HdTheme.colors.terracotta)
        Spacer(Modifier.height(28.dp))
        Text(headline, style = HdTheme.typography.headlineMedium, color = HdTheme.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(phaseCopy, style = HdTheme.typography.bodyMedium, color = HdTheme.colors.stone)
        Spacer(Modifier.height(36.dp))
        Text(
            text = "Cancel",
            style = HdTheme.typography.labelLarge,
            color = HdTheme.colors.stone,
            modifier = Modifier.clickable(onClick = onCancel).padding(12.dp),
        )
    }
}

@Composable
fun SketchErrorScreen(
    message: String,
    retryable: Boolean,
    retryCta: String = SketchCopy.RETRY_CTA,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        JourneyEyebrow("AI · Sketch import")
        Spacer(Modifier.height(16.dp))
        Text("Something went wrong", style = HdTheme.typography.headlineMedium, color = HdTheme.colors.ink)
        Spacer(Modifier.height(10.dp))
        Text(message, style = HdTheme.typography.bodyMedium, color = HdTheme.colors.stone)
        Spacer(Modifier.height(28.dp))
        if (retryable) {
            InkPlateButton(label = retryCta, onClick = onRetry)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = "Close",
            style = HdTheme.typography.labelLarge,
            color = HdTheme.colors.stone,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onClose)
                .padding(12.dp),
        )
    }
}

@Composable
private fun TypeChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) HdTheme.colors.terracotta else HdTheme.colors.hairline,
                shape = RoundedCornerShape(14.dp),
            )
            .background(if (selected) HdTheme.colors.tintWarm else HdTheme.colors.ivory)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, style = HdTheme.typography.titleSmall, color = HdTheme.colors.ink)
        Text(subtitle, style = HdTheme.typography.bodySmall, color = HdTheme.colors.stone)
    }
}

@Composable
private fun SourceRow(
    title: String,
    subtitle: String,
    camera: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HdTheme.colors.ink)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HdTheme.colors.paper.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (camera) Icons.Outlined.CameraAlt else Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = HdTheme.colors.paper,
            )
        }
        Column {
            Text(title, style = HdTheme.typography.titleMedium, color = HdTheme.colors.paper)
            Text(subtitle, style = HdTheme.typography.bodySmall, color = HdTheme.colors.paper.copy(alpha = 0.7f))
        }
    }
}
