package com.pitchforge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.pitchforge.app.domain.CosmeticTheme

val PitchForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/** Swatch colors for the settings picker (dark-primary accent). */
fun CosmeticTheme.swatchPrimary(): Color = when (this) {
    CosmeticTheme.STUDIO -> Color(0xFFFFB300)
    CosmeticTheme.OCEAN -> Color(0xFF4DD0E1)
    CosmeticTheme.FOREST -> Color(0xFF81C784)
    CosmeticTheme.VOLCANIC -> Color(0xFFFF6E40)
    CosmeticTheme.AURORA -> Color(0xFF80CBC4)
    CosmeticTheme.MIDNIGHT -> Color(0xFFFF4081)
}

fun colorSchemeFor(theme: CosmeticTheme, darkTheme: Boolean): ColorScheme =
    if (darkTheme) theme.darkScheme() else theme.lightScheme()

private fun CosmeticTheme.darkScheme(): ColorScheme = when (this) {
    CosmeticTheme.STUDIO -> darkColorScheme(
        primary = Color(0xFFFFB300),
        onPrimary = Color(0xFF0E0B08),
        primaryContainer = Color(0xFF4A3300),
        onPrimaryContainer = Color(0xFFFFD9A8),
        secondary = Color(0xFFFF7043),
        onSecondary = Color(0xFF0E0B08),
        secondaryContainer = Color(0xFF5C2A1A),
        onSecondaryContainer = Color(0xFFFFD0BF),
        tertiary = Color(0xFFE6C06A),
        onTertiary = Color(0xFF0E0B08),
        tertiaryContainer = Color(0xFF3D3216),
        onTertiaryContainer = Color(0xFFF6E3B0),
        background = Color(0xFF0E0B08),
        onBackground = Color(0xFFF3EBE0),
        surface = Color(0xFF1A1612),
        onSurface = Color(0xFFF3EBE0),
        surfaceVariant = Color(0xFF322A22),
        onSurfaceVariant = Color(0xFFC9B8A8),
        surfaceContainer = Color(0xFF241E18),
        outline = Color(0xFF4A4036),
        outlineVariant = Color(0xFF2E261E),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF0E0B08)
    )
    CosmeticTheme.OCEAN -> darkColorScheme(
        primary = Color(0xFF4DD0E1),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFFB2EBF2),
        secondary = Color(0xFF80CBC4),
        onSecondary = Color(0xFF003731),
        secondaryContainer = Color(0xFF005048),
        onSecondaryContainer = Color(0xFFB2DFDB),
        tertiary = Color(0xFF90CAF9),
        onTertiary = Color(0xFF0D2137),
        tertiaryContainer = Color(0xFF1A3A5C),
        onTertiaryContainer = Color(0xFFBBDEFB),
        background = Color(0xFF071318),
        onBackground = Color(0xFFE0F2F1),
        surface = Color(0xFF0E1C22),
        onSurface = Color(0xFFE0F2F1),
        surfaceVariant = Color(0xFF1A3038),
        onSurfaceVariant = Color(0xFFA7C4CB),
        surfaceContainer = Color(0xFF14262D),
        outline = Color(0xFF3D565E),
        outlineVariant = Color(0xFF243940),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF071318)
    )
    CosmeticTheme.FOREST -> darkColorScheme(
        primary = Color(0xFF81C784),
        onPrimary = Color(0xFF0A1F0C),
        primaryContainer = Color(0xFF1B3D1E),
        onPrimaryContainer = Color(0xFFC8E6C9),
        secondary = Color(0xFFA1887F),
        onSecondary = Color(0xFF1B120E),
        secondaryContainer = Color(0xFF3E2723),
        onSecondaryContainer = Color(0xFFD7CCC8),
        tertiary = Color(0xFFC5E1A5),
        onTertiary = Color(0xFF1A2A0A),
        tertiaryContainer = Color(0xFF33461C),
        onTertiaryContainer = Color(0xFFDCEDC8),
        background = Color(0xFF0C120D),
        onBackground = Color(0xFFE8F0E6),
        surface = Color(0xFF141C15),
        onSurface = Color(0xFFE8F0E6),
        surfaceVariant = Color(0xFF253028),
        onSurfaceVariant = Color(0xFFB5C4B7),
        surfaceContainer = Color(0xFF1A241B),
        outline = Color(0xFF3E4C40),
        outlineVariant = Color(0xFF28332A),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF0C120D)
    )
    CosmeticTheme.VOLCANIC -> darkColorScheme(
        primary = Color(0xFFFF6E40),
        onPrimary = Color(0xFF2A0A00),
        primaryContainer = Color(0xFF5C1F00),
        onPrimaryContainer = Color(0xFFFFCCBC),
        secondary = Color(0xFFFFAB40),
        onSecondary = Color(0xFF2A1600),
        secondaryContainer = Color(0xFF5C3300),
        onSecondaryContainer = Color(0xFFFFE0B2),
        tertiary = Color(0xFFFF8A65),
        onTertiary = Color(0xFF2A0C00),
        tertiaryContainer = Color(0xFF5C2200),
        onTertiaryContainer = Color(0xFFFFCCBC),
        background = Color(0xFF120A08),
        onBackground = Color(0xFFF5E6DF),
        surface = Color(0xFF1C1210),
        onSurface = Color(0xFFF5E6DF),
        surfaceVariant = Color(0xFF332420),
        onSurfaceVariant = Color(0xFFCFB5AC),
        surfaceContainer = Color(0xFF261814),
        outline = Color(0xFF4A3731),
        outlineVariant = Color(0xFF2E201C),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF120A08)
    )
    CosmeticTheme.AURORA -> darkColorScheme(
        primary = Color(0xFF80CBC4),
        onPrimary = Color(0xFF003732),
        primaryContainer = Color(0xFF004D47),
        onPrimaryContainer = Color(0xFFB2DFDB),
        secondary = Color(0xFFB39DDB),
        onSecondary = Color(0xFF1A1033),
        secondaryContainer = Color(0xFF311B4D),
        onSecondaryContainer = Color(0xFFE1BEE7),
        tertiary = Color(0xFF81D4FA),
        onTertiary = Color(0xFF00344A),
        tertiaryContainer = Color(0xFF01579B),
        onTertiaryContainer = Color(0xFFB3E5FC),
        background = Color(0xFF0A1214),
        onBackground = Color(0xFFE4F1F0),
        surface = Color(0xFF121C1E),
        onSurface = Color(0xFFE4F1F0),
        surfaceVariant = Color(0xFF243235),
        onSurfaceVariant = Color(0xFFAFC0C2),
        surfaceContainer = Color(0xFF182628),
        outline = Color(0xFF3C4E51),
        outlineVariant = Color(0xFF263437),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF0A1214)
    )
    CosmeticTheme.MIDNIGHT -> darkColorScheme(
        primary = Color(0xFFFF4081),
        onPrimary = Color(0xFF2A0014),
        primaryContainer = Color(0xFF5C002E),
        onPrimaryContainer = Color(0xFFFF80AB),
        secondary = Color(0xFF40C4FF),
        onSecondary = Color(0xFF001F2A),
        secondaryContainer = Color(0xFF003A4D),
        onSecondaryContainer = Color(0xFF80D8FF),
        tertiary = Color(0xFFE040FB),
        onTertiary = Color(0xFF2A0030),
        tertiaryContainer = Color(0xFF4A0058),
        onTertiaryContainer = Color(0xFFEA80FC),
        background = Color(0xFF0A0610),
        onBackground = Color(0xFFF3E8F0),
        surface = Color(0xFF140E1A),
        onSurface = Color(0xFFF3E8F0),
        surfaceVariant = Color(0xFF2A2233),
        onSurfaceVariant = Color(0xFFC4B5C9),
        surfaceContainer = Color(0xFF1C1524),
        outline = Color(0xFF463A4F),
        outlineVariant = Color(0xFF2C2436),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF0A0610)
    )
}

private fun CosmeticTheme.lightScheme(): ColorScheme = when (this) {
    CosmeticTheme.STUDIO -> lightColorScheme(
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
    CosmeticTheme.OCEAN -> lightColorScheme(
        primary = Color(0xFF006874),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2EBF2),
        onPrimaryContainer = Color(0xFF002022),
        secondary = Color(0xFF4F6367),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD2E4E8),
        onSecondaryContainer = Color(0xFF0B1E22),
        tertiary = Color(0xFF1565C0),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBBDEFB),
        onTertiaryContainer = Color(0xFF001D36),
        background = Color(0xFFF4FAFB),
        onBackground = Color(0xFF151D1F),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF151D1F),
        surfaceVariant = Color(0xFFDCE8EB),
        onSurfaceVariant = Color(0xFF3F4C4F),
        surfaceContainer = Color(0xFFE8F2F4),
        outline = Color(0xFF6F7F83),
        outlineVariant = Color(0xFFBEC9CC),
        error = Color(0xFFB3261E),
        onError = Color.White
    )
    CosmeticTheme.FOREST -> lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = Color(0xFF0A1F0C),
        secondary = Color(0xFF6D4C41),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7CCC8),
        onSecondaryContainer = Color(0xFF1B120E),
        tertiary = Color(0xFF558B2F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCEDC8),
        onTertiaryContainer = Color(0xFF1A2A0A),
        background = Color(0xFFF5F8F4),
        onBackground = Color(0xFF171D16),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF171D16),
        surfaceVariant = Color(0xFFDDE5DA),
        onSurfaceVariant = Color(0xFF424940),
        surfaceContainer = Color(0xFFE8EEE6),
        outline = Color(0xFF72796F),
        outlineVariant = Color(0xFFC1C9BC),
        error = Color(0xFFB3261E),
        onError = Color.White
    )
    CosmeticTheme.VOLCANIC -> lightColorScheme(
        primary = Color(0xFFBF360C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFCCBC),
        onPrimaryContainer = Color(0xFF2A0A00),
        secondary = Color(0xFFE65100),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFE0B2),
        onSecondaryContainer = Color(0xFF2A1600),
        tertiary = Color(0xFFD84315),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFCCBC),
        onTertiaryContainer = Color(0xFF2A0C00),
        background = Color(0xFFFFF6F2),
        onBackground = Color(0xFF231814),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF231814),
        surfaceVariant = Color(0xFFF0DED6),
        onSurfaceVariant = Color(0xFF53433D),
        surfaceContainer = Color(0xFFF8EBE5),
        outline = Color(0xFF86736C),
        outlineVariant = Color(0xFFD8C5BD),
        error = Color(0xFFB3261E),
        onError = Color.White
    )
    CosmeticTheme.AURORA -> lightColorScheme(
        primary = Color(0xFF00695C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2DFDB),
        onPrimaryContainer = Color(0xFF00201C),
        secondary = Color(0xFF5E35B1),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1C4E9),
        onSecondaryContainer = Color(0xFF1A1033),
        tertiary = Color(0xFF0277BD),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFB3E5FC),
        onTertiaryContainer = Color(0xFF001D36),
        background = Color(0xFFF3FAF9),
        onBackground = Color(0xFF151D1C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF151D1C),
        surfaceVariant = Color(0xFFD9E6E4),
        onSurfaceVariant = Color(0xFF3E4A48),
        surfaceContainer = Color(0xFFE6F1EF),
        outline = Color(0xFF6E7A78),
        outlineVariant = Color(0xFFBDC9C7),
        error = Color(0xFFB3261E),
        onError = Color.White
    )
    CosmeticTheme.MIDNIGHT -> lightColorScheme(
        primary = Color(0xFFC2185B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF8BBD0),
        onPrimaryContainer = Color(0xFF2A0014),
        secondary = Color(0xFF0277BD),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB3E5FC),
        onSecondaryContainer = Color(0xFF001F2A),
        tertiary = Color(0xFF8E24AA),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE1BEE7),
        onTertiaryContainer = Color(0xFF2A0030),
        background = Color(0xFFFAF4F7),
        onBackground = Color(0xFF1F151A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1F151A),
        surfaceVariant = Color(0xFFEEDFE7),
        onSurfaceVariant = Color(0xFF504049),
        surfaceContainer = Color(0xFFF5E8EF),
        outline = Color(0xFF83727B),
        outlineVariant = Color(0xFFD4C2CC),
        error = Color(0xFFB3261E),
        onError = Color.White
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PitchForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: CosmeticTheme = CosmeticTheme.STUDIO,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(theme, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PitchForgeTypography,
            shapes = PitchForgeShapes,
            content = content
        )
    }
}
