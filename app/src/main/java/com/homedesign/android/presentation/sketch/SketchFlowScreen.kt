package com.homedesign.android.presentation.sketch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homedesign.android.core.ui.components.InkPlateButton
import com.homedesign.android.core.ui.components.JourneyEyebrow
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.data.remote.DrawingType
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.io.decodeHome
import com.homedesign.android.domain.model.HomeFactory
import com.homedesign.android.domain.project.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SketchPhase {
    data object Picker : SketchPhase
    data object Crop : SketchPhase
    data class Progress(val headline: String, val phase: String) : SketchPhase
    data class Error(val message: String, val retryable: Boolean) : SketchPhase
}

@HiltViewModel
class SketchFlowViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {
    fun createAndOpen(onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val meta = projectRepository.createBlank(name = "Sketch import")
            onOpened(meta.id)
        }
    }

    /**
     * After sketch download succeeds: decode `.homedesign` / manifest JSON,
     * persist as a project, then open the editor. On failure returns an error message.
     */
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
}

@Composable
fun SketchFlowScreen(
    onClose: () -> Unit,
    onOpened: (projectId: String) -> Unit,
    viewModel: SketchFlowViewModel = hiltViewModel(),
) {
    var phase by remember { mutableStateOf<SketchPhase>(SketchPhase.Picker) }
    var drawingType by remember { mutableStateOf(DrawingType.SKETCH) }
    val scope = rememberCoroutineScope()

    when (val p = phase) {
        SketchPhase.Picker -> SketchPickerScreen(
            drawingType = drawingType,
            onDrawingType = { drawingType = it },
            onClose = onClose,
            onTakePhoto = { phase = SketchPhase.Crop },
            onLibrary = { phase = SketchPhase.Crop },
        )
        SketchPhase.Crop -> SketchCropScreen(
            onCancel = { phase = SketchPhase.Picker },
            onUsePhoto = {
                phase = SketchPhase.Progress("Reading your sketch", "reading the drawing…")
                scope.launch {
                    delay(900)
                    phase = SketchPhase.Progress("Drawing your plan", "finding walls…")
                    delay(1100)
                    phase = SketchPhase.Progress("Building your model", "placing furniture…")
                    delay(900)
                    // Placeholder until SketchApi download is wired: persist a blank Home archive.
                    // When download bytes arrive, call viewModel.importDownloadedArchive(bytes, …).
                    try {
                        val demo = HomedesignZip.encode(HomeFactory.emptyHome("Sketch import"))
                        viewModel.importDownloadedArchive(
                            bytes = demo,
                            onOpened = onOpened,
                            onError = { msg ->
                                phase = SketchPhase.Error(message = msg, retryable = true)
                            },
                        )
                    } catch (e: Exception) {
                        phase = SketchPhase.Error(
                            message = e.message ?: "Could not open the converted plan",
                            retryable = true,
                        )
                    }
                }
            },
        )
        is SketchPhase.Progress -> SketchProgressScreen(
            headline = p.headline,
            phaseCopy = p.phase,
            onCancel = {
                phase = SketchPhase.Error(
                    message = "Conversion cancelled.",
                    retryable = true,
                )
            },
        )
        is SketchPhase.Error -> SketchErrorScreen(
            message = p.message,
            retryable = p.retryable,
            onRetry = { phase = SketchPhase.Picker },
            onClose = onClose,
        )
    }
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

@Composable
fun SketchCropScreen(
    onCancel: () -> Unit,
    onUsePhoto: () -> Unit,
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
            Text(
                text = "Cancel",
                style = HdTheme.typography.labelLarge,
                color = HdTheme.colors.stone,
                modifier = Modifier.clickable(onClick = onCancel).padding(8.dp),
            )
            Text("Crop", style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
            Text(
                text = "Use photo",
                style = HdTheme.typography.labelLarge,
                color = HdTheme.colors.terracotta,
                modifier = Modifier.clickable(onClick = onUsePhoto).padding(8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(HdTheme.colors.highlight)
                .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Crop placeholder",
                    style = HdTheme.typography.headlineSmall,
                    color = HdTheme.colors.ink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Image crop UI will live here.",
                    style = HdTheme.typography.bodySmall,
                    color = HdTheme.colors.stone,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        InkPlateButton(label = "Use photo", onClick = onUsePhoto)
    }
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
            InkPlateButton(label = "Try again", onClick = onRetry)
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
