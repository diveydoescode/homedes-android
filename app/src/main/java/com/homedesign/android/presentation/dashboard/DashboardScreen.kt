package com.homedesign.android.presentation.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OtherHouses
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homedesign.android.core.ui.hdGlassCapsule
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
    val totalCount: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
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
            totalCount = projects.size,
            firstName = settings.firstName,
            lastName = settings.lastName,
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
    /** iOS "From a template" secondary sheet (showcase / sample / import). */
    var showTemplateSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<ProjectOverlay?>(null) }
    // Expand fully so template / import rows are not clipped on phone heights.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    val searchEmpty = state.search.isBlank()
    val showHero = searchEmpty && state.sort == SortOrder.Recent && state.projects.isNotEmpty()
    val hero = if (showHero) state.projects.first() else null
    val grid = if (hero != null) state.projects.drop(1) else state.projects
    val initial = dashboardInitials(state.firstName, state.lastName)

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
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DashboardChrome(
                    designCount = state.totalCount,
                    initials = initial,
                    search = state.search,
                    onSearch = viewModel::setSearch,
                    sort = state.sort,
                    sortMenu = sortMenu,
                    onSortMenu = { sortMenu = it },
                    onSort = viewModel::setSort,
                )
            }

            if (searchEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ShowcaseSection(
                        onOpenVilla = { viewModel.openShowcase(onOpenProject) },
                        onOpenSample = { viewModel.openSampleSh3d(onOpenProject) },
                    )
                }
            }

            if (state.totalCount == 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyDesigns(onStart = { showNewSheet = true })
                }
            } else if (state.projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NoMatches(query = state.search)
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
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionEyebrow(
                            text = "All designs",
                            modifier = Modifier.padding(top = 24.dp, bottom = 2.dp),
                        )
                    }
                }
                if (!showHero) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(6.dp))
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

        NewDesignFab(
            onClick = { showNewSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp),
        )

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }

    // iOS NewDesignSheet parity: Blank / From a sketch / From a template.
    if (showNewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSheet = false },
            sheetState = sheetState,
            containerColor = HdTheme.colors.paper,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                Text("New design", style = HdTheme.typography.headlineSmall, color = HdTheme.colors.ink)
                Text(
                    "Three ways in.",
                    style = HdTheme.typography.bodySmall,
                    color = HdTheme.colors.stone,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                NewActionRow(
                    title = "Blank floor plan",
                    subtitle = "Start from an empty canvas",
                    onClick = {
                        showNewSheet = false
                        viewModel.createBlank(onOpenProject)
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "From a sketch",
                    subtitle = "Photograph a hand-drawn plan",
                    icon = true,
                    onClick = {
                        showNewSheet = false
                        onOpenSketch()
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "From a template",
                    subtitle = "Showcase villa, sample plans, imports…",
                    folder = true,
                    onClick = {
                        showNewSheet = false
                        showTemplateSheet = true
                    },
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showTemplateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTemplateSheet = false },
            sheetState = templateSheetState,
            containerColor = HdTheme.colors.paper,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                Text("From a template", style = HdTheme.typography.headlineSmall, color = HdTheme.colors.ink)
                Spacer(Modifier.height(16.dp))
                NewActionRow(
                    title = "Showcase villa",
                    subtitle = "Sample plan with curved wall & rooms",
                    onClick = {
                        showTemplateSheet = false
                        viewModel.openShowcase(onOpenProject)
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "Sample .sh3d",
                    subtitle = "Bundled test plan (walls & furniture)",
                    folder = true,
                    onClick = {
                        showTemplateSheet = false
                        viewModel.openSampleSh3d(onOpenProject)
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "Open .homedesign",
                    subtitle = "Import a plan from storage",
                    folder = true,
                    onClick = {
                        showTemplateSheet = false
                        pickHomedesign.launch("*/*")
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "Open .sh3d",
                    subtitle = "Import walls, rooms, furniture from archive",
                    folder = true,
                    onClick = {
                        showTemplateSheet = false
                        pickSh3d.launch("*/*")
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
            .padding(top = 90.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, HdTheme.colors.stone.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .border(1.dp, HdTheme.colors.stone.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("No designs yet", style = HdTheme.typography.headlineSmall, color = HdTheme.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start from a blank plan, a photo of a sketch, or one of our templates.",
            style = HdTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            color = HdTheme.colors.graphite,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HdTheme.colors.ink)
                .clickable(onClick = onStart)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "New design",
                style = HdTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = HdTheme.colors.paper,
            )
        }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

private fun dashboardInitials(firstName: String, lastName: String): String {
    val letters = listOf(firstName, lastName)
        .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
        .joinToString("")
    return letters.ifEmpty { "HD" }.take(2)
}

@Composable
private fun Modifier.dashboardPill(): Modifier =
    hdGlassCapsule(
        backdrop = null,
        fallbackFill = HdTheme.colors.ivory,
    )

@Composable
private fun DashboardChrome(
    designCount: Int,
    initials: String,
    search: String,
    onSearch: (String) -> Unit,
    sort: SortOrder,
    sortMenu: Boolean,
    onSortMenu: (Boolean) -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Designs",
                    style = HdTheme.typography.displaySmall.copy(
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = HdTheme.colors.ink,
                )
                Text(
                    text = if (designCount == 1) "1 design" else "$designCount designs",
                    style = HdTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                    ),
                    color = HdTheme.colors.stone,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(HdTheme.colors.sand)
                    .border(0.5.dp, HdTheme.colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = HdTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = HdTheme.colors.ink,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .dashboardPill()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = HdTheme.colors.stone,
                    modifier = Modifier.size(16.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (search.isEmpty()) {
                        Text(
                            text = "Search designs",
                            style = HdTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = HdTheme.colors.stone,
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = onSearch,
                        singleLine = true,
                        textStyle = HdTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = HdTheme.colors.ink,
                        ),
                        cursorBrush = SolidColor(HdTheme.colors.terracotta),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (search.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        tint = HdTheme.colors.stone,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSearch("") },
                    )
                }
            }

            Box {
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .dashboardPill()
                        .clickable { onSortMenu(true) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = sort.name,
                        style = HdTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = HdTheme.colors.ink,
                    )
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = HdTheme.colors.ink,
                        modifier = Modifier.size(10.dp),
                    )
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { onSortMenu(false) }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.name) },
                            onClick = {
                                onSort(order)
                                onSortMenu(false)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text.uppercase(),
            style = HdTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = HdTheme.colors.stone,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(HdTheme.colors.hairline),
        )
    }
}

/** Horizontal gallery matching iOS; extras reuse Villa Bianca + bundled sample loaders. */
@Composable
private fun ShowcaseSection(
    onOpenVilla: () -> Unit,
    onOpenSample: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionEyebrow(text = "Showcase")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShowcaseCard(
                name = "Villa Bianca",
                blurb = "Italian one-storey · 3 beds",
                icon = Icons.Outlined.Home,
                onClick = onOpenVilla,
            )
            ShowcaseCard(
                name = "Villa Aurelia",
                blurb = "Italian villa · patio fountain",
                icon = Icons.Outlined.OtherHouses,
                onClick = onOpenVilla,
            )
            ShowcaseCard(
                name = "Sample plan",
                blurb = "Bundled test plan · walls & furniture",
                icon = Icons.Outlined.Apartment,
                onClick = onOpenSample,
            )
            ShowcaseCard(
                name = "English Villa",
                blurb = "Classic English home",
                icon = Icons.Outlined.Cottage,
                onClick = onOpenSample,
            )
            ShowcaseCard(
                name = "Alpine Hotel",
                blurb = "Chalet hotel · many rooms",
                icon = Icons.Outlined.Hotel,
                onClick = onOpenSample,
            )
        }
    }
}

@Composable
private fun ShowcaseCard(
    name: String,
    blurb: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val thumb = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 168.dp, height = 108.dp)
                .clip(thumb)
                .background(
                    Brush.linearGradient(
                        listOf(
                            HdTheme.colors.terracotta.copy(alpha = 0.22f),
                            HdTheme.colors.sand.copy(alpha = 0.55f),
                        ),
                    ),
                )
                .border(0.5.dp, HdTheme.colors.hairline, thumb),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HdTheme.colors.terracotta,
                modifier = Modifier.size(34.dp),
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = HdTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = HdTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = blurb,
                style = HdTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = HdTheme.colors.stone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoMatches(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No matches",
            style = HdTheme.typography.headlineSmall,
            color = HdTheme.colors.ink,
        )
        Text(
            text = "Nothing matches “$query”.",
            style = HdTheme.typography.bodySmall,
            color = HdTheme.colors.stone,
        )
    }
}

@Composable
private fun NewDesignFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(1.5.dp, HdTheme.colors.terracotta.copy(alpha = 0.55f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(elevation = 14.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(HdTheme.colors.ink)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "New design",
                tint = HdTheme.colors.paper,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
