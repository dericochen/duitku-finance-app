package com.duitku.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.duitku.app.domain.model.ThemeMode

// =====================================================================================
// DUITKU — Neo Finance theme
// Light = cream canvas + deep navy anchor + lime accent.
// Dark  = navy canvas + lighter lime accent.
// Primary = navy (the app's anchor). Lime is a high-emphasis accent exposed via
// ExtendedColors.accent and mapped onto tertiary so existing Material components that
// read tertiary pick up the brand accent automatically.
// =====================================================================================

private val LightColors = lightColorScheme(
    primary = DarkNavy,
    onPrimary = PureWhite,
    primaryContainer = SecondaryNavy,
    onPrimaryContainer = PureWhite,
    secondary = NavyMuted,
    onSecondary = PureWhite,
    secondaryContainer = CreamSurfaceVariant,
    onSecondaryContainer = NavyInk,
    tertiary = LimeInk,
    onTertiary = PureWhite,
    tertiaryContainer = AccentLime,
    onTertiaryContainer = LimeInk,
    error = ExpenseCoral,
    onError = PureWhite,
    errorContainer = Color(0xFFF7D9D1),
    onErrorContainer = Color(0xFF5A1B10),
    background = PrimaryBackground,
    onBackground = NavyInk,
    surface = CreamSurface,
    onSurface = NavyInk,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = SecondaryText,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = CreamSurface,
    surfaceContainer = CreamSurface,
    surfaceContainerHigh = CreamSurfaceVariant,
    surfaceContainerHighest = CreamSurfaceVariant,
    scrim = Color(0x66101C27),
)

private val DarkColors = darkColorScheme(
    primary = AccentLimeDark,
    onPrimary = NavyInk,
    primaryContainer = SecondaryNavy,
    onPrimaryContainer = PureWhite,
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = AccentLimeDark,
    onTertiary = NavyInk,
    tertiaryContainer = Color(0xFF3B5A1C),
    onTertiaryContainer = LimeSoft,
    error = ExpenseCoralDark,
    onError = Color(0xFF4A140A),
    errorContainer = Color(0xFF6B2419),
    onErrorContainer = Color(0xFFF7D9D1),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
    scrim = Color(0x990A121A),
)

/**
 * Warna di luar skema Material 3 standar: aksen merek (lime), pemasukan, pengeluaran,
 * transfer, peringatan, dan warna kontras untuk kartu navy.
 */
@Immutable
data class ExtendedColors(
    val accent: Color,
    val onAccent: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color,
    val safe: Color,
    /** Permukaan navy premium (kartu saldo, header). */
    val navy: Color,
    val onNavy: Color,
    val onNavyMuted: Color,
)

private val LightExtended = ExtendedColors(
    accent = AccentLime,
    onAccent = LimeInk,
    income = IncomeGreen,
    expense = ExpenseCoral,
    transfer = TransferBlue,
    warning = WarningAmber,
    safe = IncomeGreen,
    navy = DarkNavy,
    onNavy = PureWhite,
    onNavyMuted = Color(0xFFAEBDC8),
)

private val DarkExtended = ExtendedColors(
    accent = AccentLimeDark,
    onAccent = NavyInk,
    income = IncomeGreenDark,
    expense = ExpenseCoralDark,
    transfer = TransferBlueDark,
    warning = WarningAmberDark,
    safe = IncomeGreenDark,
    navy = SecondaryNavy,
    onNavy = PureWhite,
    onNavyMuted = Color(0xFFAEBDC8),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

object DuitKuTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

@Composable
fun DuitKuTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DuitKuTypography,
            shapes = DuitKuShapes,
            content = content,
        )
    }
}
