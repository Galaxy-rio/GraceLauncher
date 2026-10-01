package com.galaxyrio.gracelauncher.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = MintContainer,
    onPrimaryContainer = Moon,
    secondary = PaleMint,
    onSecondary = Ink,
    tertiary = Coral,
    background = Ink,
    onBackground = Moon,
    surface = Ink,
    onSurface = Moon,
    surfaceVariant = DeepTeal,
    onSurfaceVariant = Mist,
)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Paper,
    primaryContainer = PaleMint,
    onPrimaryContainer = Ink,
    secondary = DeepTeal,
    tertiary = Rose,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GraceLauncherTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        seedColor != null -> rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
