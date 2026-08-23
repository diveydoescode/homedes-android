package com.homedesign.android.presentation.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.components.JourneyEyebrow
import com.homedesign.android.core.ui.theme.HdSerif
import com.homedesign.android.core.ui.theme.HdTheme
import kotlinx.coroutines.launch

data class OnboardingBeat(
    val eyebrow: String,
    val line1: String,
    val line2: String,
    val sub: String,
)

private val Beats = listOf(
    OnboardingBeat(
        eyebrow = "01 / Plan",
        line1 = "Plan the rooms",
        line2 = "you live in.",
        sub = "A 2D floor plan, measured in millimetres. Walls snap, dimensions update live.",
    ),
    OnboardingBeat(
        eyebrow = "02 / Sketch",
        line1 = "Drew it on paper?",
        line2 = "Let it type.",
        sub = "Photograph a hand-drawn plan. We turn it into editable walls, rooms, and openings.",
    ),
    OnboardingBeat(
        eyebrow = "03 / Export",
        line1 = "And share it",
        line2 = "with the room.",
        sub = "Download DXF and PDF contractor sheets when you're ready to build.",
    ),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onSkip: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { Beats.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == Beats.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Skip",
                style = HdTheme.typography.labelLarge,
                color = HdTheme.colors.stone,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onSkip)
                    .padding(8.dp),
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val beat = Beats[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                BeatVisual(index = page, modifier = Modifier.fillMaxWidth().height(220.dp))
                Spacer(Modifier.height(28.dp))
                JourneyEyebrow(beat.eyebrow)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = buildAnnotatedString {
                        append(beat.line1)
                        append("\n")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontFamily = HdSerif)) {
                            append(beat.line2)
                        }
                    },
                    style = HdTheme.typography.displaySmall,
                    color = HdTheme.colors.ink,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = beat.sub,
                    style = HdTheme.typography.bodyMedium,
                    color = HdTheme.colors.stone,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Beats.indices.forEach { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) HdTheme.colors.terracotta else HdTheme.colors.hairline,
                            )
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(i) }
                            },
                    )
                }
            }
            Text(
                text = if (last) "Continue →" else "Continue →",
                style = HdTheme.typography.labelLarge,
                color = HdTheme.colors.ink,
                modifier = Modifier
                    .clickable {
                        if (last) {
                            onFinished()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun BeatVisual(index: Int, modifier: Modifier = Modifier) {
    val ink = HdTheme.colors.ink
    val stone = HdTheme.colors.stone
    val accent = HdTheme.colors.terracotta
    val paper = HdTheme.colors.paper
    val highlight = HdTheme.colors.highlight
    val sand = HdTheme.colors.sand

    Canvas(
        modifier = modifier
            .background(if (index == 2) sand else highlight)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        val w = size.width
        val h = size.height
        when (index) {
            0 -> {
                drawRect(highlight)
                drawRect(
                    color = paper,
                    topLeft = Offset(w * 0.08f, h * 0.1f),
                    size = Size(w * 0.84f, h * 0.8f),
                )
                val stroke = Stroke(width = 5f, cap = StrokeCap.Square)
                drawLine(ink, Offset(w * 0.12f, h * 0.14f), Offset(w * 0.45f, h * 0.14f), stroke.width)
                drawLine(ink, Offset(w * 0.58f, h * 0.14f), Offset(w * 0.88f, h * 0.14f), stroke.width)
                drawLine(ink, Offset(w * 0.88f, h * 0.14f), Offset(w * 0.88f, h * 0.86f), stroke.width)
                drawLine(ink, Offset(w * 0.88f, h * 0.86f), Offset(w * 0.12f, h * 0.86f), stroke.width)
                drawLine(ink, Offset(w * 0.12f, h * 0.86f), Offset(w * 0.12f, h * 0.14f), stroke.width)
                drawLine(ink, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.86f), stroke.width)
                drawLine(accent, Offset(w * 0.78f, h * 0.86f), Offset(w * 0.72f, h * 0.78f), 2.5f)
            }
            1 -> {
                drawRect(highlight)
                val stroke = Stroke(width = 3f)
                drawRoundRect(
                    color = stone.copy(alpha = 0.35f),
                    topLeft = Offset(w * 0.16f, h * 0.18f),
                    size = Size(w * 0.68f, h * 0.64f),
                    style = stroke,
                )
                drawRect(
                    color = accent.copy(alpha = 0.15f),
                    topLeft = Offset(w * 0.2f, h * 0.22f),
                    size = Size(w * 0.6f, h * 0.56f),
                )
                drawLine(accent, Offset(w * 0.2f, h * 0.22f), Offset(w * 0.8f, h * 0.22f), 3.5f)
                drawLine(accent, Offset(w * 0.8f, h * 0.22f), Offset(w * 0.8f, h * 0.78f), 3.5f)
                drawLine(accent, Offset(w * 0.8f, h * 0.78f), Offset(w * 0.2f, h * 0.78f), 3.5f)
                drawLine(accent, Offset(w * 0.2f, h * 0.78f), Offset(w * 0.2f, h * 0.22f), 3.5f)
                drawLine(accent, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), 3.5f)
            }
            else -> {
                drawRect(ColorWarmDark)
                drawRect(
                    color = paper.copy(alpha = 0.92f),
                    topLeft = Offset(w * 0.18f, h * 0.16f),
                    size = Size(w * 0.64f, h * 0.68f),
                )
                drawLine(ink, Offset(w * 0.26f, h * 0.32f), Offset(w * 0.74f, h * 0.32f), 2.5f)
                drawLine(ink, Offset(w * 0.26f, h * 0.42f), Offset(w * 0.62f, h * 0.42f), 2.5f)
                drawLine(ink, Offset(w * 0.26f, h * 0.52f), Offset(w * 0.7f, h * 0.52f), 2.5f)
                drawRect(
                    color = accent,
                    topLeft = Offset(w * 0.58f, h * 0.66f),
                    size = Size(w * 0.16f, h * 0.08f),
                )
            }
        }
    }
}

private val ColorWarmDark = androidx.compose.ui.graphics.Color(0xFF3A2A24)
