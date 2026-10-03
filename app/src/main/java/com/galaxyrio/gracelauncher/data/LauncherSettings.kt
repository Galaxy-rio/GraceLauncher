package com.galaxyrio.gracelauncher.data

enum class ThemeMode { System, Light, Dark }

enum class AppFont(val id: String?) {
    Default(null), System("system"), NotoSans("noto_sans"), Sacramento("sacramento"),
    Bokor("bokor"), Plaster("plaster"), Monoton("monoton"), LuckiestGuy("luckiest_guy"),
}

data class LauncherSettings(
    val calendarAgenda: Boolean = true,
    val showBatteryPercentage: Boolean = true,
    val allowHapticFeedback: Boolean = true,
    val useDynamicColors: Boolean = true,
    val themeColor: Int = 0xFF6750A4.toInt(),
    val darkMode: ThemeMode = ThemeMode.System,
    /** Retained for installations that used the original single-pack setting. */
    val iconPackPackage: String? = null,
    val iconPackPackages: List<String> = emptyList(),
    val iconDesign: ItemIcon? = null,
    val mediaPlayer: Boolean = true,
    val weatherEnabled: Boolean = false,
    val weatherForecastDays: Int = 7,
    val weatherLocationId: String? = null,
    /** null delegates to Android's standard SHOW_ALARMS action. */
    val clockAppKey: String? = null,
    val clockStyle: ClockStyle = ClockStyle(),
    val homeLayout: HomeLayout = HomeLayout(),
    val hideStatusBar: Boolean = true,
    val hideAlphabet: Boolean = false,
    val hideFavoriteNames: Boolean = false,
    val dimWallpaper: Boolean = false,
    val wallpaperDimAmount: Int = 20,
    val blurWallpaper: Boolean = true,
    val wallpaperBlurRadius: Int = 16,
    /** null keeps the original Josefin Sans; imported fonts use their private file id. */
    val appFontId: String? = null,
    val applyFontToSettings: Boolean = true,
) {
    /** Icon designer overrides precede this order; system icons always follow it. */
    val enabledIconPackPackages: List<String>
        get() = normalizeIconPackOrder(iconPackPackages.ifEmpty { listOfNotNull(iconPackPackage) })

    fun withIconPacks(packages: List<String>): LauncherSettings = copy(
        iconPackPackage = null,
        iconPackPackages = normalizeIconPackOrder(packages),
    )
}

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
