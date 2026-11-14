package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = StudioAmber,
    onPrimary = Color.White,
    primaryContainer = StudioAmberContainerLight,
    onPrimaryContainer = Color(0xFF5C1D04),
    secondary = VerifiedEmerald,
    onSecondary = Color.White,
    secondaryContainer = VerifiedEmeraldContainerLight,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = SearchCobalt,
    onTertiary = Color.White,
    tertiaryContainer = SearchCobaltContainerLight,
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLow = PaperSurfaceContainer,
    surfaceContainer = PaperSurfaceVariant,
    outline = PaperOutline,
    error = SignalRed,
    onError = Color.White,
    errorContainer = SignalRedContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = StudioAmberLight,
    onPrimary = Color(0xFF280D02),
    primaryContainer = StudioAmberContainerDark,
    onPrimaryContainer = Color(0xFFFFD6C4),
    secondary = VerifiedEmeraldLight,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = VerifiedEmeraldContainerDark,
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = SearchCobaltLight,
    onTertiary = Color(0xFF0B1F4B),
    tertiaryContainer = SearchCobaltContainerDark,
    onTertiaryContainer = Color(0xFFBFDBFE),
    background = ObsidianBackground,
    onBackground = ChalkPrimary,
    surface = ObsidianSurface,
    onSurface = ChalkPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ChalkSecondary,
    surfaceContainerLow = ObsidianSurfaceContainer,
    surfaceContainer = ObsidianSurfaceVariant,
    outline = ObsidianOutline,
    error = SignalRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = SignalRedContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

val AudioBrainShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AudioBrainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AudioBrainTypography,
        shapes = AudioBrainShapes,
        content = content
    )
}
