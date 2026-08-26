package com.homedesign.android.presentation.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homedesign.android.core.ui.hdGlassCapsule
import com.homedesign.android.core.ui.hdGlassChrome
import com.homedesign.android.core.ui.hdLayerBackdrop
import com.homedesign.android.core.ui.rememberHdLayerBackdrop
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.catalog.catalogById
import com.homedesign.android.domain.geom.LevelMutation
import com.homedesign.android.domain.geom.OpeningKind
import com.homedesign.android.domain.geom.PlanAxis
import com.homedesign.android.domain.geom.PlanRotation
import com.homedesign.android.domain.geom.TRACE_MAX_CM
import com.homedesign.android.domain.geom.TRACE_MIN_CM
import com.homedesign.android.domain.geom.defaultWallThicknessCM
import com.homedesign.android.domain.geom.exteriorThicknessCM
import com.homedesign.android.domain.geom.formatTraceWidthLabel
import com.homedesign.android.domain.geom.interiorThicknessCM
import com.homedesign.android.domain.model.Level
import com.homedesign.android.domain.model.Selection
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import kotlin.math.roundToInt

/** Web `SIDE_PANEL_MIN_WIDTH` — property sheet docks right at/above this width. */
private val SidePanelMinWidth = 672.dp
private val SidePanelWidth = 360.dp
/** Side-by-side 2D+3D Split chip is offered at/above this width. */
private val SplitViewMinWidth = 900.dp
/** Clearance below status bars for floating tip/trace/place banners (single iOS-style strip). */
private val TopChromeClearance = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenSketch: () -> Unit,
    onOpenProject: (String) -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var exportOpen by remember { mutableStateOf(false) }
    var floorMenuOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var catalogReplaceMode by remember { mutableStateOf(false) }
    /** null = floor; "left"/"right" = wall side finish. */
    var pendingTextureImport by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val addSheetState = rememberModalBottomSheetState()
    val catalogSheetState = rememberModalBottomSheetState()

    val pickTrace = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.setTraceFromUri(uri)
    }

    val pickUserTexture = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val target = pendingTextureImport
        pendingTextureImport = null
        if (uri == null) return@rememberLauncherForActivityResult
        when (target) {
            "left", "right" -> viewModel.importWallTextureFromUri(target, uri)
            "floor" -> viewModel.importFloorTextureFromUri(uri)
        }
    }

    val pickHomedesign = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.flushSave()
            viewModel.openHomedesignFromUri(uri, onOpenProject)
        }
    }

    val pickSh3d = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.flushSave()
            viewModel.openSh3dFromUri(uri, onOpenProject)
        }
    }

    val saveCopy = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.saveCopyToUri(uri)
    }

    LaunchedEffect(projectId) {
        viewModel.load(projectId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbar.showSnackbar(msg)
        }
    }

    LaunchedEffect(state.toast) {
        val t = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(t)
        viewModel.consumeToast()
    }

    LaunchedEffect(state.exportUri) {
        val uri = state.exportUri ?: return@LaunchedEffect
        val mime = state.exportMime ?: "application/octet-stream"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, state.exportLabel ?: "Export"))
        viewModel.consumeExport()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushSave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushSave()
        }
    }

    val hasSelection = state.selection !is Selection.None
    var additiveSelect by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Back priority (last registered wins): dismiss sheets → clear selection → leave editor.
    BackHandler(enabled = hasSelection) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = showAdd) {
        showAdd = false
    }
    BackHandler(enabled = showCatalog) {
        showCatalog = false
        catalogReplaceMode = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.ShiftLeft, Key.ShiftRight,
                    Key.CtrlLeft, Key.CtrlRight,
                    Key.MetaLeft, Key.MetaRight,
                    -> {
                        additiveSelect = event.type == KeyEventType.KeyDown ||
                            event.isShiftPressed ||
                            event.isCtrlPressed ||
                            event.isMetaPressed
                        false
                    }
                    else -> {
                        additiveSelect =
                            event.isShiftPressed || event.isCtrlPressed || event.isMetaPressed
                        false
                    }
                }
            },
    ) {
        val sidePanel = maxWidth >= SidePanelMinWidth
        val wideSplit = maxWidth >= SplitViewMinWidth
        val placeTool = state.tool as? EditorTool.PlaceFurniture
        val showPlanChrome = state.viewMode == EditorViewMode.Plan2D ||
            state.viewMode == EditorViewMode.Split
        // Kyant Backdrop — canvas is the layer; chrome draws glass over it.
        val glassBackdrop = rememberHdLayerBackdrop(HdTheme.colors.paper)

        when (state.viewMode) {
            EditorViewMode.View3D, EditorViewMode.Walk -> {
                Plan3DScreen(
                    home = state.home,
                    cameraMode = if (state.viewMode == EditorViewMode.Walk) {
                        Plan3DCameraMode.Walk
                    } else {
                        Plan3DCameraMode.Orbit
                    },
                    onBackToPlan = { viewModel.setViewMode(EditorViewMode.Plan2D) },
                    modifier = Modifier
                        .fillMaxSize()
                        .hdLayerBackdrop(glassBackdrop),
                )
            }
            EditorViewMode.AR -> {
                val soloFurnitureId = (state.selection as? Selection.Furniture)?.id
                ArHomeScreen(
                    home = state.home,
                    soloFurnitureId = soloFurnitureId,
                    onBackToPlan = { viewModel.setViewMode(EditorViewMode.Plan2D) },
                    onOpenWalk = { viewModel.setViewMode(EditorViewMode.Walk) },
                    modifier = Modifier
                        .fillMaxSize()
                        .hdLayerBackdrop(glassBackdrop),
                )
            }
            EditorViewMode.Split -> {
                // iOS phone: 3D above (~45%) / 2D below (~55%). Wide: side-by-side.
                val splitPad = Modifier
                    .fillMaxSize()
                    .hdLayerBackdrop(glassBackdrop)
                    .padding(
                        top = TopChromeClearance,
                        bottom = 100.dp,
                        end = if (sidePanel && hasSelection && state.tool is EditorTool.Select) {
                            SidePanelWidth + 24.dp
                        } else {
                            0.dp
                        },
                    )
                val planCanvas: @Composable (Modifier) -> Unit = { mod ->
                    PlanCanvas(
                        home = state.home,
                        selection = state.selection,
                        tool = state.tool,
                        preview = state.preview,
                        unitSystem = state.unitSystem,
                        trace = state.trace,
                        ghostOtherLevels = state.ghostOtherLevels,
                        additiveSelect = additiveSelect,
                        cameraFocus = state.cameraFocus,
                        onTap = viewModel::onPlanTap,
                        onDrawWallArm = viewModel::onDrawWallArm,
                        onDrawWallDrag = viewModel::onDrawWallDrag,
                        onDrawWallCommit = { plan, scale ->
                            val thickness = (state.tool as? EditorTool.DrawWall)?.thickness
                                ?: defaultWallThicknessCM
                            viewModel.onDrawWallCommit(plan, thickness, scale)
                        },
                        onDrawRoomDrag = viewModel::onDrawRoomDrag,
                        onDrawRoomCommit = viewModel::onDrawRoomCommit,
                        onCancelPreview = viewModel::cancelPreview,
                        tryBeginOpeningDrag = viewModel::tryBeginOpeningDrag,
                        onOpeningDrag = viewModel::updateOpeningDrag,
                        onOpeningDragEnd = viewModel::endOpeningDrag,
                        tryBeginBowHandleDrag = viewModel::tryBeginBowHandleDrag,
                        onBowHandleDrag = viewModel::updateBowHandleDrag,
                        onBowHandleDragEnd = viewModel::endBowHandleDrag,
                        onFurnitureMovePreview = viewModel::onFurnitureMovePreview,
                        onFurnitureRotatePreview = viewModel::onFurnitureRotatePreview,
                        onFurnitureMoveCommit = viewModel::commitFurnitureMoveGesture,
                        onFurnitureRotateCommit = viewModel::commitFurnitureRotateGesture,
                        onDimensionEndPreview = viewModel::onDimensionEndPreview,
                        onDimensionOffsetPreview = viewModel::onDimensionOffsetPreview,
                        onDimensionEditCommit = viewModel::commitDimensionEdit,
                        modifier = mod,
                    )
                }
                if (wideSplit) {
                    Row(splitPad) {
                        planCanvas(Modifier.weight(1f).fillMaxHeight())
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(HdTheme.colors.hairline),
                        )
                        Plan3DScreen(
                            home = state.home,
                            cameraMode = Plan3DCameraMode.Orbit,
                            onBackToPlan = { viewModel.setViewMode(EditorViewMode.Plan2D) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(splitPad) {
                        Plan3DScreen(
                            home = state.home,
                            cameraMode = Plan3DCameraMode.Orbit,
                            onBackToPlan = { viewModel.setViewMode(EditorViewMode.Plan2D) },
                            modifier = Modifier.weight(0.45f).fillMaxWidth(),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(HdTheme.colors.hairline),
                        )
                        planCanvas(Modifier.weight(0.55f).fillMaxWidth())
                    }
                }
            }
            EditorViewMode.Plan2D -> PlanCanvas(
                home = state.home,
                selection = state.selection,
                tool = state.tool,
                preview = state.preview,
                unitSystem = state.unitSystem,
                trace = state.trace,
                ghostOtherLevels = state.ghostOtherLevels,
                additiveSelect = additiveSelect,
                cameraFocus = state.cameraFocus,
                onTap = viewModel::onPlanTap,
                onDrawWallArm = viewModel::onDrawWallArm,
                onDrawWallDrag = viewModel::onDrawWallDrag,
                onDrawWallCommit = { plan, scale ->
                    val thickness = (state.tool as? EditorTool.DrawWall)?.thickness
                        ?: defaultWallThicknessCM
                    viewModel.onDrawWallCommit(plan, thickness, scale)
                },
                onDrawRoomDrag = viewModel::onDrawRoomDrag,
                onDrawRoomCommit = viewModel::onDrawRoomCommit,
                onCancelPreview = viewModel::cancelPreview,
                tryBeginOpeningDrag = viewModel::tryBeginOpeningDrag,
                onOpeningDrag = viewModel::updateOpeningDrag,
                onOpeningDragEnd = viewModel::endOpeningDrag,
                tryBeginBowHandleDrag = viewModel::tryBeginBowHandleDrag,
                onBowHandleDrag = viewModel::updateBowHandleDrag,
                onBowHandleDragEnd = viewModel::endBowHandleDrag,
                onFurnitureMovePreview = viewModel::onFurnitureMovePreview,
                onFurnitureRotatePreview = viewModel::onFurnitureRotatePreview,
                onFurnitureMoveCommit = viewModel::commitFurnitureMoveGesture,
                onFurnitureRotateCommit = viewModel::commitFurnitureRotateGesture,
                onDimensionEndPreview = viewModel::onDimensionEndPreview,
                onDimensionOffsetPreview = viewModel::onDimensionOffsetPreview,
                onDimensionEditCommit = viewModel::commitDimensionEdit,
                modifier = Modifier
                    .fillMaxSize()
                    .hdLayerBackdrop(glassBackdrop)
                    .padding(
                        top = TopChromeClearance,
                        bottom = 100.dp,
                        end = if (sidePanel && hasSelection && state.tool is EditorTool.Select) {
                            SidePanelWidth + 24.dp
                        } else {
                            0.dp
                        },
                    ),
            )
        }

        // iOS EditorDeckTopBar: back · title/saved · units · mode icons · share · overflow
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .hdGlassChrome(
                    backdrop = glassBackdrop,
                    shape = RoundedCornerShape(20.dp),
                    fallbackFill = HdTheme.colors.ivory.copy(alpha = 0.92f),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                viewModel.flushSave()
                onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to designs",
                    tint = HdTheme.colors.architectInk,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "Untitled" },
                    style = HdTheme.typography.titleMedium,
                    color = HdTheme.colors.architectInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.savedLabel,
                    style = HdTheme.typography.labelSmall,
                    color = HdTheme.colors.architectGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showPlanChrome) {
                UnitSystemChips(
                    selected = state.unitSystem,
                    onSelect = viewModel::setUnitSystem,
                )
                Spacer(Modifier.width(4.dp))
            }
            EditorModeIconSwitch(
                selected = state.viewMode,
                onSelect = viewModel::setViewMode,
                showSplit = true,
            )
            IconButton(onClick = { viewModel.exportPdf() }) {
                Icon(
                    Icons.Outlined.IosShare,
                    contentDescription = "Share or export",
                    tint = HdTheme.colors.architectInk,
                )
            }
            Box {
                IconButton(onClick = { exportOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "More",
                        tint = HdTheme.colors.architectInk,
                    )
                }
                DropdownMenu(expanded = exportOpen, onDismissRequest = { exportOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open .homedesign…") },
                        onClick = {
                            exportOpen = false
                            pickHomedesign.launch("*/*")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Open .sh3d…") },
                        onClick = {
                            exportOpen = false
                            pickSh3d.launch("*/*")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save a copy…") },
                        onClick = {
                            exportOpen = false
                            saveCopy.launch(viewModel.suggestedSaveFilename())
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Trace photo…") },
                        onClick = {
                            exportOpen = false
                            pickTrace.launch("image/*")
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Floors…") },
                        onClick = {
                            exportOpen = false
                            floorMenuOpen = true
                        },
                    )
                    if (state.home.levels.isNotEmpty()) {
                        // Keep floor menu reachable via overflow when strip is tight.
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Mirror plan L↔R") },
                        onClick = {
                            exportOpen = false
                            viewModel.mirrorPlan(PlanAxis.Vertical)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mirror plan T↔B") },
                        onClick = {
                            exportOpen = false
                            viewModel.mirrorPlan(PlanAxis.Horizontal)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rotate plan 90° CW") },
                        onClick = {
                            exportOpen = false
                            viewModel.rotatePlan(PlanRotation.Clockwise)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rotate plan 90° CCW") },
                        onClick = {
                            exportOpen = false
                            viewModel.rotatePlan(PlanRotation.CounterClockwise)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Export PDF") },
                        onClick = {
                            exportOpen = false
                            viewModel.exportPdf()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export DXF") },
                        onClick = {
                            exportOpen = false
                            viewModel.exportDxf()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export .homedesign") },
                        onClick = {
                            exportOpen = false
                            viewModel.exportHomedesign()
                        },
                    )
                }
            }
            // Floor selector sits in overflow-adjacent slot on the strip when space allows.
            FloorSelectorButton(
                levels = state.home.levels,
                selectedLevelID = state.home.selectedLevelID,
                ghostOtherLevels = state.ghostOtherLevels,
                menuOpen = floorMenuOpen,
                onMenuOpenChange = { floorMenuOpen = it },
                onSelectLevel = viewModel::selectLevel,
                onAddFloor = viewModel::addFloorOnTop,
                onToggleGhost = viewModel::toggleGhostOtherLevels,
            )
        }

        if (showPlanChrome &&
            state.showEditorTip &&
            placeTool == null &&
            state.trace == null
        ) {
            EditorTipBanner(
                onDismiss = viewModel::dismissEditorTip,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = TopChromeClearance, start = 16.dp, end = 16.dp),
            )
        }

        if (showPlanChrome) {
            state.trace?.let { underlay ->
                TraceUnderlayCapsule(
                    widthCM = underlay.widthCM,
                    onWidthCM = viewModel::setTraceWidthCM,
                    onRemove = viewModel::clearTrace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = TopChromeClearance),
                )
            }
        }

        if (showPlanChrome) Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp)
                .padding(bottom = 16.dp)
                .hdGlassCapsule(
                    backdrop = glassBackdrop,
                    fallbackFill = HdTheme.colors.ivory.copy(alpha = 0.94f),
                )
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            // iOS EditorDock: Edit⇄Measure · Add · Catalog · Sketch · Walk · Undo · Redo
            val measure = state.tool is EditorTool.Dimension
            DockItem(
                label = if (measure) "Measure" else "Edit",
                active = measure,
                onClick = {
                    if (measure) viewModel.setTool(EditorTool.Select)
                    else viewModel.setTool(EditorTool.Dimension)
                },
            ) {
                Icon(
                    if (measure) Icons.Outlined.Straighten else Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = if (measure) HdTheme.colors.paper else HdTheme.colors.architectInk,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (state.tool is EditorTool.DrawWall) {
                val thickness = (state.tool as EditorTool.DrawWall).thickness
                DockItem(
                    label = "Int",
                    active = kotlin.math.abs(thickness - interiorThicknessCM) < 0.1,
                    onClick = { viewModel.setTool(EditorTool.DrawWall(interiorThicknessCM)) },
                ) {
                    Text(
                        UnitFormat.length(interiorThicknessCM, state.unitSystem),
                        color = HdTheme.colors.architectInk,
                        style = HdTheme.typography.labelSmall,
                    )
                }
                DockItem(
                    label = "Ext",
                    active = kotlin.math.abs(thickness - exteriorThicknessCM) < 0.1,
                    onClick = { viewModel.setTool(EditorTool.DrawWall(exteriorThicknessCM)) },
                ) {
                    Text(
                        UnitFormat.length(exteriorThicknessCM, state.unitSystem),
                        color = HdTheme.colors.architectInk,
                        style = HdTheme.typography.labelSmall,
                    )
                }
            }
            DockItem(label = "Add", ink = true, onClick = { showAdd = true }) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = HdTheme.colors.paper,
                    modifier = Modifier.size(18.dp),
                )
            }
            DockItem(
                label = "Catalog",
                active = state.tool is EditorTool.PlaceFurniture,
                onClick = { showCatalog = true },
            ) {
                Icon(
                    Icons.Outlined.Weekend,
                    contentDescription = null,
                    tint = HdTheme.colors.architectInk,
                    modifier = Modifier.size(18.dp),
                )
            }
            DockItem(label = "Sketch", onClick = {
                viewModel.flushSave()
                onOpenSketch()
            }) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = HdTheme.colors.architectInk,
                    modifier = Modifier.size(18.dp),
                )
            }
            DockItem(
                label = "Walk",
                active = state.viewMode == EditorViewMode.Walk,
                onClick = { viewModel.setViewMode(EditorViewMode.Walk) },
            ) {
                Icon(
                    Icons.Outlined.DirectionsWalk,
                    contentDescription = null,
                    tint = if (state.viewMode == EditorViewMode.Walk) {
                        HdTheme.colors.paper
                    } else {
                        HdTheme.colors.architectInk
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            DockItem(
                label = "Undo",
                active = false,
                onClick = { if (state.canUndo) viewModel.undo() },
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = "Undo",
                    tint = if (state.canUndo) {
                        HdTheme.colors.architectInk
                    } else {
                        HdTheme.colors.architectGray
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            DockItem(
                label = "Redo",
                active = false,
                onClick = { if (state.canRedo) viewModel.redo() },
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Redo,
                    contentDescription = "Redo",
                    tint = if (state.canRedo) {
                        HdTheme.colors.architectInk
                    } else {
                        HdTheme.colors.architectGray
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (showPlanChrome && placeTool != null) {
            PlaceFurnitureBanner(
                catalogId = placeTool.catalogId,
                stamp = placeTool.stamp,
                onToggleStamp = viewModel::toggleStampMode,
                onCancel = { viewModel.setTool(EditorTool.Select) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = TopChromeClearance),
            )
        }

        if (showPlanChrome && state.tool is EditorTool.FormatPainter) {
            FormatPainterBanner(
                onCancel = { viewModel.setTool(EditorTool.Select) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = TopChromeClearance),
            )
        }

        if (showPlanChrome &&
            hasSelection &&
            state.tool is EditorTool.Select
        ) {
            if (sidePanel) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = TopChromeClearance, end = 12.dp, bottom = 108.dp)
                        .width(SidePanelWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(HdTheme.colors.ivory.copy(alpha = 0.96f))
                        .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(16.dp)),
                ) {
                    PropertySheetContent(
                        state = state,
                        unitSystem = state.unitSystem,
                        onDelete = viewModel::deleteSelected,
                        onInterior = { viewModel.setWallThickness(interior = true) },
                        onExterior = { viewModel.setWallThickness(interior = false) },
                        onRename = viewModel::renameSelection,
                        onWallLength = viewModel::setWallLength,
                        onWallHeight = viewModel::setWallHeight,
                        onWallSideColor = viewModel::setWallSideColor,
                        onWallSidePreset = viewModel::setWallSidePreset,
                        onClearWallSideTexture = viewModel::clearWallSideTexture,
                        onWallPattern = viewModel::setWallPattern,
                        onWallGlass = viewModel::setWallGlass,
                        onStraightenWall = viewModel::straightenWall,
                        onAddCurvePoint = viewModel::addCurvePoint,
                        onWallBaseboardEnabled = viewModel::setWallBaseboardEnabled,
                        onWallBaseboardHeight = viewModel::setWallBaseboardHeight,
                        onWallBaseboardThickness = viewModel::setWallBaseboardThickness,
                        onFormatPainter = viewModel::startFormatPainter,
                        onImportWallTexture = { side ->
                            pendingTextureImport = side
                            pickUserTexture.launch("image/*")
                        },
                        onFloorColor = viewModel::setFloorColor,
                        onFloorPreset = viewModel::setFloorPreset,
                        onClearFloorTexture = viewModel::clearFloorTexture,
                        onImportFloorTexture = {
                            pendingTextureImport = "floor"
                            pickUserTexture.launch("image/*")
                        },
                        onCeilingColor = viewModel::setCeilingColor,
                        onCeilingPreset = viewModel::setCeilingPreset,
                        onClearCeilingTexture = viewModel::clearCeilingTexture,
                        onRoomBorder = viewModel::setRoomBorder,
                        onCeilingVisible = viewModel::setCeilingVisible,
                        onCeilingStyle = viewModel::setCeilingStyle,
                        onRoomSize = viewModel::setRoomSize,
                        onStageRoom = viewModel::stageSelectedRoom,
                        onOpeningWidth = viewModel::setOpeningWidth,
                        onFlipHinge = viewModel::flipOpeningHinge,
                        onFlipSwing = viewModel::flipOpeningSwing,
                        onFurnitureWidth = viewModel::setFurnitureWidth,
                        onFurnitureDepth = viewModel::setFurnitureDepth,
                        onFurnitureAngleDeg = viewModel::setFurnitureAngleDeg,
                        onCopyFurniture = viewModel::copySelection,
                        onPasteFurniture = viewModel::pasteClipboard,
                        onDuplicateFurniture = viewModel::duplicateSelection,
                        onReplaceFurniture = {
                            catalogReplaceMode = true
                            showCatalog = true
                        },
                        onAlign = viewModel::alignSelection,
                        onDistribute = viewModel::distributeSelection,
                        onGroupFurniture = viewModel::groupSelection,
                        onUngroupFurniture = viewModel::ungroupSelection,
                        onMirrorFurniture = viewModel::mirrorSelection,
                        onDimensionLength = viewModel::setDimensionLength,
                        compact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            } else {
                // Phone: peek sheet — cap height so the plan stays visible (iOS inspector).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, bottom = 108.dp)
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .wrapContentHeight(align = Alignment.Bottom)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HdTheme.colors.ivory.copy(alpha = 0.96f))
                        .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    PropertySheetContent(
                        state = state,
                        unitSystem = state.unitSystem,
                        onDelete = viewModel::deleteSelected,
                        onInterior = { viewModel.setWallThickness(interior = true) },
                        onExterior = { viewModel.setWallThickness(interior = false) },
                        onRename = viewModel::renameSelection,
                        onWallLength = viewModel::setWallLength,
                        onWallHeight = viewModel::setWallHeight,
                        onWallSideColor = viewModel::setWallSideColor,
                        onWallSidePreset = viewModel::setWallSidePreset,
                        onClearWallSideTexture = viewModel::clearWallSideTexture,
                        onWallPattern = viewModel::setWallPattern,
                        onWallGlass = viewModel::setWallGlass,
                        onStraightenWall = viewModel::straightenWall,
                        onAddCurvePoint = viewModel::addCurvePoint,
                        onWallBaseboardEnabled = viewModel::setWallBaseboardEnabled,
                        onWallBaseboardHeight = viewModel::setWallBaseboardHeight,
                        onWallBaseboardThickness = viewModel::setWallBaseboardThickness,
                        onFormatPainter = viewModel::startFormatPainter,
                        onImportWallTexture = { side ->
                            pendingTextureImport = side
                            pickUserTexture.launch("image/*")
                        },
                        onFloorColor = viewModel::setFloorColor,
                        onFloorPreset = viewModel::setFloorPreset,
                        onClearFloorTexture = viewModel::clearFloorTexture,
                        onImportFloorTexture = {
                            pendingTextureImport = "floor"
                            pickUserTexture.launch("image/*")
                        },
                        onCeilingColor = viewModel::setCeilingColor,
                        onCeilingPreset = viewModel::setCeilingPreset,
                        onClearCeilingTexture = viewModel::clearCeilingTexture,
                        onRoomBorder = viewModel::setRoomBorder,
                        onCeilingVisible = viewModel::setCeilingVisible,
                        onCeilingStyle = viewModel::setCeilingStyle,
                        onRoomSize = viewModel::setRoomSize,
                        onStageRoom = viewModel::stageSelectedRoom,
                        onOpeningWidth = viewModel::setOpeningWidth,
                        onFlipHinge = viewModel::flipOpeningHinge,
                        onFlipSwing = viewModel::flipOpeningSwing,
                        onFurnitureWidth = viewModel::setFurnitureWidth,
                        onFurnitureDepth = viewModel::setFurnitureDepth,
                        onFurnitureAngleDeg = viewModel::setFurnitureAngleDeg,
                        onCopyFurniture = viewModel::copySelection,
                        onPasteFurniture = viewModel::pasteClipboard,
                        onDuplicateFurniture = viewModel::duplicateSelection,
                        onReplaceFurniture = {
                            catalogReplaceMode = true
                            showCatalog = true
                        },
                        onAlign = viewModel::alignSelection,
                        onDistribute = viewModel::distributeSelection,
                        onGroupFurniture = viewModel::groupSelection,
                        onUngroupFurniture = viewModel::ungroupSelection,
                        onMirrorFurniture = viewModel::mirrorSelection,
                        onDimensionLength = viewModel::setDimensionLength,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp),
        )
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = addSheetState,
            containerColor = HdTheme.colors.ivory,
        ) {
            AddSheetContent(
                unitSystem = state.unitSystem,
                onDrawWallInterior = {
                    showAdd = false
                    viewModel.setTool(EditorTool.DrawWall(interiorThicknessCM))
                },
                onDrawWallExterior = {
                    showAdd = false
                    viewModel.setTool(EditorTool.DrawWall(exteriorThicknessCM))
                },
                onDrawRoom = {
                    showAdd = false
                    viewModel.setTool(EditorTool.DrawRoom)
                },
                onDimension = {
                    showAdd = false
                    viewModel.setTool(EditorTool.Dimension)
                },
                onExteriorDims = {
                    showAdd = false
                    viewModel.applyExteriorDims()
                },
                onDoor = {
                    showAdd = false
                    viewModel.setTool(EditorTool.PlaceOpening(OpeningKind.Door))
                },
                onWindow = {
                    showAdd = false
                    viewModel.setTool(EditorTool.PlaceOpening(OpeningKind.Window))
                },
                onFrench = {
                    showAdd = false
                    viewModel.setTool(EditorTool.PlaceOpening(OpeningKind.FrenchDoor))
                },
                onFurniture = {
                    showAdd = false
                    catalogReplaceMode = false
                    showCatalog = true
                },
                onTrace = {
                    showAdd = false
                    pickTrace.launch("image/*")
                },
            )
        }
    }

    if (showCatalog) {
        ModalBottomSheet(
            onDismissRequest = {
                showCatalog = false
                catalogReplaceMode = false
            },
            sheetState = catalogSheetState,
            containerColor = HdTheme.colors.ivory,
        ) {
            FurniturePickerContent(
                unitSystem = state.unitSystem,
                recentIds = state.recentFurnitureIds,
                title = if (catalogReplaceMode) "Replace with…" else "Catalog",
                onPick = { entry ->
                    showCatalog = false
                    viewModel.recordRecentFurniture(entry.id)
                    if (catalogReplaceMode) {
                        catalogReplaceMode = false
                        viewModel.replaceFurniture(entry)
                    } else {
                        viewModel.setTool(EditorTool.PlaceFurniture(entry.id))
                    }
                },
            )
        }
    }
}

@Composable
private fun EditorTipBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HdTheme.colors.selection.copy(alpha = 0.12f))
            .border(1.dp, HdTheme.colors.selection.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Tip: Add → wall to draw · pinch to zoom · two fingers to pan",
            style = HdTheme.typography.labelMedium,
            color = HdTheme.colors.architectInk,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Dismiss tip",
                tint = HdTheme.colors.architectInk,
            )
        }
    }
}

@Composable
private fun FloorSelectorButton(
    levels: List<Level>,
    selectedLevelID: String?,
    ghostOtherLevels: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSelectLevel: (String) -> Unit,
    onAddFloor: () -> Unit,
    onToggleGhost: () -> Unit,
) {
    val ordered = remember(levels) { LevelMutation.orderedVisible(levels) }
    if (ordered.isEmpty()) return
    val active = ordered.firstOrNull { it.id == selectedLevelID } ?: ordered.first()
    Box {
        IconButton(onClick = { onMenuOpenChange(true) }) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(HdTheme.colors.selection),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = LevelMutation.elevatorLabel(active),
                    style = HdTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HdTheme.colors.paper,
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { onMenuOpenChange(false) },
        ) {
            ordered.forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = LevelMutation.menuLabel(level),
                            fontWeight = if (level.id == active.id) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onSelectLevel(level.id)
                    },
                    leadingIcon = if (level.id == active.id) {
                        {
                            Icon(
                                Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = HdTheme.colors.selection,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add floor on top") },
                onClick = {
                    onMenuOpenChange(false)
                    onAddFloor()
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                },
            )
            if (ordered.size > 1) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (ghostOtherLevels) "Hide other floors" else "Show ghosted floors",
                        )
                    },
                    onClick = {
                        onMenuOpenChange(false)
                        onToggleGhost()
                    },
                )
            }
        }
    }
}

@Composable
private fun FormatPainterBanner(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HdTheme.colors.ivory.copy(alpha = 0.94f))
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Tap walls to paint",
            style = HdTheme.typography.labelMedium,
            color = HdTheme.colors.architectInk,
        )
        Text(
            text = "Done",
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.selectionDeep,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PlaceFurnitureBanner(
    catalogId: String?,
    stamp: Boolean,
    onToggleStamp: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = catalogId?.let { catalogById(it)?.name } ?: "Furniture"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HdTheme.colors.ivory.copy(alpha = 0.94f))
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Place $name",
            style = HdTheme.typography.labelMedium,
            color = HdTheme.colors.architectInk,
        )
        Text(
            text = "Stamp",
            style = HdTheme.typography.labelSmall,
            color = if (stamp) HdTheme.colors.paper else HdTheme.colors.architectInk,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (stamp) HdTheme.colors.selection else HdTheme.colors.highlight)
                .clickable(onClick = onToggleStamp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = "Done",
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.selectionDeep,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TraceUnderlayCapsule(
    widthCM: Double,
    onWidthCM: (Double) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(HdTheme.colors.ivory.copy(alpha = 0.94f))
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            tint = HdTheme.colors.architectInk,
            modifier = Modifier.size(16.dp),
        )
        Slider(
            value = (widthCM / 100.0).toFloat(),
            onValueChange = { onWidthCM(it.toDouble() * 100.0) },
            valueRange = (TRACE_MIN_CM / 100.0).toFloat()..(TRACE_MAX_CM / 100.0).toFloat(),
            steps = ((TRACE_MAX_CM - TRACE_MIN_CM) / 100.0).roundToInt() - 1,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = formatTraceWidthLabel(widthCM),
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.architectInk,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove trace image",
                tint = HdTheme.colors.architectInk,
            )
        }
    }
}

/** iOS EditorModeSwitchDeck — icon modes (2D / Split / 3D / AR). Walk lives on the dock. */
@Composable
private fun EditorModeIconSwitch(
    selected: EditorViewMode,
    onSelect: (EditorViewMode) -> Unit,
    showSplit: Boolean = false,
) {
    data class ModeIcon(val mode: EditorViewMode, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
    val modes = buildList {
        add(ModeIcon(EditorViewMode.Plan2D, Icons.Outlined.GridView, "2D"))
        if (showSplit) add(ModeIcon(EditorViewMode.Split, Icons.Outlined.ViewQuilt, "Split"))
        add(ModeIcon(EditorViewMode.View3D, Icons.Outlined.ViewInAr, "3D"))
        add(ModeIcon(EditorViewMode.AR, Icons.Outlined.ViewInAr, "AR"))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HdTheme.colors.sand.copy(alpha = 0.7f))
            .padding(3.dp),
    ) {
        modes.forEach { item ->
            val active = selected == item.mode ||
                (item.mode == EditorViewMode.View3D && selected == EditorViewMode.Walk)
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (active) HdTheme.colors.architectInk else HdTheme.colors.architectGray,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) HdTheme.colors.ivory else Color.Transparent)
                    .clickable { onSelect(item.mode) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun ViewModeChips(
    selected: EditorViewMode,
    onSelect: (EditorViewMode) -> Unit,
    showSplit: Boolean = false,
) {
    EditorModeIconSwitch(selected = selected, onSelect = onSelect, showSplit = showSplit)
}

@Composable
private fun UnitSystemChips(
    selected: UnitSystem,
    onSelect: (UnitSystem) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HdTheme.colors.highlight)
            .padding(2.dp),
    ) {
        listOf(
            UnitSystem.Millimetre to "mm",
            UnitSystem.Metric to "cm",
            UnitSystem.Imperial to "ft",
        ).forEach { (system, label) ->
            val active = selected == system
            Text(
                text = label,
                style = HdTheme.typography.labelSmall,
                color = if (active) HdTheme.colors.paper else HdTheme.colors.architectInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) HdTheme.colors.architectInk else Color.Transparent)
                    .clickable { onSelect(system) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun AddSheetContent(
    unitSystem: UnitSystem,
    onDrawWallInterior: () -> Unit,
    onDrawWallExterior: () -> Unit,
    onDrawRoom: () -> Unit,
    onDimension: () -> Unit,
    onExteriorDims: () -> Unit,
    onDoor: () -> Unit,
    onWindow: () -> Unit,
    onFrench: () -> Unit,
    onFurniture: () -> Unit,
    onTrace: () -> Unit,
) {
    val intLabel = UnitFormat.length(interiorThicknessCM, unitSystem)
    val extLabel = UnitFormat.length(exteriorThicknessCM, unitSystem)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Add", style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        SheetRow("Interior wall · $intLabel", "Tap start, tap end; tap again to finish", onDrawWallInterior)
        SheetRow("Exterior wall · $extLabel", "Tap start, tap end; tap again to finish", onDrawWallExterior)
        SheetRow("Room", "Drag a rectangle", onDrawRoom)
        SheetRow("Door", "Tap a wall to insert", onDoor)
        SheetRow("Window", "Tap a wall to insert", onWindow)
        SheetRow("French door", "Tap a wall to insert", onFrench)
        SheetRow("Dimension", "Tap two points", onDimension)
        SheetRow("Exterior dims", "Auto-chain outer face dimensions for this level", onExteriorDims)
        SheetRow("Furniture…", "Pick from the catalog", onFurniture)
        SheetRow("Trace photo…", "Photo under the plan at 35% opacity", onTrace)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HdTheme.colors.paper)
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, style = HdTheme.typography.titleSmall, color = HdTheme.colors.ink)
        Text(subtitle, style = HdTheme.typography.bodySmall, color = HdTheme.colors.stone)
    }
}

@Composable
private fun DockItem(
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    ink: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Equal-width slots keep icon + label centered (iOS dock mono labels).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    when {
                        ink -> HdTheme.colors.architectInk
                        active -> HdTheme.colors.selection
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = HdTheme.colors.architectGray,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
    }
}
