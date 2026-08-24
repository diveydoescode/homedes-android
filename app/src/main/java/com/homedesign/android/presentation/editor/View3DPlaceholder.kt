package com.homedesign.android.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme

/** Deprecated shell — View3D now uses [Plan3DScreen] / Filament. Kept for reference. */
@Composable
fun View3DPlaceholder(
    onBackToPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(HdTheme.colors.ivory)
                .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(HdTheme.colors.terracotta.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ViewInAr,
                    contentDescription = null,
                    tint = HdTheme.colors.terracotta,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = "3D view coming soon",
                style = HdTheme.typography.headlineSmall.copy(fontFamily = HdSerif),
                color = HdTheme.colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Filament rendering and AR are Tier 3 work — not available in this build.",
                style = HdTheme.typography.bodyMedium,
                color = HdTheme.colors.stone,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HdTheme.colors.terracotta)
                    .clickable(onClick = onBackToPlan)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Back to 2D plan",
                    style = HdTheme.typography.labelLarge,
                    color = HdTheme.colors.paper,
                )
            }
        }
    }
}
