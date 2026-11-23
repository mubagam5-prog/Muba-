package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MubaDarkColorScheme = darkColorScheme(
    primary = MubaPurple,
    onPrimary = MubaPureWhite,
    primaryContainer = MubaPurpleContainerDark,
    onPrimaryContainer = MubaPurpleGlow,
    secondary = MubaPurpleGlow,
    onSecondary = MubaInkBlack,
    secondaryContainer = MubaSurfaceElevatedDark,
    onSecondaryContainer = MubaPureWhite,
    tertiary = MubaPureWhite,
    onTertiary = MubaInkBlack,
    background = MubaObsidian,
    onBackground = MubaPureWhite,
    surface = MubaSurfaceDark,
    onSurface = MubaPureWhite,
    surfaceVariant = MubaSurfaceElevatedDark,
    onSurfaceVariant = MubaMutedDark,
    outline = MubaBorderDark,
    outlineVariant = MubaBorderDark,
    error = MubaError,
    onError = MubaPureWhite
)

private val MubaLightColorScheme = lightColorScheme(
    primary = MubaPurpleDeep,
    onPrimary = MubaPureWhite,
    primaryContainer = MubaPurpleContainerLight,
    onPrimaryContainer = MubaPurpleDeep,
    secondary = MubaPurple,
    onSecondary = MubaPureWhite,
    secondaryContainer = MubaSurfaceElevatedLight,
    onSecondaryContainer = MubaInkBlack,
    tertiary = MubaInkBlack,
    onTertiary = MubaPureWhite,
    background = MubaOffWhite,
    onBackground = MubaInkBlack,
    surface = MubaSurfaceLight,
    onSurface = MubaInkBlack,
    surfaceVariant = MubaSurfaceElevatedLight,
    onSurfaceVariant = MubaMutedLight,
    outline = MubaBorderLight,
    outlineVariant = MubaBorderLight,
    error = MubaError,
    onError = MubaPureWhite
)

val MubaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MubaAiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MubaDarkColorScheme else MubaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MubaTypography,
        shapes = MubaShapes,
        content = content
    )
}
