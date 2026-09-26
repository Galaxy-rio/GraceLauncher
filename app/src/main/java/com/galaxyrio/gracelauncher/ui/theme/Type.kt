package com.galaxyrio.gracelauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultMaterialTypography = Typography()

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 58.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = DefaultMaterialTypography.displayMedium.copy(fontFamily = LauncherFontFamily),
    displaySmall = DefaultMaterialTypography.displaySmall.copy(fontFamily = LauncherFontFamily),
    headlineLarge = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = DefaultMaterialTypography.headlineMedium.copy(fontFamily = LauncherFontFamily),
    headlineSmall = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = DefaultMaterialTypography.titleSmall.copy(fontFamily = LauncherFontFamily),
    bodyLarge = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = DefaultMaterialTypography.bodySmall.copy(fontFamily = LauncherFontFamily),
    labelLarge = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LauncherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = DefaultMaterialTypography.labelSmall.copy(fontFamily = LauncherFontFamily),
)
