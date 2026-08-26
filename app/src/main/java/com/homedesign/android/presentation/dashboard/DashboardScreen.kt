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
import androidx.compose.foundation.layout.offset
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
import androidx.annotation.DrawableRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homedesign.android.core.ui.HdSfIcons
import com.homedesign.android.core.ui.SfIcon
import com.homedesign.android.core.ui.hdGlassCapsule
import com.homedesign.android.core.ui.relativeTime
import com.homedesign.android.core.ui.theme.HdMono
import com.homedesign.android.core.ui.theme.HdSerif
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
                val home = HomedesignZip.decode(
                    bytes,
                    HomedesignZip.embeddedTextureDirectory(appContext.filesDir),
                )
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

    init {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { projectRepository.refreshThumbnails() }
        }
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
                // iOS NewDesignSheet: "New " serif + italic "design" on one line.
                Text(
                    text = buildAnnotatedString {
                        append("New ")
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                fontFamily = HdSerif,
                            ),
                        ) {
                            append("design")
                        }
                    },
                    style = HdTheme.typography.headlineMedium.copy(
                        fontFamily = HdSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                    ),
                    color = HdTheme.colors.ink,
                )
                Text(
                    "Three ways in.",
                    style = HdTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = HdTheme.colors.graphite,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                NewActionRow(
                    title = "Blank floor plan",
                    subtitle = "Start from an empty canvas",
                    iconRes = HdSfIcons.squareDashed,
                    onClick = {
                        showNewSheet = false
                        viewModel.createBlank(onOpenProject)
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "From a sketch",
                    subtitle = "Photograph a hand-drawn plan",
                    iconRes = HdSfIcons.cameraViewfinder,
                    pill = "AI",
                    onClick = {
                        showNewSheet = false
                        onOpenSketch()
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "From a template",
                    subtitle = "Modern flat, bedroom, loft…",
                    iconRes = HdSfIcons.squareGrid2x2,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "NEW DESIGN",
                            fontFamily = HdMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = HdTheme.colors.stone,
                        )
                        Text(
                            text = buildAnnotatedString {
                                append("From a ")
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                                    append("template")
                                }
                            },
                            style = HdTheme.typography.headlineMedium.copy(fontFamily = HdSerif),
                            color = HdTheme.colors.ink,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(HdTheme.colors.sand)
                            .clickable { showTemplateSheet = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        SfIcon(
                            HdSfIcons.xmark,
                            contentDescription = "Close",
                            tint = HdTheme.colors.stone,
                            size = 14.dp,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                TemplateCard(
                    title = "Modern Flat",
                    blurb = "Open plan · 2 rooms",
                    iconRes = HdSfIcons.building2Fill,
                    onClick = {
                        showTemplateSheet = false
                        viewModel.openShowcase(onOpenProject)
                    },
                )
                Spacer(Modifier.height(10.dp))
                TemplateCard(
                    title = "Bedroom",
                    blurb = "Sleeping suite · 1 room",
                    iconRes = HdSfIcons.houseFill,
                    onClick = {
                        showTemplateSheet = false
                        viewModel.openSampleSh3d(onOpenProject)
                    },
                )
                Spacer(Modifier.height(10.dp))
                TemplateCard(
                    title = "Loft Apartment",
                    blurb = "Double-height · 3 rooms",
                    iconRes = HdSfIcons.houseLodgeFill,
                    onClick = {
                        showTemplateSheet = false
                        viewModel.openShowcase(onOpenProject)
                    },
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Templates are fully editable once opened.",
                    style = HdTheme.typography.bodySmall,
                    color = HdTheme.colors.stone,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "Open .homedesign",
                    subtitle = "Import a plan from storage",
                    iconRes = HdSfIcons.squareAndArrowDown,
                    onClick = {
                        showTemplateSheet = false
                        pickHomedesign.launch("*/*")
                    },
                )
                Spacer(Modifier.height(8.dp))
                NewActionRow(
                    title = "Open .sh3d",
                    subtitle = "Import walls, rooms, furniture from archive",
                    iconRes = HdSfIcons.folder,
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
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SfIcon(
                                HdSfIcons.checkmark,
                                contentDescription = null,
                                tint = HdTheme.colors.terracotta,
                                size = 14.dp,
                            )
                            Text("Done")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            overlay = ProjectOverlay.ConfirmDelete(current.meta)
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SfIcon(
                                HdSfIcons.trash,
                                contentDescription = null,
                                tint = HdTheme.colors.destructive,
                                size = 14.dp,
                            )
                            Text("Delete", color = HdTheme.colors.destructive)
                        }
                    }
                },
            )
        }
        is ProjectOverlay.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = { overlay = null },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(HdTheme.colors.terracotta.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SfIcon(
                            HdSfIcons.trash,
                            contentDescription = null,
                            tint = HdTheme.colors.terracotta,
                            size = 20.dp,
                        )
                    }
                },
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
        SfIcon(
            HdSfIcons.squareDashed,
            contentDescription = null,
            tint = HdTheme.colors.stone,
            size = 38.dp,
        )
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
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color(0xFF1A1714).copy(alpha = 0.06f),
                spotColor = Color(0xFF1A1714).copy(alpha = 0.06f),
            )
            .clip(shape)
            .background(HdTheme.colors.ivory)
            .border(0.5.dp, HdTheme.colors.hairline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .width(116.dp)
                .fillMaxHeight()
                .background(Color(0xFFF3F7FB)),
        ) {
            PlanThumbImage(meta = meta, modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Continue · ${relativeTime(meta.updatedAt)}".uppercase(),
                style = HdTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    letterSpacing = 1.4.sp,
                ),
                color = HdTheme.colors.stone,
            )
            Text(
                meta.name.uppercase(),
                style = HdTheme.typography.headlineSmall.copy(
                    fontFamily = HdSerif,
                    fontSize = 19.sp,
                    letterSpacing = (-0.2).sp,
                ),
                color = HdTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "L${meta.levelCount}",
                    fontFamily = HdMono,
                    fontSize = 11.5.sp,
                    color = HdTheme.colors.ink,
                )
                Text("·", color = HdTheme.colors.stone)
                Text(
                    "${meta.roomCount} room${if (meta.roomCount == 1) "" else "s"}",
                    style = HdTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = HdTheme.colors.graphite,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Resume",
                    style = HdTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = HdTheme.colors.ink,
                )
                SfIcon(
                    HdSfIcons.arrowRight,
                    contentDescription = null,
                    tint = HdTheme.colors.ink,
                    size = 11.dp,
                )
            }
        }
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
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Color(0xFF1A1714).copy(alpha = 0.07f),
                spotColor = Color(0xFF1A1714).copy(alpha = 0.07f),
            )
            .clip(shape)
            .background(Color.White)
            .border(0.5.dp, Color(0xFF1A1714).copy(alpha = 0.08f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            PlanThumbImage(meta = meta, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                SfIcon(
                    HdSfIcons.ellipsis,
                    contentDescription = "More for ${meta.name}",
                    tint = HdTheme.colors.ink,
                    size = 16.dp,
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                meta.name.uppercase(),
                style = HdTheme.typography.titleSmall.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp,
                ),
                color = HdTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "L${meta.levelCount}",
                    style = HdTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.sp,
                    ),
                    color = HdTheme.colors.ink,
                )
                Text("·", color = HdTheme.colors.stone, fontSize = 11.sp)
                Text(
                    text = relativeTime(meta.updatedAt),
                    style = HdTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = HdTheme.colors.stone,
                )
            }
        }
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
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    pill: String? = null,
) {
    val tile = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HdTheme.colors.ivory)
            .border(0.5.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(tile)
                .background(HdTheme.colors.paper)
                .border(0.5.dp, HdTheme.colors.hairline, tile),
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(
                iconRes,
                contentDescription = null,
                tint = HdTheme.colors.ink,
                size = 18.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    style = HdTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = HdTheme.colors.ink,
                )
                if (pill != null) {
                    Text(
                        text = pill,
                        style = HdTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = HdTheme.colors.paper,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HdTheme.colors.terracotta)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(subtitle, style = HdTheme.typography.bodySmall, color = HdTheme.colors.stone)
        }
        SfIcon(
            HdSfIcons.chevronRight,
            contentDescription = null,
            tint = HdTheme.colors.stone,
            size = 13.dp,
        )
    }
}

@Composable
private fun TemplateCard(
    title: String,
    blurb: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HdTheme.colors.ivory)
            .border(0.5.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            HdTheme.colors.sand,
                            HdTheme.colors.highlight,
                        ),
                    ),
                )
                .border(0.5.dp, HdTheme.colors.hairline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(iconRes, contentDescription = null, tint = HdTheme.colors.terracotta, size = 22.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HdTheme.typography.headlineSmall.copy(fontFamily = HdSerif, fontSize = 18.sp), color = HdTheme.colors.ink)
            Text(blurb, style = HdTheme.typography.bodySmall, color = HdTheme.colors.stone)
        }
        SfIcon(HdSfIcons.chevronRight, contentDescription = null, tint = HdTheme.colors.stone, size = 13.dp)
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
        fallbackFill = Color.White.copy(alpha = 0.55f),
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Designs",
                    style = HdTheme.typography.displayMedium.copy(
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = HdTheme.colors.ink,
                )
                Text(
                    text = if (designCount == 1) "1 design" else "$designCount designs",
                    style = HdTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.3.sp,
                    ),
                    color = HdTheme.colors.stone,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(HdTheme.colors.sand, Color(0xFFDCCDA9)),
                        ),
                    )
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = HdTheme.typography.titleSmall.copy(
                        fontFamily = HdSerif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
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
                    .height(42.dp)
                    .dashboardPill()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SfIcon(
                    HdSfIcons.magnifyingglass,
                    contentDescription = null,
                    tint = HdTheme.colors.stone,
                    size = 13.dp,
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
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onSearch("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        SfIcon(
                            HdSfIcons.xmarkCircleFill,
                            contentDescription = "Clear search",
                            tint = HdTheme.colors.stone,
                            size = 14.dp,
                        )
                    }
                }
            }

            Box {
                Row(
                    modifier = Modifier
                        .height(42.dp)
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
                    SfIcon(
                        HdSfIcons.chevronDown,
                        contentDescription = null,
                        tint = HdTheme.colors.ink,
                        size = 9.dp,
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
                iconRes = HdSfIcons.houseFill,
                onClick = onOpenVilla,
            )
            ShowcaseCard(
                name = "Villa Aurelia",
                blurb = "Italian villa · patio fountain",
                iconRes = HdSfIcons.houseLodgeFill,
                onClick = onOpenVilla,
            )
            ShowcaseCard(
                name = "Sample plan",
                blurb = "Bundled test plan · walls & furniture",
                iconRes = HdSfIcons.building2Fill,
                onClick = onOpenSample,
            )
            ShowcaseCard(
                name = "English Villa",
                blurb = "Classic English home",
                iconRes = HdSfIcons.houseLodgeFill,
                onClick = onOpenSample,
            )
            ShowcaseCard(
                name = "Alpine Hotel",
                blurb = "Chalet hotel · many rooms",
                iconRes = HdSfIcons.buildingColumnsFill,
                onClick = onOpenSample,
            )
        }
    }
}

@Composable
private fun ShowcaseCard(
    name: String,
    blurb: String,
    @DrawableRes iconRes: Int,
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
            SfIcon(
                iconRes,
                contentDescription = null,
                tint = HdTheme.colors.terracotta,
                size = 34.dp,
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
        modifier = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(HdTheme.colors.ink)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(
                HdSfIcons.plus,
                contentDescription = "New design",
                tint = Color.White,
                size = 22.dp,
            )
        }
    }
}
