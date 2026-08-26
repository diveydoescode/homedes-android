package com.homedesign.android.presentation.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homedesign.android.core.ui.relativeTime
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.io.HomedesignZip
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.project.ProjectMeta
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder { Recent, Name, Oldest }

data class DashboardUiState(
    val projects: List<ProjectMeta> = emptyList(),
    val firstName: String = "",
    val search: String = "",
    val sort: SortOrder = SortOrder.Recent,
    val unitSystem: UnitSystem = UnitSystem.Millimetre,
    val resumeProjectId: String? = null,
    val resumeProjectName: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val search = kotlinx.coroutines.flow.MutableStateFlow("")
    private val sort = kotlinx.coroutines.flow.MutableStateFlow(SortOrder.Recent)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        projectRepository.observeProjects(),
        settingsRepository.settings,
        search,
        sort,
    ) { projects, settings, q, s ->
        val filtered = if (q.isBlank()) {
            projects
        } else {
            projects.filter { it.name.contains(q, ignoreCase = true) }
        }
        val sorted = when (s) {
            SortOrder.Recent -> filtered.sortedByDescending { it.updatedAt }
            SortOrder.Name -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.Oldest -> filtered.sortedBy { it.updatedAt }
        }
        val resumeId = settings.lastProjectId?.takeIf { settings.editorSessionDirty }
            ?.takeIf { id -> projects.any { it.id == id } }
        DashboardUiState(
            projects = sorted,
            firstName = settings.firstName,
            search = q,
            sort = s,
            unitSystem = settings.unitSystem,
            resumeProjectId = resumeId,
            resumeProjectName = resumeId?.let { id -> projects.find { it.id == id }?.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setSearch(value: String) {
        search.value = value
    }

    fun setSort(value: SortOrder) {
        sort.value = value
    }

    fun createBlank(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val meta = projectRepository.createBlank()
            onCreated(meta.id)
        }
    }

    fun openHomedesign(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read file")
                }
                val home = HomedesignZip.decode(bytes)
                val meta = projectRepository.createFromHome(home)
                onOpened(meta.id)
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Could not open .homedesign")
            }
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { projectRepository.rename(id, name) }
    }

    fun delete(id: String) {
        viewModelScope.launch { projectRepository.delete(id) }
    }

    fun openSh3d(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read file")
                }
                val home = withContext(Dispatchers.Default) {
                    com.homedesign.android.domain.io.SH3DReader.read(
                        bytes,
                        // filesDir survives process death; cacheDir can be wiped.
                        cacheDirectory = java.io.File(appContext.filesDir, "HomeMeshes/${java.util.UUID.randomUUID()}"),
                    )
                }
                val meta = projectRepository.createFromHome(home)
                onOpened(meta.id)
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Could not open .sh3d")
            }
        }
    }

    fun openShowcase(onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val home = withContext(Dispatchers.Default) {
                    com.homedesign.android.domain.project.ShowcaseVilla.make()
                }
                val meta = projectRepository.createFromHome(home)
                onOpened(meta.id)
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Could not open showcase")
            }
        }
    }

    fun openSampleSh3d(onOpened: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.assets.open("samples/test_small.sh3d").use { it.readBytes() }
                }
                val home = withContext(Dispatchers.Default) {
                    com.homedesign.android.domain.io.SH3DReader.read(
                        bytes,
                        cacheDirectory = java.io.File(appContext.filesDir, "HomeMeshes/${java.util.UUID.randomUUID()}"),
                    )
                }
                val meta = projectRepository.createFromHome(home, name = "Sample plan")
                onOpened(meta.id)
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Could not open sample")
            }
        }
    }

    fun dismissResumeSession() {
        viewModelScope.launch { settingsRepository.clearEditorSession() }
    }
}

private sealed interface ProjectOverlay {
    data class Info(val meta: ProjectMeta, val draftName: String) : ProjectOverlay
    data class ConfirmDelete(val meta: ProjectMeta) : ProjectOverlay
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onOpenProject: (String) -> Unit,
    onOpenSketch: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<ProjectOverlay?>(null) }
    // Expand fully so "Sample .sh3d" / "From sketch" are not clipped on phone heights.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbar = remember { SnackbarHostState() }

    val pickHomedesign = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.openHomedesign(uri, onOpenProject)
    }
    val pickSh3d = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.openSh3d(uri, onOpenProject)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg -> snackbar.showSnackbar(msg) }
    }

    val showHero = state.search.isBlank() && state.sort == SortOrder.Recent && state.projects.isNotEmpty()
    val hero = if (showHero) state.projects.first() else null
    val grid = if (hero != null) state.projects.drop(1) else state.projects
    val initial = state.firstName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "·"

    fun openInfo(meta: ProjectMeta) {
        overlay = ProjectOverlay.Info(meta, meta.name)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Home Design",
                                style = HdTheme.typography.labelMedium,
                                color = HdTheme.colors.stone,
                            )
                            Text(
                                text = "Designs",
                                style = HdTheme.typography.headlineLarge,
                                color = HdTheme.colors.ink,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(HdTheme.colors.highlight)
                                .border(1.dp, HdTheme.colors.hairline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(initial, style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = viewModel::setSearch,
                            placeholder = { Text("Search designs", color = HdTheme.colors.stone.copy(alpha = 0.6f)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = HdTheme.colors.stone)
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HdTheme.colors.terracotta,
                                unfocusedBorderColor = HdTheme.colors.hairline,
                                focusedContainerColor = HdTheme.colors.ivory,
                                unfocusedContainerColor = HdTheme.colors.ivory,
                            ),
                        )
                        Box {
                            Text(
                                text = when (state.sort) {
                                    SortOrder.Recent -> "Recent"
                                    SortOrder.Name -> "Name"
                                    SortOrder.Oldest -> "Oldest"
                                },
                                style = HdTheme.typography.labelLarge,
                                color = HdTheme.colors.ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(12.dp))
                                    .clickable { sortMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                            )
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.name) },
                                        onClick = {
                                            viewModel.setSort(order)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyDesigns(onStart = { showNewSheet = true })
                }
            } else {
                hero?.let { meta ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HeroCard(
                            meta = meta,
                            unitSystem = state.unitSystem,
                            onClick = { onOpenProject(meta.id) },
                            onLongClick = { openInfo(meta) },
                            onMore = { openInfo(meta) },
                        )
                    }
                }
                if (grid.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        ) {
                            Text(
                                text = "All designs",
                                style = HdTheme.typography.labelMedium,
                                color = HdTheme.colors.stone,
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(HdTheme.colors.hairline),
                            )
                        }
                    }
                }
                items(grid, key = { it.id }) { meta ->
                    ProjectCard(
                        meta = meta,
                        unitSystem = state.unitSystem,
                        onClick = { onOpenProject(meta.id) },
                        onLongClick = { openInfo(meta) },
                        onMore = { openInfo(meta) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewSheet = true },
            containerColor = HdTheme.colors.ink,
            contentColor = HdTheme.colors.paper,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "New design")
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }

    if (showNewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSheet = false },
            sheetState = sheetState,
            containerColor = HdTheme.colors.ivory,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text("Start a design", style = HdTheme.typography.headlineSmall, color = HdTheme.colors.ink)
                Spacer(Modifier.height(16.dp))
                NewActionRow(
                    title = "Blank plan",
                    subtitle = "Empty canvas with walls & rooms",
                    onClick = {
                        showNewSheet = false
                        viewModel.createBlank(onOpenProject)
                    },
                )
                Spacer(Modifier.height(10.dp))
                NewActionRow(
                    title = "Open .homedesign",
                    subtitle = "Import a plan from storage",
                    folder = true,
                    onClick = {
                        showNewSheet = false
                        pickHomedesign.launch("*/*")
                    },
                )
                Spacer(Modifier.height(10.dp))
                NewActionRow(
                    title = "Open .sh3d",
                    subtitle = "Import walls, rooms, furniture from archive",
                    folder = true,
                    onClick = {
                        showNewSheet = false
                        pickSh3d.launch("*/*")
                    },
                )
                Spacer(Modifier.height(10.dp))
                NewActionRow(
                    title = "Showcase villa",
                    subtitle = "Sample plan with curved wall & rooms",
                    onClick = {
                        showNewSheet = false
                        viewModel.openShowcase(onOpenProject)
                    },
                )
                Spacer(Modifier.height(10.dp))
                NewActionRow(
                    title = "Sample .sh3d",
                    subtitle = "Bundled test plan (walls & furniture)",
                    folder = true,
                    onClick = {
                        showNewSheet = false
                        viewModel.openSampleSh3d(onOpenProject)
                    },
                )
                Spacer(Modifier.height(10.dp))
                NewActionRow(
                    title = "From sketch",
                    subtitle = "Photograph a hand-drawn plan",
                    icon = true,
                    onClick = {
                        showNewSheet = false
                        onOpenSketch()
                    },
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    val resumeId = state.resumeProjectId
    if (resumeId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResumeSession() },
            title = { Text("Resume editing?", color = HdTheme.colors.ink) },
            text = {
                Text(
                    text = state.resumeProjectName?.let { "“$it” had unsaved changes when you left." }
                        ?: "A design had unsaved changes when you left.",
                    color = HdTheme.colors.stone,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissResumeSession()
                        onOpenProject(resumeId)
                    },
                ) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResumeSession() }) {
                    Text("Dismiss")
                }
            },
        )
    }

    when (val current = overlay) {
        is ProjectOverlay.Info -> {
            AlertDialog(
                onDismissRequest = { overlay = null },
                title = { Text("Design", color = HdTheme.colors.ink) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = current.draftName,
                            onValueChange = { overlay = current.copy(draftName = it) },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${UnitFormat.area(current.meta.floorAreaM2, state.unitSystem)} · updated ${relativeTime(current.meta.updatedAt)}",
                            style = HdTheme.typography.bodySmall,
                            color = HdTheme.colors.stone,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.rename(current.meta.id, current.draftName)
                            overlay = null
                        },
                    ) { Text("Done") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            overlay = ProjectOverlay.ConfirmDelete(current.meta)
                        },
                    ) {
                        Text("Delete", color = HdTheme.colors.destructive)
                    }
                },
            )
        }
        is ProjectOverlay.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = { overlay = null },
                title = { Text("Delete design?") },
                text = {
                    Text("Delete ${current.meta.name}? This cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(current.meta.id)
                            overlay = null
                        },
                    ) {
                        Text("Delete", color = HdTheme.colors.destructive)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { overlay = null }) { Text("Cancel") }
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun EmptyDesigns(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HdTheme.colors.highlight)
                .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.height(18.dp))
        Text("No designs yet", style = HdTheme.typography.headlineSmall, color = HdTheme.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start from a blank plan, a photo of a sketch, or one of our templates.",
            style = HdTheme.typography.bodyMedium,
            color = HdTheme.colors.stone,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Start a design →",
            style = HdTheme.typography.labelLarge,
            color = HdTheme.colors.terracotta,
            modifier = Modifier.clickable(onClick = onStart),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCard(
    meta: ProjectMeta,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HdTheme.colors.highlight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .clip(RoundedCornerShape(12.dp))
                .background(HdTheme.colors.sand),
            contentAlignment = Alignment.Center,
        ) {
            PlanThumbImage(meta = meta, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More for ${meta.name}",
                    tint = HdTheme.colors.ink,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Continue",
            style = HdTheme.typography.labelMedium,
            color = HdTheme.colors.terracotta,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(meta.name, style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        Text(
            text = buildString {
                append(UnitFormat.area(meta.floorAreaM2, unitSystem))
                append(" · ")
                append(relativeTime(meta.updatedAt))
                if (meta.levelCount > 1) {
                    append(" · ")
                    append(meta.levelCount)
                    append(" floors")
                }
            },
            style = HdTheme.typography.bodySmall,
            color = HdTheme.colors.stone,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    meta: ProjectMeta,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
            .background(HdTheme.colors.ivory)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(10.dp))
                .background(HdTheme.colors.highlight),
            contentAlignment = Alignment.Center,
        ) {
            PlanThumbImage(meta = meta, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "More for ${meta.name}",
                    tint = HdTheme.colors.ink,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(meta.name, style = HdTheme.typography.titleSmall, color = HdTheme.colors.ink, maxLines = 1)
        Text(
            text = buildString {
                append(UnitFormat.area(meta.floorAreaM2, unitSystem))
                append(" · ")
                append(relativeTime(meta.updatedAt))
                if (meta.levelCount > 1) {
                    append(" · ")
                    append(meta.levelCount)
                    append(" floors")
                }
            },
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.stone,
            maxLines = 2,
        )
    }
}

@Composable
private fun PlanThumbImage(meta: ProjectMeta, modifier: Modifier = Modifier) {
    val bytes = meta.thumbnailJpeg
    if (bytes != null && bytes.isNotEmpty()) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(bytes)
                .memoryCacheKey("project-thumb-${meta.id}-${meta.updatedAt}")
                .build(),
            contentDescription = "Plan preview for ${meta.name}",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Text(
            text = "Plan",
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.stone,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun NewActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: Boolean = false,
    folder: Boolean = false,
) {
    val glyph = when {
        folder -> Icons.Outlined.FolderOpen
        icon -> Icons.Outlined.CameraAlt
        else -> Icons.Outlined.Add
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HdTheme.colors.paper)
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HdTheme.colors.tintWarm),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = HdTheme.colors.terracotta,
            )
        }
        Column {
            Text(title, style = HdTheme.typography.titleMedium, color = HdTheme.colors.ink)
            Text(subtitle, style = HdTheme.typography.bodySmall, color = HdTheme.colors.stone)
        }
    }
}
