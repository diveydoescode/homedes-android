package com.homedesign.android.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.catalog.CATALOG_ENTRIES
import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.catalog.PickerMode
import com.homedesign.android.domain.catalog.PickerSort
import com.homedesign.android.domain.catalog.RECENT_CHIP
import com.homedesign.android.domain.catalog.categoriesFor
import com.homedesign.android.domain.catalog.pickerEntries
import com.homedesign.android.domain.catalog.visibleRows
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem

@Composable
fun FurniturePickerContent(
    unitSystem: UnitSystem,
    recentIds: List<String>,
    onPick: (CatalogEntry) -> Unit,
    mode: PickerMode = PickerMode.Furniture,
    title: String = "Catalog",
) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PickerSort.Name) }

    val catalogSize = CATALOG_ENTRIES.size
    val pool = remember(mode, catalogSize) { pickerEntries(mode) }
    val categories = remember(mode, pool) { categoriesFor(mode, pool) }
    val rows = remember(mode, search, category, sort, recentIds, catalogSize) {
        visibleRows(mode, search, category, sort, recentIds)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SortChip("A–Z", sort == PickerSort.Name) { sort = PickerSort.Name }
                SortChip("Small", sort == PickerSort.SizeSmall) { sort = PickerSort.SizeSmall }
                SortChip("Large", sort == PickerSort.SizeLarge) { sort = PickerSort.SizeLarge }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search name, category, id…") },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = category == null && search.isBlank(),
                onClick = { category = null },
                label = { Text("All") },
            )
            if (mode == PickerMode.Furniture) {
                FilterChip(
                    selected = category == RECENT_CHIP && search.isBlank(),
                    onClick = { category = RECENT_CHIP },
                    label = { Text("Recent") },
                )
            }
            categories.forEach { name ->
                FilterChip(
                    selected = category == name && search.isBlank(),
                    onClick = { category = name },
                    label = { Text(name) },
                )
            }
        }
        Text(
            text = "${rows.size} item${if (rows.size == 1) "" else "s"}",
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.stone,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.id }) { entry ->
                PickerRow(
                    entry = entry,
                    unitSystem = unitSystem,
                    imageLoader = imageLoader,
                    onClick = { onPick(entry) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = HdTheme.typography.labelSmall,
        color = if (selected) HdTheme.colors.paper else HdTheme.colors.architectInk,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) HdTheme.colors.architectInk else HdTheme.colors.highlight)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun PickerRow(
    entry: CatalogEntry,
    unitSystem: UnitSystem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HdTheme.colors.paper)
                .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = entry.icon
            if (icon != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/$icon")
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = HdTheme.typography.titleSmall, color = HdTheme.colors.ink)
            Text(entry.category, style = HdTheme.typography.labelSmall, color = HdTheme.colors.stone)
        }
        Text(
            "${UnitFormat.length(entry.width, unitSystem)} × ${UnitFormat.length(entry.depth, unitSystem)}",
            style = HdTheme.typography.labelSmall,
            color = HdTheme.colors.architectGray,
        )
    }
}
