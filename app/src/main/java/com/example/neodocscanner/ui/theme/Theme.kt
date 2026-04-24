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

private val DarkBackground = Color(0xFF14171B)
private val DarkSurface = Color(0xFF1C2025)
private val DarkSurfaceVariant = Color(0xFF2A2F36)
private val DarkSecondaryContainer = Color(0xFF2A2628)
private val DarkSurfaceHighest = Color(0xFF343A42)
private val DarkOnBackground = Color(0xFFE7EAF0)
private val DarkOnSurface = Color(0xFFE6E9EE)
private val DarkOnSurfaceVariant = Color(0xFFB7BEC9)
private val DarkOutline = Color(0xFF515A66)

// The supplied design tokens are light-first. These darker neutrals keep dark mode
// usable until a dedicated dark palette is provided.
private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF171A1E),
    primaryContainer = Color(0xFF5A3932),
    onPrimaryContainer = Color(0xFFFFD8CF),
    secondary = CoralDark,
    onSecondary = Color(0xFF161A1E),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFEBDADC),
    tertiary = GreenAccent,
    onTertiary = Color(0xFF141A14),
    tertiaryContainer = Color(0xFF24412E),
    onTertiaryContainer = Color(0xFFD7F3DE),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceHighest,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Coral,
    inverseSurface = DarkOnBackground,
    inverseOnSurface = DarkBackground,
    inversePrimary = CoralDark,
    error = DangerRed,
    onError = Color(0xFF1F1A1A),
    errorContainer = Color(0xFF5D2A2A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.72f),
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
            // In dark mode keep status bar aligned with dark surface to reduce glare.
            window.statusBarColor = if (darkTheme) {
                colorScheme.surface.toArgb()
            } else {
                Coral.toArgb()
            }
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
