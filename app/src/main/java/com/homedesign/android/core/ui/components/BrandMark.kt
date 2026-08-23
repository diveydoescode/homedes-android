package com.homedesign.android.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.theme.HdTheme

/** Corner-plan brand mark matching the splash SVG. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val ink = HdTheme.colors.ink
    val terracotta = HdTheme.colors.terracotta

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val scale = s / 64f
        val strokeInk = Stroke(
            width = 5.2f * scale,
            join = StrokeJoin.Miter,
            cap = StrokeCap.Butt,
        )
        val strokeAccent = Stroke(width = 3f * scale, cap = StrokeCap.Butt)
        val strokeArc = Stroke(width = 1.2f * scale, cap = StrokeCap.Butt)

        // Outer plan outline: M6 6H58V26 / M58 44V58H6V6
        drawLine(ink, Offset(6f * scale, 6f * scale), Offset(58f * scale, 6f * scale), strokeInk.width)
        drawLine(ink, Offset(58f * scale, 6f * scale), Offset(58f * scale, 26f * scale), strokeInk.width)
        drawLine(ink, Offset(58f * scale, 44f * scale), Offset(58f * scale, 58f * scale), strokeInk.width)
        drawLine(ink, Offset(58f * scale, 58f * scale), Offset(6f * scale, 58f * scale), strokeInk.width)
        drawLine(ink, Offset(6f * scale, 58f * scale), Offset(6f * scale, 6f * scale), strokeInk.width)

        // Accent: M58 26H42
        drawLine(
            terracotta,
            Offset(58f * scale, 26f * scale),
            Offset(42f * scale, 26f * scale),
            strokeAccent.width,
        )

        // Quarter arc: M58 42A16 16 0 0 0 42 26
        drawArc(
            color = terracotta,
            startAngle = 0f,
            sweepAngle = -90f,
            useCenter = false,
            topLeft = Offset(42f * scale, 26f * scale),
            size = androidx.compose.ui.geometry.Size(32f * scale, 32f * scale),
            style = strokeArc,
        )
    }
}
