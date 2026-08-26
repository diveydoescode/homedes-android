package com.homedesign.android.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.HdSfIcons
import com.homedesign.android.core.ui.SfIcon
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme

@Composable
fun JourneyStage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun JourneyEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = HdTheme.typography.labelSmall,
        color = HdTheme.colors.stone,
        modifier = modifier,
    )
}

@Composable
fun SerifHeadline(
    lead: String,
    italic: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildAnnotatedString {
            append(lead)
            if (italic.isNotEmpty()) {
                append("\n")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                    append(italic)
                }
            }
        },
        style = HdTheme.typography.displayMedium,
        color = HdTheme.colors.ink,
        modifier = modifier,
    )
}

@Composable
fun InkPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    val cream = Color(0xFFFCFAF6)
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.linearGradient(listOf(Color(0xFF2A2420), Color(0xFF1A1714)))
                } else {
                    Brush.linearGradient(
                        listOf(
                            HdTheme.colors.stone.copy(alpha = 0.35f),
                            HdTheme.colors.stone.copy(alpha = 0.35f),
                        ),
                    )
                },
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = HdTheme.typography.labelLarge,
            color = cream,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(HdTheme.colors.terracotta),
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(
                HdSfIcons.arrowRight,
                contentDescription = null,
                tint = cream,
                size = 11.dp,
            )
        }
    }
}

@Composable
fun InkPlateButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) HdTheme.colors.ink else HdTheme.colors.stone.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = HdTheme.typography.labelLarge,
            color = HdTheme.colors.paper,
        )
        if (showArrow) {
            SfIcon(
                HdSfIcons.arrowRight,
                contentDescription = null,
                tint = HdTheme.colors.paper,
                size = 14.dp,
            )
        }
    }
}

@Composable
fun JourneyBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        SfIcon(
            HdSfIcons.chevronBackward,
            contentDescription = "Back",
            tint = HdTheme.colors.ink,
            size = 17.dp,
        )
    }
}

@Composable
fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = HdTheme.colors.stone.copy(alpha = 0.55f))
            },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HdTheme.colors.terracotta,
                unfocusedBorderColor = HdTheme.colors.hairline,
                focusedTextColor = HdTheme.colors.ink,
                unfocusedTextColor = HdTheme.colors.ink,
                cursorColor = HdTheme.colors.terracotta,
                focusedContainerColor = HdTheme.colors.paper,
                unfocusedContainerColor = HdTheme.colors.paper,
            ),
            shape = RoundedCornerShape(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            JourneyEyebrow(label)
            Text(
                text = tag,
                style = HdTheme.typography.labelSmall,
                color = if (tag.equals("Required", true)) HdTheme.colors.terracotta else HdTheme.colors.stone,
            )
        }
    }
}

@Composable
fun UnitChoiceCard(
    title: String,
    sample: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) HdTheme.colors.terracotta else HdTheme.colors.hairline,
                shape = shape,
            )
            .background(if (selected) HdTheme.colors.tintWarm else HdTheme.colors.ivory)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = HdTheme.typography.titleLarge, color = HdTheme.colors.ink)
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(HdTheme.colors.terracotta),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = HdTheme.colors.paper, style = HdTheme.typography.labelMedium)
                }
            } else {
                Spacer(Modifier.width(22.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(sample, style = HdTheme.typography.labelSmall, color = HdTheme.colors.graphite)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = HdTheme.typography.bodySmall, color = HdTheme.colors.stone)
    }
}
