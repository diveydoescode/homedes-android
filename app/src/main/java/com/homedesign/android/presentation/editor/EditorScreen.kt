package com.homedesign.android.presentation.editor

import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.catalog.CATALOG_ENTRIES
import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.geom.exteriorThicknessCM
import com.homedesign.android.domain.geom.interiorThicknessCM
import com.homedesign.android.domain.model.Selection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenSketch: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var exportOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val addSheetState = rememberModalBottomSheetState()
    val catalogSheetState = rememberModalBottomSheetState()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper),
    ) {
        PlanCanvas(
            home = state.home,
            selection = state.selection,
            tool = state.tool,
            preview = state.preview,
            onTap = viewModel::onPlanTap,
            onDrawWallArm = viewModel::onDrawWallArm,
            onDrawWallDrag = viewModel::onDrawWallDrag,
            onDrawWallCommit = { plan ->
                val thickness = (state.tool as? EditorTool.DrawWall)?.thickness
                    ?: com.homedesign.android.domain.geom.defaultWallThicknessCM
                viewModel.onDrawWallCommit(plan, thickness)
            },
            onDrawRoomDrag = viewModel::onDrawRoomDrag,
            onDrawRoomCommit = viewModel::onDrawRoomCommit,
            onCancelPreview = viewModel::cancelPreview,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 100.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(HdTheme.colors.ivory.copy(alpha = 0.92f))
                    .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(20.dp))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
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
                    )
                    Text(
                        text = state.savedLabel,
                        style = HdTheme.typography.labelSmall,
                        color = HdTheme.colors.architectGray,
                    )
                }
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(Icons.Outlined.Undo, contentDescription = "Undo", tint = HdTheme.colors.architectInk)
                }
                IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                    Icon(Icons.Outlined.Redo, contentDescription = "Redo", tint = HdTheme.colors.architectInk)
                }
                Box {
                    IconButton(onClick = { exportOpen = true }) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Export",
                            tint = HdTheme.colors.architectInk,
                        )
                    }
                    DropdownMenu(expanded = exportOpen, onDismissRequest = { exportOpen = false }) {
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
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HdTheme.colors.ivory.copy(alpha = 0.94f))
                .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val measure = state.tool is EditorTool.Dimension
            DockItem(
                label = if (measure) "Measure" else "Edit",
                active = measure || state.tool is EditorTool.Select,
                onClick = {
                    if (measure) viewModel.setTool(EditorTool.Select)
                    else viewModel.setTool(EditorTool.Dimension)
                },
            ) {
                Icon(
                    Icons.Outlined.Straighten,
                    contentDescription = null,
                    tint = if (measure) HdTheme.colors.paper else HdTheme.colors.architectInk,
                    modifier = Modifier.size(18.dp),
                )
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
        }

        if (hasSelection && state.tool is EditorTool.Select) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 108.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(HdTheme.colors.ivory.copy(alpha = 0.96f))
                    .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(18.dp))
                    .padding(12.dp),
            ) {
                PropertySheetContent(
                    state = state,
                    onDelete = viewModel::deleteSelected,
                    onInterior = { viewModel.setWallThickness(interior = true) },
                    onExterior = { viewModel.setWallThickness(interior = false) },
                    onRename = viewModel::renameSelection,
                    compact = true,
                )
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
                onDrawWall = {
                    showAdd = false
                    viewModel.setTool(EditorTool.DrawWall())
                },
                onDrawRoom = {
                    showAdd = false
                    viewModel.setTool(EditorTool.DrawRoom)
                },
                onDimension = {
                    showAdd = false
                    viewModel.setTool(EditorTool.Dimension)
                },
                onFurniture = {
                    showAdd = false
                    showCatalog = true
                },
            )
        }
    }

    if (showCatalog) {
        ModalBottomSheet(
            onDismissRequest = { showCatalog = false },
            sheetState = catalogSheetState,
            containerColor = HdTheme.colors.ivory,
        ) {
            FurniturePickerContent(
                entries = CATALOG_ENTRIES.filter { !it.doorOrWindow },
                onPick = { entry ->
                    showCatalog = false
                    viewModel.setTool(EditorTool.PlaceFurniture(entry.id))
                },
            )
        }
    }
}

@Composable
private fun AddSheetContent(
    onDrawWall: () -> Unit,
    onDrawRoom: () -> Unit,
    onDimension: () -> Unit,
    onFurniture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Add", style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        SheetRow("Wall", "Tap start, tap or drag end", onDrawWall)
        SheetRow("Room", "Drag a rectangle", onDrawRoom)
        SheetRow("Dimension", "Tap two points", onDimension)
        SheetRow("Furniture…", "Pick from the catalog", onFurniture)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FurniturePickerContent(
    entries: List<CatalogEntry>,
    onPick: (CatalogEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        Text("Catalog", style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.id }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(entry) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(entry.name, style = HdTheme.typography.titleSmall, color = HdTheme.colors.ink)
                        Text(entry.category, style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
                    }
                    Text(
                        "${entry.width.toInt()}×${entry.depth.toInt()}",
                        style = HdTheme.typography.labelSmall,
                        color = HdTheme.colors.architectGray,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PropertySheetContent(
    state: EditorUiState,
    onDelete: () -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onRename: (String) -> Unit,
    compact: Boolean = false,
) {
    val home = state.home
    val selection = state.selection
    var name by remember(selection) {
        mutableStateOf(
            when (selection) {
                is Selection.Room -> home.rooms.find { it.id == selection.id }?.name.orEmpty()
                is Selection.Furniture -> home.furniture.find { it.id == selection.id }?.name.orEmpty()
                is Selection.Wall -> "Wall"
                is Selection.Endpoint -> "Wall"
                is Selection.Opening -> "Opening"
                is Selection.Annotation -> if (selection.isLabel) "Label" else "Dimension"
                else -> ""
            },
        )
    }
    val wall = when (selection) {
        is Selection.Wall -> home.walls.find { it.id == selection.id }
        is Selection.Endpoint -> home.walls.find { it.id == selection.wallID }
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        if (!compact) {
            Text("Properties", style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        }
        if (selection is Selection.Room || selection is Selection.Furniture) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onRename(it)
                },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        } else {
            Text(
                name.ifBlank { "Selection" },
                style = HdTheme.typography.titleMedium,
                color = HdTheme.colors.ink,
            )
        }

        if (wall != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kotlin.math.abs(wall.thickness - interiorThicknessCM) < 0.1,
                    onClick = onInterior,
                    label = { Text("Interior 10") },
                )
                FilterChip(
                    selected = kotlin.math.abs(wall.thickness - exteriorThicknessCM) < 0.1,
                    onClick = onExterior,
                    label = { Text("Exterior 20") },
                )
            }
        }

        TextButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = HdTheme.colors.destructive)
            Spacer(Modifier.width(8.dp))
            Text("Delete", color = HdTheme.colors.destructive)
        }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        ink -> HdTheme.colors.architectInk
                        active -> HdTheme.colors.selection
                        else -> HdTheme.colors.highlight
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = HdTheme.typography.labelSmall, color = HdTheme.colors.architectGray)
    }
}
