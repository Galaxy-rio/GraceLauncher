package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.ClockPresetFont

// Resource fonts are loaded locally and cached by Compose's font resolver.
private val Sacramento = FontFamily(Font(R.font.sacramento))
private val Bokor = FontFamily(Font(R.font.bokor))
private val Plaster = FontFamily(Font(R.font.plaster))
private val Monoton = FontFamily(Font(R.font.monoton))
private val LuckiestGuy = FontFamily(Font(R.font.luckiest_guy))

internal fun ClockPresetFont.family(): FontFamily = when (this) {
    ClockPresetFont.Sacramento -> Sacramento
    ClockPresetFont.Bokor -> Bokor
    ClockPresetFont.Plaster -> Plaster
    ClockPresetFont.Monoton -> Monoton
    ClockPresetFont.LuckiestGuy -> LuckiestGuy
}
