package com.galaxyrio.gracelauncher.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray

/**
 * New launcher data lives in Room; legacy favorites, renames, categories and
 * appearance preferences remain in their existing stores without being reset.
 * All reads are observable Room queries and every write is a suspending DAO
 * operation, so no database access blocks the main thread.
 */
class LauncherSettingsRepository(private val database: LauncherDatabase) {
    private val dao = database.settingsDao()

    val snapshots: Flow<LauncherStorageSnapshot> = combine(
        dao.observeSettings(),
        dao.observeHiddenApps(),
        dao.observeFolders(),
    ) { settings, hiddenApps, folders ->
        LauncherStorageSnapshot(
            settings = settings?.toSettings() ?: LauncherSettings(),
            hiddenAppKeys = hiddenApps.toSet(),
            folders = folders.map { entry ->
                LauncherFolder(
                    id = entry.folder.id,
                    name = entry.folder.name,
                    appKeys = entry.apps.sortedBy(FolderAppEntity::position).map(FolderAppEntity::appKey),
                    placement = FolderPlacement.entries.firstOrNull { it.name == entry.folder.placement }
                        ?: FolderPlacement.AppList,
                )
            },
        )
    }.distinctUntilChanged()

    suspend fun updateSettings(settings: LauncherSettings) {
        dao.saveSettings(LauncherSettingsEntity(
            calendarAgenda = settings.calendarAgenda,
            showBatteryPercentage = settings.showBatteryPercentage,
            allowHapticFeedback = settings.allowHapticFeedback,
            useDynamicColors = settings.useDynamicColors,
            themeColor = settings.themeColor,
            darkMode = settings.darkMode.name,
            iconPackPackage = settings.iconPackPackage,
            iconDesignJson = settings.iconDesign?.encode(),
            iconPackPackagesJson = normalizeIconPackOrder(settings.iconPackPackages).takeIf { it.isNotEmpty() }
                ?.let { JSONArray(it).toString() },
            mediaPlayer = settings.mediaPlayer,
            weatherEnabled = settings.weatherEnabled,
            weatherForecastDays = settings.weatherForecastDays.coerceIn(1, 14),
            weatherLocationId = settings.weatherLocationId,
            clockAppKey = settings.clockAppKey,
            clockStyleJson = settings.clockStyle.encode(),
            homeLayoutJson = settings.homeLayout.encode(),
            hideStatusBar = settings.hideStatusBar,
            hideAlphabet = settings.hideAlphabet,
            hideFavoriteNames = settings.hideFavoriteNames,
            dimWallpaper = settings.dimWallpaper,
            wallpaperDimAmount = settings.wallpaperDimAmount.coerceIn(0, 100),
            blurWallpaper = settings.blurWallpaper,
            wallpaperBlurRadius = settings.wallpaperBlurRadius.coerceIn(0, 48),
            appFontId = settings.appFontId,
            applyFontToSettings = settings.applyFontToSettings,
        ))
    }

    /** Read-modify-write is atomic even when a UI emission has not arrived yet. */
    suspend fun mutateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        database.withTransaction {
            val current = dao.readSettings()?.toSettings() ?: LauncherSettings()
            updateSettings(transform(current))
        }
    }

    suspend fun setHiddenApps(keys: Set<String>) {
        // Membership and favorites are deliberately retained when an app is
        // hidden or temporarily uninstalled; unhide/reinstall restores them.
        dao.replaceHiddenApps(keys.filter(String::isNotBlank).map(::HiddenAppEntity))
    }

    suspend fun saveFolder(folder: LauncherFolder) {
        require(folder.id.isNotBlank()) { "A folder must have a stable id" }
        val name = folder.name.trim()
        require(name.isNotEmpty()) { "A folder name must not be blank" }
        val keys = folder.appKeys.filter(String::isNotBlank).distinct()
        dao.saveFolder(
            LauncherFolderEntity(folder.id, name, folder.placement.name),
            keys.mapIndexed { index, key -> FolderAppEntity(folder.id, key, index) },
        )
    }

    suspend fun deleteFolder(id: String) = dao.deleteFolder(id)
}

private fun LauncherSettingsEntity.toSettings() = LauncherSettings(
    calendarAgenda = calendarAgenda,
    showBatteryPercentage = showBatteryPercentage,
    allowHapticFeedback = allowHapticFeedback,
    useDynamicColors = useDynamicColors,
    themeColor = themeColor,
    darkMode = ThemeMode.entries.firstOrNull { it.name == darkMode } ?: ThemeMode.System,
    iconPackPackage = iconPackPackage,
    iconDesign = iconDesignJson?.let(ItemIcon::decode),
    iconPackPackages = decodeIconPackOrder(iconPackPackagesJson),
    mediaPlayer = mediaPlayer,
    weatherEnabled = weatherEnabled,
    weatherForecastDays = weatherForecastDays.coerceIn(1, 14),
    weatherLocationId = weatherLocationId,
    clockAppKey = clockAppKey,
    clockStyle = ClockStyle.decode(clockStyleJson),
    homeLayout = HomeLayout.decode(homeLayoutJson),
    hideStatusBar = hideStatusBar,
    hideAlphabet = hideAlphabet,
    hideFavoriteNames = hideFavoriteNames,
    dimWallpaper = dimWallpaper,
    wallpaperDimAmount = wallpaperDimAmount.coerceIn(0, 100),
    blurWallpaper = blurWallpaper,
    wallpaperBlurRadius = wallpaperBlurRadius.coerceIn(0, 48),
    appFontId = appFontId,
    applyFontToSettings = applyFontToSettings,
)

private fun decodeIconPackOrder(json: String?): List<String> = runCatching {
    if (json == null) emptyList() else JSONArray(json).let { array ->
        normalizeIconPackOrder((0 until array.length()).mapNotNull { index ->
            (array.opt(index) as? String)?.takeIf(String::isNotBlank)
        })
    }
}.getOrDefault(emptyList())
