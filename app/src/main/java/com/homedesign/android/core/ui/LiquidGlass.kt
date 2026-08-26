package com.homedesign.android.core.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.theme.HdTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Capture composable content into a layer backdrop (required for glass chrome). */
fun Modifier.hdLayerBackdrop(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

/** True when AGSL RuntimeShader path is available (Backdrop lens/blur). */
val liquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Composable
fun rememberHdLayerBackdrop(paper: Color = HdTheme.colors.paper): LayerBackdrop {
    return rememberLayerBackdrop {
        drawRect(paper)
        drawContent()
    }
}

/**
 * Liquid Glass chrome surface (iOS Layer 2 / HDGlassBackground).
 * Kyant Backdrop on API 33+; frosted ivory fallback below.
 * Soft shadow + hairline rule mirror iOS elevation + shine.
 */
fun Modifier.hdGlassChrome(
    backdrop: Backdrop?,
    shape: Shape = RoundedCornerShape(14.dp),
    surfaceAlpha: Float = 0.55f,
    blurRadius: Dp = 20.dp,
    lensRadius: Dp = 16.dp,
    lensHeight: Dp = 32.dp,
    borderColor: Color = Color.White.copy(alpha = 0.75f),
    fallbackFill: Color = Color.White.copy(alpha = 0.55f),
    elevation: Dp = 8.dp,
    dark: Boolean = false,
): Modifier {
    val fill = if (dark) Color(0xFF1A1714).copy(alpha = 0.55f) else fallbackFill
    val edge = if (dark) Color.White.copy(alpha = 0.16f) else borderColor
    val base = this.shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color(0xFF1A1714).copy(alpha = 0.10f),
        spotColor = Color(0xFF1A1714).copy(alpha = 0.10f),
    )
    return if (liquidGlassSupported && backdrop != null) {
        base
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(lensRadius.toPx(), lensHeight.toPx())
                },
                onDrawSurface = {
                    drawRect(
                        if (dark) Color(0xFF1A1714).copy(alpha = 0.55f)
                        else Color.White.copy(alpha = surfaceAlpha),
                    )
                },
            )
            .border(0.5.dp, edge, shape)
    } else {
        base
            .clip(shape)
            .background(fill, shape)
            .border(0.5.dp, edge, shape)
    }
}

/** Capsule dock / FAB glass (999 radius) — GlassV2 recipe. */
fun Modifier.hdGlassCapsule(
    backdrop: Backdrop?,
    surfaceAlpha: Float = 0.55f,
    fallbackFill: Color = Color.White.copy(alpha = 0.55f),
    dark: Boolean = false,
): Modifier = hdGlassChrome(
    backdrop = backdrop,
    shape = RoundedCornerShape(999.dp),
    surfaceAlpha = surfaceAlpha,
    blurRadius = 24.dp,
    lensRadius = 12.dp,
    lensHeight = 24.dp,
    fallbackFill = fallbackFill,
    elevation = 8.dp,
    dark = dark,
)
