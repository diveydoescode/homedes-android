package com.homedesign.android.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.textures.PAINT_PRESET_PALETTE
import com.homedesign.android.domain.textures.TexturePreset
import com.homedesign.android.domain.textures.paintSwatchSelected
import com.homedesign.android.domain.textures.presetAssetPath
import com.homedesign.android.domain.textures.presetHandle

@Composable
fun PaintRow(
    selectedHex: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (swatch in PAINT_PRESET_PALETTE) {
            val selected = paintSwatchSelected(selectedHex, swatch.hex)
            val rgb = swatch.hex.takeLast(6)
            val color = runCatching {
                Color(android.graphics.Color.parseColor("#$rgb"))
            }.getOrDefault(Color.Gray)
            Box(
                modifier = Modifier
                    .size(if (selected) 28.dp else 24.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) HdTheme.colors.selection else HdTheme.colors.hairline,
                        shape = CircleShape,
                    )
                    .clickable { onPick(swatch.hex) },
            )
        }
    }
}

@Composable
fun PresetGrid(
    presets: List<TexturePreset>,
    selectedHandle: String?,
    onPick: (TexturePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (preset in presets) {
            val handle = presetHandle(preset.slug)
            val selected = selectedHandle == handle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) HdTheme.colors.selection else HdTheme.colors.hairline,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onPick(preset) }
                    .padding(4.dp),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/${presetAssetPath(preset)}")
                        .build(),
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Text(
                    preset.name,
                    style = HdTheme.typography.labelSmall,
                    color = HdTheme.colors.stone,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AppliedTextureRow(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HdTheme.typography.bodySmall, color = HdTheme.colors.ink)
        TextButton(onClick = onRemove) { Text("Clear") }
    }
}
