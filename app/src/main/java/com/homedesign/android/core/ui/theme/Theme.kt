package com.homedesign.android.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class HdExtendedColors(
    val terracotta: Color,
    val selection: Color,
    val selectionDeep: Color,
    val paper: Color,
    val ivory: Color,
    val highlight: Color,
    val sand: Color,
    val stone: Color,
    val graphite: Color,
    val ink: Color,
    val architectInk: Color,
    val architectGray: Color,
    val hairline: Color,
    val scrim: Color,
    val tintWarm: Color,
    val tintCool: Color,
    val success: Color,
    val caution: Color,
    val destructive: Color,
)

val LocalHdColors = staticCompositionLocalOf {
    lightExtendedColors()
}

private fun lightExtendedColors() = HdExtendedColors(
    terracotta = Terracotta,
    selection = Selection,
    selectionDeep = SelectionDeep,
    paper = EditorialPaperLight,
    ivory = EditorialIvoryLight,
    highlight = EditorialHighlightLight,
    sand = EditorialSandLight,
    stone = EditorialStoneLight,
    graphite = EditorialGraphiteLight,
    ink = EditorialInkLight,
    architectInk = ArchitectInkLight,
    architectGray = ArchitectGrayLight,
    hairline = HairlineLight,
    scrim = ScrimLight,
    tintWarm = TintWarmLight,
    tintCool = TintCoolLight,
    success = Success,
    caution = Caution,
    destructive = Destructive,
)

private fun darkExtendedColors() = HdExtendedColors(
    terracotta = TerracottaDark,
    selection = Selection,
    selectionDeep = SelectionDeep,
    paper = EditorialPaperDark,
    ivory = EditorialIvoryDark,
    highlight = EditorialHighlightDark,
    sand = EditorialSandDark,
    stone = EditorialStoneDark,
    graphite = EditorialGraphiteDark,
    ink = EditorialInkDark,
    architectInk = ArchitectInkDark,
    architectGray = ArchitectGrayDark,
    hairline = HairlineDark,
    scrim = ScrimDark,
    tintWarm = TintWarmDark,
    tintCool = TintCoolDark,
    success = Success,
    caution = Caution,
    destructive = DestructiveDark,
)

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = EditorialHighlightLight,
    onPrimaryContainer = EditorialInkLight,
    secondary = Selection,
    onSecondary = Color.White,
    secondaryContainer = TintCoolLight,
    onSecondaryContainer = SelectionDeep,
    tertiary = EditorialSandLight,
    onTertiary = EditorialInkLight,
    background = EditorialPaperLight,
    onBackground = EditorialInkLight,
    surface = EditorialIvoryLight,
    onSurface = EditorialInkLight,
    surfaceVariant = EditorialHighlightLight,
    onSurfaceVariant = EditorialGraphiteLight,
    outline = EditorialStoneLight,
    outlineVariant = HairlineLight,
    error = Destructive,
    onError = Color.White,
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = EditorialPaperDark,
    primaryContainer = EditorialHighlightDark,
    onPrimaryContainer = EditorialInkDark,
    secondary = Selection,
    onSecondary = Color.White,
    secondaryContainer = TintCoolDark,
    onSecondaryContainer = EditorialInkDark,
    tertiary = EditorialSandDark,
    onTertiary = EditorialInkDark,
    background = EditorialPaperDark,
    onBackground = EditorialInkDark,
    surface = EditorialIvoryDark,
    onSurface = EditorialInkDark,
    surfaceVariant = EditorialHighlightDark,
    onSurfaceVariant = EditorialGraphiteDark,
    outline = EditorialStoneDark,
    outlineVariant = HairlineDark,
    error = DestructiveDark,
    onError = EditorialPaperDark,
)

object HdTheme {
    val colors: HdExtendedColors
        @Composable
        get() = LocalHdColors.current

    val typography
        @Composable
        get() = MaterialTheme.typography
}

@Composable
fun HomeDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) darkExtendedColors() else lightExtendedColors()
    val colorScheme = if (darkTheme) darkScheme() else lightScheme()

    CompositionLocalProvider(LocalHdColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HdTypography,
            content = content,
        )
    }
}
