package com.llamacpp.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalLlamaColors = staticCompositionLocalOf { LightLlamaColors }

private fun lightScheme() = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightPrimaryForeground,
    primaryContainer = LightSecondary,
    onPrimaryContainer = LightSecondaryForeground,
    secondary = LightSecondary,
    onSecondary = LightSecondaryForeground,
    secondaryContainer = LightMuted,
    onSecondaryContainer = LightForeground,
    tertiary = LightAccent,
    onTertiary = LightAccentForeground,
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightCard,
    onSurface = LightCardForeground,
    surfaceVariant = LightMuted,
    onSurfaceVariant = LightMutedForeground,
    surfaceContainerLowest = LightCard,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightMuted,
    surfaceContainerHigh = LightSecondary,
    surfaceContainerHighest = LightSecondary,
    error = LightDestructive,
    onError = LightDestructiveForeground,
    outline = LightBorder,
    outlineVariant = LightInput,
    inverseSurface = LightForeground,
    inverseOnSurface = LightBackground,
    inversePrimary = LightPrimary,
    surfaceTint = LightPrimary,
    scrim = Color.Black,
)

private fun darkScheme() = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkPrimaryForeground,
    primaryContainer = DarkSecondary,
    onPrimaryContainer = DarkSecondaryForeground,
    secondary = DarkSecondary,
    onSecondary = DarkSecondaryForeground,
    secondaryContainer = DarkMuted,
    onSecondaryContainer = DarkForeground,
    tertiary = DarkAccent,
    onTertiary = DarkAccentForeground,
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkCard,
    onSurface = DarkCardForeground,
    surfaceVariant = DarkMuted,
    onSurfaceVariant = DarkMutedForeground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkMuted,
    surfaceContainerHigh = DarkSecondary,
    surfaceContainerHighest = DarkSecondary,
    error = DarkDestructive,
    onError = DarkDestructiveForeground,
    outline = DarkBorder,
    outlineVariant = DarkInput,
    inverseSurface = DarkForeground,
    inverseOnSurface = DarkBackground,
    inversePrimary = DarkPrimary,
    surfaceTint = DarkPrimary,
    scrim = Color.Black,
)

private val LlamaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun LlamaChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkScheme() else lightScheme()
    val extra = if (darkTheme) DarkLlamaColors else LightLlamaColors

    CompositionLocalProvider(LocalLlamaColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LlamaTypography,
            shapes = LlamaShapes,
            content = content,
        )
    }
}
