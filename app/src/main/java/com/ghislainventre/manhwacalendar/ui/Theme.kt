package com.ghislainventre.manhwacalendar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Identité visuelle : fond « encre », accent rose vif, ambre pour les nouveautés.
// Pas de couleurs dynamiques Android : l'app garde la même allure sur tous les téléphones.

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6F91),
    onPrimary = Color(0xFF3A0717),
    primaryContainer = Color(0xFF4D1529),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFFFB547),
    onSecondary = Color(0xFF2A1800),
    secondaryContainer = Color(0xFF3F2C0C),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFA594FF),
    onTertiary = Color(0xFF1C0F5C),
    background = Color(0xFF0F0E14),
    onBackground = Color(0xFFEEEBF3),
    surface = Color(0xFF0F0E14),
    onSurface = Color(0xFFEEEBF3),
    surfaceVariant = Color(0xFF25222E),
    onSurfaceVariant = Color(0xFFA6A1B2),
    surfaceContainerLowest = Color(0xFF0A090E),
    surfaceContainerLow = Color(0xFF15131B),
    surfaceContainer = Color(0xFF1B1922),
    surfaceContainerHigh = Color(0xFF23202B),
    surfaceContainerHighest = Color(0xFF2C2935),
    outline = Color(0xFF4C4857),
    outlineVariant = Color(0xFF2F2C38),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF4A1512),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD81B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001A),
    secondary = Color(0xFFB86E00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3BF),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFF5B45D6),
    onTertiary = Color.White,
    background = Color(0xFFF7F5FA),
    onBackground = Color(0xFF1B1A20),
    surface = Color(0xFFF7F5FA),
    onSurface = Color(0xFF1B1A20),
    surfaceVariant = Color(0xFFECE8F1),
    onSurfaceVariant = Color(0xFF625E6C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFECF3),
    surfaceContainerHighest = Color(0xFFE7E3ED),
    outline = Color(0xFF8E8999),
    outlineVariant = Color(0xFFE2DDE8),
)

private val base = Typography()

private val AppTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ManhwaCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
