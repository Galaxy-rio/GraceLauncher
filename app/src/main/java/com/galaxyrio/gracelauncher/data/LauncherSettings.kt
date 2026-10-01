package com.galaxyrio.gracelauncher.data

enum class ThemeMode { System, Light, Dark }

data class LauncherSettings(
    val calendarAgenda: Boolean = true,
    val showBatteryPercentage: Boolean = true,
    val allowHapticFeedback: Boolean = true,
    val useDynamicColors: Boolean = true,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val darkMode: ThemeMode = ThemeMode.System,
    val iconPackPackage: String? = null,
    val mediaPlayer: Boolean = true,
    val weatherEnabled: Boolean = false,
    val weatherForecastDays: Int = 7,
    val weatherLocationId: String? = null,
    /** null delegates to Android's standard SHOW_ALARMS action. */
    val clockAppKey: String? = null,
)

enum class FolderPlacement { Favorites, AppList }

data class LauncherFolder(
    val id: String,
    val name: String,
    val appKeys: List<String>,
    val placement: FolderPlacement,
)

/** A complete first emission is available only after all Room tables have loaded. */
data class LauncherStorageSnapshot(
    val settings: LauncherSettings = LauncherSettings(),
    val hiddenAppKeys: Set<String> = emptySet(),
    val folders: List<LauncherFolder> = emptyList(),
)
