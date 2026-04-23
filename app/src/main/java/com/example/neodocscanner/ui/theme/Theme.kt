package com.example.neodocscanner.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkSurface = Color(0xFF232326)
private val DarkSurfaceVariant = Color(0xFF2F2F33)
private val DarkSecondaryContainer = Color(0xFF2B2624)
private val DarkSurfaceHighest = Color(0xFF38383C)

// The supplied design tokens are light-first. These darker neutrals keep dark mode
// usable until a dedicated dark palette is provided.
private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = BgCard,
    primaryContainer = CoralDark,
    onPrimaryContainer = BgCard,
    secondary = CoralDark,
    onSecondary = BgCard,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = BgBase,
    tertiary = GreenAccent,
    onTertiary = BgCard,
    tertiaryContainer = GreenAccent.copy(alpha = 0.24f),
    onTertiaryContainer = BgBase,
    background = Ink,
    onBackground = BgBase,
    surface = DarkSurface,
    surfaceDim = Ink,
    surfaceBright = DarkSurface,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceHighest,
    onSurface = BgBase,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = InkDim,
    surfaceTint = Coral,
    inverseSurface = BgBase,
    inverseOnSurface = Ink,
    inversePrimary = CoralDark,
    error = DangerRed,
    onError = BgCard,
    errorContainer = DangerRed.copy(alpha = 0.24f),
    onErrorContainer = BgBase,
    outline = StrokeMid,
    outlineVariant = StrokeMid.copy(alpha = 0.55f),
    scrim = Color.Black.copy(alpha = 0.48f)
)

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = BgCard,
    primaryContainer = CoralSoft,
    onPrimaryContainer = Coral,
    secondary = CoralDark,
    onSecondary = BgCard,
    secondaryContainer = BgSurface,
    onSecondaryContainer = Ink,
    tertiary = GreenAccent,
    onTertiary = BgCard,
    tertiaryContainer = GreenAccent.copy(alpha = 0.12f),
    onTertiaryContainer = Ink,
    background = BgBase,
    onBackground = Ink,
    surface = BgCard,
    surfaceDim = BgSurface,
    surfaceBright = BgCard,
    surfaceContainerLowest = BgCard,
    surfaceContainerLow = BgCard,
    surfaceContainer = BgSurface,
    surfaceContainerHigh = BgSurface,
    surfaceContainerHighest = StrokeLight,
    onSurface = Ink,
    surfaceVariant = BgSurface,
    onSurfaceVariant = InkMid,
    surfaceTint = Coral,
    inverseSurface = Ink,
    inverseOnSurface = BgCard,
    inversePrimary = CoralDark,
    error = DangerRed,
    onError = BgCard,
    errorContainer = DangerRed.copy(alpha = 0.12f),
    onErrorContainer = DangerRed,
    outline = StrokeMid,
    outlineVariant = StrokeLight,
    scrim = Ink.copy(alpha = 0.40f)
)

@Composable
fun NeoDocScannerTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context.findActivity()
            val window = activity?.window ?: return@SideEffect
            // Keep status bar color stable after returning from ML Kit scanner.
            // Explicitly pin to Coral token for consistent brand color.
            window.statusBarColor = Coral.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
