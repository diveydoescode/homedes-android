package com.homedesign.android.core.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Liquid Glass chrome surface (iOS Layer 2). Uses Kyant Backdrop on API 33+;
 * falls back to frosted ivory Material fill on older devices.
 */
fun Modifier.hdGlassChrome(
    backdrop: Backdrop?,
    shape: Shape = RoundedCornerShape(14.dp),
    surfaceAlpha: Float = 0.48f,
    blurRadius: Dp = 4.dp,
    lensRadius: Dp = 16.dp,
    lensHeight: Dp = 32.dp,
    borderColor: Color = Color.Black.copy(alpha = 0.08f),
    fallbackFill: Color = Color.White.copy(alpha = 0.88f),
): Modifier {
    return if (liquidGlassSupported && backdrop != null) {
        this
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius.toPx())
                    lens(lensRadius.toPx(), lensHeight.toPx())
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = surfaceAlpha))
                },
            )
            .border(0.5.dp, borderColor, shape)
    } else {
        this
            .clip(shape)
            .background(fallbackFill, shape)
            .border(0.5.dp, borderColor, shape)
    }
}

/** Capsule dock / FAB glass (999 radius). */
fun Modifier.hdGlassCapsule(
    backdrop: Backdrop?,
    surfaceAlpha: Float = 0.50f,
    fallbackFill: Color = Color.White.copy(alpha = 0.92f),
): Modifier = hdGlassChrome(
    backdrop = backdrop,
    shape = RoundedCornerShape(999.dp),
    surfaceAlpha = surfaceAlpha,
    blurRadius = 4.dp,
    lensRadius = 12.dp,
    lensHeight = 24.dp,
    fallbackFill = fallbackFill,
)
