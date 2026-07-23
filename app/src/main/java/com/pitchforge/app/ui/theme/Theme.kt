package com.pitchforge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Refined warm "music studio" palette — deep charcoal, amber accent, coral secondary,
// muted gold tertiary. Tuned for the Pixel 10 Pro XL's deep, high-contrast OLED.
private val Amber = Color(0xFFFFB300)
private val AmberDeep = Color(0xFFFF8F00)
private val Coral = Color(0xFFFF7043)
private val Gold = Color(0xFFE6C06A)
private val Ink = Color(0xFF0E0B08)
private val InkSurface = Color(0xFF1A1612)
private val InkSurfaceHi = Color(0xFF241E18)
private val InkSurfaceVariant = Color(0xFF322A22)
private val Outline = Color(0xFF4A4036)
private val OnInk = Color(0xFFF3EBE0)
private val OnInkVariant = Color(0xFFC9B8A8)

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = Color(0xFF4A3300),
    onPrimaryContainer = Color(0xFFFFD9A8),
    secondary = Coral,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF5C2A1A),
    onSecondaryContainer = Color(0xFFFFD0BF),
    tertiary = Gold,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF3D3216),
    onTertiaryContainer = Color(0xFFF6E3B0),
    background = Ink,
    onBackground = OnInk,
    surface = InkSurface,
    onSurface = OnInk,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = OnInkVariant,
    surfaceContainer = InkSurfaceHi,
    outline = Outline,
    outlineVariant = Color(0xFF2E261E),
    error = Color(0xFFFF8A80),
    onError = Ink
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB85C00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9A8),
    onPrimaryContainer = Color(0xFF2E1D00),
    secondary = Color(0xFFB5402A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD0BF),
    onSecondaryContainer = Color(0xFF3A0F02),
    tertiary = Color(0xFF7A5C12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E3B0),
    onTertiaryContainer = Color(0xFF261B00),
    background = Color(0xFFFAF4EC),
    onBackground = Color(0xFF1F1A14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1A14),
    surfaceVariant = Color(0xFFF0E5D8),
    onSurfaceVariant = Color(0xFF5C4B3E),
    surfaceContainer = Color(0xFFF5EDE2),
    outline = Color(0xFF9C8A77),
    outlineVariant = Color(0xFFD9CBB9),
    error = Color(0xFFB3261E),
    onError = Color.White
)

// A consistent, slightly larger corner-radius scale reads as more "product" than defaults.
val PitchForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

private val PitchForgeTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PitchForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent bars for a clean edge-to-edge look on the Pixel 10 Pro XL; the
            // centered front-camera punch-out is handled via insets in the screens, not here.
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // No stretch/glow overscroll — edge hits stop immediately instead of rubber-banding.
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PitchForgeTypography,
            shapes = PitchForgeShapes,
            content = content
        )
    }
}
