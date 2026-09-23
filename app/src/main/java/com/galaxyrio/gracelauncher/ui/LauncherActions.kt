package com.galaxyrio.gracelauncher.ui

import androidx.compose.ui.geometry.Rect
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.ShortcutResult
import com.galaxyrio.gracelauncher.data.ShortcutStatus
import com.galaxyrio.gracelauncher.data.WallpaperTextMode

/** Platform actions are injected so all launcher surfaces can be previewed and tested. */
data class LauncherActions(
    val requestDefaultHome: () -> Unit = {},
    val appInfo: (LauncherApp) -> Unit = {},
    val screenTime: (LauncherApp) -> Unit = {},
    val uninstall: (LauncherApp) -> Unit = {},
    val rename: (LauncherApp, String) -> Unit = { _, _ -> },
    val categorize: (LauncherApp, String?) -> Unit = { _, _ -> },
    val storePage: (LauncherApp) -> Unit = {},
    val newEvent: () -> Unit = {},
    val openEvent: (ScheduleEvent) -> Unit = {},
    val refreshAgenda: () -> Unit = {},
    val textMode: (WallpaperTextMode) -> Unit = {},
    val themedIcons: (Boolean) -> Unit = {},
    val shortcuts: suspend (LauncherApp) -> ShortcutResult = { ShortcutResult(ShortcutStatus.DefaultLauncherRequired) },
    val cachedShortcuts: (LauncherApp) -> ShortcutResult? = { null },
    val prepareShortcuts: (LauncherApp) -> Unit = {},
    val launchAppAt: ((LauncherApp, Rect) -> Unit)? = null,
    val launchShortcutAt: ((LauncherShortcut, Rect) -> Unit)? = null,
    val launchShortcut: (LauncherShortcut) -> Unit = {},
)
