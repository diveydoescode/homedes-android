package com.homedesign.android.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.project.ProjectMeta
import com.homedesign.android.domain.project.ProjectRepository
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder { Recent, Name, Oldest }

data class DashboardUiState(
    val projects: List<ProjectMeta> = emptyList(),
    val firstName: String = "",
    val search: String = "",
    val sort: SortOrder = SortOrder.Recent,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val search = kotlinx.coroutines.flow.MutableStateFlow("")
    private val sort = kotlinx.coroutines.flow.MutableStateFlow(SortOrder.Recent)

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
        DashboardUiState(
            projects = sorted,
            firstName = settings.firstName,
            search = q,
            sort = s,
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

    fun delete(id: String) {
        viewModelScope.launch { projectRepository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenProject: (String) -> Unit,
    onOpenSketch: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val showHero = state.search.isBlank() && state.sort == SortOrder.Recent && state.projects.isNotEmpty()
    val hero = if (showHero) state.projects.first() else null
    val grid = if (hero != null) state.projects.drop(1) else state.projects
    val initial = state.firstName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "·"

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
                        HeroCard(meta = meta, onClick = { onOpenProject(meta.id) })
                    }
                }
                items(grid, key = { it.id }) { meta ->
                    ProjectCard(meta = meta, onClick = { onOpenProject(meta.id) })
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
    }

    if (showNewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewSheet = false },
            sheetState = sheetState,
            containerColor = HdTheme.colors.ivory,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
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

@Composable
private fun HeroCard(meta: ProjectMeta, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HdTheme.colors.highlight)
            .clickable(onClick = onClick)
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
            Text(
                text = "Plan",
                style = HdTheme.typography.labelSmall,
                color = HdTheme.colors.stone,
                fontStyle = FontStyle.Italic,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(meta.name, style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
        Text(
            text = formatUpdated(meta.updatedAt),
            style = HdTheme.typography.bodySmall,
            color = HdTheme.colors.stone,
        )
    }
}

@Composable
private fun ProjectCard(meta: ProjectMeta, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(14.dp))
            .background(HdTheme.colors.ivory)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(10.dp))
                .background(HdTheme.colors.highlight),
        )
        Spacer(Modifier.height(10.dp))
        Text(meta.name, style = HdTheme.typography.titleSmall, color = HdTheme.colors.ink, maxLines = 1)
        Text(
            text = formatUpdated(meta.updatedAt),
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.stone,
        )
    }
}

@Composable
private fun NewActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: Boolean = false,
) {
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
                imageVector = if (icon) Icons.Outlined.CameraAlt else Icons.Outlined.Add,
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

private fun formatUpdated(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
