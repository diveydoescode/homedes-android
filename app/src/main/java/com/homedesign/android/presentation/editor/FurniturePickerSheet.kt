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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.homedesign.android.core.ui.HdSfIcons
import com.homedesign.android.core.ui.SfIcon
import com.homedesign.android.core.ui.theme.HdMono
import com.homedesign.android.core.ui.theme.HdSans
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.catalog.CATALOG_ENTRIES
import com.homedesign.android.domain.catalog.CatalogEntry
import com.homedesign.android.domain.catalog.PickerMode
import com.homedesign.android.domain.catalog.PickerSort
import com.homedesign.android.domain.catalog.RECENT_CHIP
import com.homedesign.android.domain.catalog.categoriesFor
import com.homedesign.android.domain.catalog.entriesInCategory
import com.homedesign.android.domain.catalog.pickerEntries
import com.homedesign.android.domain.catalog.resolveRecent
import com.homedesign.android.domain.catalog.sortEntries
import com.homedesign.android.domain.catalog.visibleRows
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem

@Composable
fun FurniturePickerContent(
    unitSystem: UnitSystem,
    recentIds: List<String>,
    onPick: (CatalogEntry) -> Unit,
    mode: PickerMode = PickerMode.Furniture,
    title: String = "Add Furniture",
    onCancel: (() -> Unit)? = null,
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
    var sortOpen by remember { mutableStateOf(false) }

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            Text(
                "Cancel",
                fontFamily = HdSans,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = HdTheme.colors.architectInk,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.06f))
                    .clickable { onCancel?.invoke() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Text(
                if (title == "Add Furniture") "Add Furniture" else title,
                fontFamily = HdSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = HdTheme.colors.architectInk,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.06f))
                        .clickable { sortOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    SfIcon(
                        HdSfIcons.arrowUpAndDown,
                        contentDescription = "Sort",
                        tint = HdTheme.colors.architectInk,
                        size = 16.dp,
                    )
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Name A–Z") },
                        onClick = { sort = PickerSort.Name; sortOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Smallest first") },
                        onClick = { sort = PickerSort.SizeSmall; sortOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Largest first") },
                        onClick = { sort = PickerSort.SizeLarge; sortOpen = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SfIcon(HdSfIcons.magnifyingglass, contentDescription = null, tint = HdTheme.colors.architectGray, size = 14.dp)
                Box(modifier = Modifier.weight(1f)) {
                    if (search.isEmpty()) {
                        Text(
                            "Search ${pool.size} items",
                            fontFamily = HdSans,
                            fontSize = 14.sp,
                            color = HdTheme.colors.architectGray,
                        )
                    }
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = HdSans,
                            fontSize = 14.sp,
                            color = HdTheme.colors.architectInk,
                        ),
                        cursorBrush = SolidColor(HdTheme.colors.selection),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PickerChip(
                label = "All",
                selected = category == null && search.isBlank(),
                onClick = { category = null },
            )
            if (mode == PickerMode.Furniture) {
                PickerChip(
                    label = "Recent",
                    selected = category == RECENT_CHIP && search.isBlank(),
                    onClick = { category = RECENT_CHIP },
                )
            }
            categories.forEach { name ->
                PickerChip(
                    label = name,
                    selected = category == name && search.isBlank(),
                    onClick = { category = name },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val recents = remember(recentIds, pool) { resolveRecent(recentIds, pool) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (search.isNotBlank()) {
                items(rows, key = { it.id }) { entry ->
                    PickerRow(entry = entry, imageLoader = imageLoader, onClick = { onPick(entry) })
                }
            } else if (category == RECENT_CHIP) {
                item { SectionLabel("Recently used") }
                items(recents, key = { "r-${it.id}" }) { entry ->
                    PickerRow(entry = entry, imageLoader = imageLoader, onClick = { onPick(entry) })
                }
            } else if (category != null) {
                item { SectionLabel(category!!) }
                items(
                    sortEntries(entriesInCategory(pool, category), sort),
                    key = { it.id },
                ) { entry ->
                    PickerRow(entry = entry, imageLoader = imageLoader, onClick = { onPick(entry) })
                }
            } else {
                if (recents.isNotEmpty()) {
                    item { SectionLabel("Recently used") }
                    items(recents.take(4), key = { "ru-${it.id}" }) { entry ->
                        PickerRow(entry = entry, imageLoader = imageLoader, onClick = { onPick(entry) })
                    }
                }
                categories.forEach { cat ->
                    val catRows = sortEntries(entriesInCategory(pool, cat), sort)
                    if (catRows.isNotEmpty()) {
                        item(key = "h-$cat") { SectionLabel(cat) }
                        items(catRows, key = { "$cat-${it.id}" }) { entry ->
                            PickerRow(entry = entry, imageLoader = imageLoader, onClick = { onPick(entry) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = HdSans,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 14.sp,
        color = if (selected) HdTheme.colors.selection else HdTheme.colors.architectInk,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) HdTheme.colors.selection.copy(alpha = 0.22f)
                else Color.Black.copy(alpha = 0.06f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = HdSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = HdTheme.colors.architectGray,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun PickerRow(
    entry: CatalogEntry,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            val icon = entry.icon
            val svgBytes = remember(icon) {
                if (icon.isNullOrBlank()) null
                else runCatching { context.assets.open(icon).use { it.readBytes() } }.getOrNull()
            }
            if (svgBytes != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(svgBytes)
                        .decoderFactory(coil.decode.SvgDecoder.Factory())
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                fontFamily = HdSans,
                fontSize = 15.sp,
                color = HdTheme.colors.architectInk,
            )
            Text(
                "${entry.width.toInt()}×${entry.depth.toInt()}×${entry.height.toInt()} cm",
                fontFamily = HdSans,
                fontSize = 12.sp,
                color = HdTheme.colors.architectGray,
            )
        }
    }
}
