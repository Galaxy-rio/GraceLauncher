package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.CalendarRepository
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherDatabase
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.data.LauncherPreferences
import com.galaxyrio.gracelauncher.data.LauncherSettings
import com.galaxyrio.gracelauncher.data.ClockStyle
import com.galaxyrio.gracelauncher.data.LauncherSettingsRepository
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.ShortcutRepository
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import com.galaxyrio.gracelauncher.data.icons.IconPackInfo
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconPackStatus
import com.galaxyrio.gracelauncher.platform.DefaultHome
import com.galaxyrio.gracelauncher.platform.BreezyWeatherUpdates
import com.galaxyrio.gracelauncher.data.media.MediaCommand
import com.galaxyrio.gracelauncher.data.media.MediaSessionRepository
import com.galaxyrio.gracelauncher.data.media.MediaSnapshot
import com.galaxyrio.gracelauncher.data.media.NowPlaying
import com.galaxyrio.gracelauncher.data.notifications.AppNotification
import com.galaxyrio.gracelauncher.data.notifications.appNotifications
import com.galaxyrio.gracelauncher.data.weather.BreezyWeatherRepository
import com.galaxyrio.gracelauncher.data.weather.WeatherState
import com.galaxyrio.gracelauncher.data.weather.WeatherStatus
import java.text.Collator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ScheduleStatus {
    PermissionRequired,
    Loading,
    Ready,
    Error,
}

data class LauncherUiState(
    val apps: List<LauncherApp> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val isLoadingApps: Boolean = true,
    val appLoadFailed: Boolean = false,
    val events: List<ScheduleEvent> = emptyList(),
    val scheduleStatus: ScheduleStatus = ScheduleStatus.PermissionRequired,
    val hasShortcutAccess: Boolean = false,
    val categories: Map<String, String> = emptyMap(),
    val textMode: WallpaperTextMode = WallpaperTextMode.Auto,
    val themedIcons: Boolean = true,
    val settings: LauncherSettings = LauncherSettings(),
    val hiddenAppKeys: Set<String> = emptySet(),
    val folders: List<LauncherFolder> = emptyList(),
    val isLoadingSettings: Boolean = false,
    val settingsLoadFailed: Boolean = false,
    val settingsSaveFailed: Boolean = false,
    val iconPacks: List<IconPackInfo> = emptyList(),
    val isLoadingIconPacks: Boolean = false,
    val iconPacksLoadFailed: Boolean = false,
    val iconPackStatus: IconPackStatus = IconPackStatus.System,
    // Unknown until the first system query, avoiding a banner flash for an existing default.
    val isDefaultHome: Boolean? = null,
    val media: MediaSnapshot = MediaSnapshot(),
    val notifications: Map<String, List<AppNotification>> = emptyMap(),
    val favoriteOrder: List<String> = emptyList(),
    val weather: WeatherState = WeatherState(),
) {
    val homeMedia: NowPlaying?
        get() = media.nowPlaying.takeIf {
            settings.mediaPlayer && media.hasAccess && !isLoadingSettings && !settingsLoadFailed
        }

    val visibleApps: List<LauncherApp>
        get() = if (isLoadingSettings || settingsLoadFailed) emptyList()
        else apps.filterNot { it.key in hiddenAppKeys }

    val favoriteApps: List<LauncherApp>
        get() {
            val favorites = visibleApps.filter { it.key in favoriteKeys }.associateBy(LauncherApp::key)
            return (favoriteOrder + favorites.keys).distinct().mapNotNull(favorites::get)
        }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val iconPacks = IconPackRepository(application)
    private val appRepository = AppRepository(application, iconPacks)
    private val calendarRepository = CalendarRepository(application)
    private val favoritesStore = FavoritesStore(application)
    private val preferences = LauncherPreferences(application)
    private val shortcutRepository = ShortcutRepository(application)
    private val settingsRepository = LauncherSettingsRepository(LauncherDatabase.getInstance(application))
    private val mediaRepository = MediaSessionRepository(application)
    private val weatherRepository = BreezyWeatherRepository(application)
    private val settingsWriteMutex = Mutex()

    private val _uiState = MutableStateFlow(LauncherUiState(
        categories = preferences.categories(),
        textMode = preferences.textMode,
        themedIcons = preferences.themedIcons,
        isLoadingSettings = true,
    ))
    val uiState = _uiState.asStateFlow()

    private val _returnHomeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnHomeRequests = _returnHomeRequests.asSharedFlow()

    private var appLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null
    private var weatherLoadJob: Job? = null
    private var lastWeatherRefresh = 0L
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            refreshApps()
        }
    }
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshApps() }
    }

    init {
        viewModelScope.launch {
            BreezyWeatherUpdates.events.collectLatest {
                // Refresh after the final signal, so a forecast update following
                // a current-conditions update is never lost to a leading-edge throttle.
                delay(500)
                refreshWeather()
            }
        }
        viewModelScope.launch {
            appNotifications.state.collect { notifications -> _uiState.update { it.copy(notifications = notifications) } }
        }
        viewModelScope.launch {
            mediaRepository.state.collect { media -> _uiState.update { it.copy(media = media) } }
        }
        ContextCompat.registerReceiver(application, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(application, dateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            settingsRepository.snapshots
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e("LauncherViewModel", "Unable to read launcher settings", error)
                    _uiState.update { it.copy(isLoadingSettings = false, settingsLoadFailed = true) }
                    mediaRepository.setEnabled(false)
                    refreshWeather()
                }
                .collect { snapshot ->
                    val previous = _uiState.value
                    _uiState.update {
                        it.copy(
                            settings = snapshot.settings,
                            hiddenAppKeys = snapshot.hiddenAppKeys,
                            folders = snapshot.folders,
                            isLoadingSettings = false,
                            settingsLoadFailed = false,
                        )
                    }
                    if (previous.isLoadingSettings ||
                        previous.settings.calendarAgenda != snapshot.settings.calendarAgenda
                    ) refreshSchedule()
                    if (previous.isLoadingSettings || previous.settings.mediaPlayer != snapshot.settings.mediaPlayer) {
                        mediaRepository.setEnabled(snapshot.settings.mediaPlayer)
                    }
                    if (previous.isLoadingSettings || previous.settings.iconPackPackage != snapshot.settings.iconPackPackage) refreshApps()
                    if (previous.isLoadingSettings || previous.settings.weatherEnabled != snapshot.settings.weatherEnabled ||
                        previous.settings.weatherLocationId != snapshot.settings.weatherLocationId
                    ) refreshWeather()
                    if (previous.isLoadingSettings || previous.hiddenAppKeys != snapshot.hiddenAppKeys) {
                        val favorites = _uiState.value.favoriteApps
                        viewModelScope.launch { shortcutRepository.prefetch(favorites) }
                    }
                }
        }
        refreshApps()
    }

    fun refreshApps() {
        mediaRepository.refresh()
        refreshWeather()
        // Both HOME and the standalone settings Activity call this on resume;
        // also refresh after the role request, including cancellation.
        val isDefaultHome = DefaultHome.isDefault(getApplication())
        _uiState.update { it.copy(isDefaultHome = isDefaultHome) }
        // Wait for the stored pack choice: do not flash system icons on cold start.
        if (_uiState.value.isLoadingSettings || _uiState.value.settingsLoadFailed) return
        appLoadJob?.cancel()
        appLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingApps = true, appLoadFailed = false, isLoadingIconPacks = true, hasShortcutAccess = shortcutRepository.hasAccess())
            }
            try {
                val installed = iconPacks.installedPacks()
                _uiState.update { it.copy(iconPacks = installed, isLoadingIconPacks = false, iconPacksLoadFailed = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoadingIconPacks = false, iconPacksLoadFailed = true) }
            }
            val selectedPack = _uiState.value.settings.iconPackPackage
            val status = when {
                selectedPack == null -> IconPackStatus.System
                iconPacks.load(selectedPack) != null -> IconPackStatus.Ready
                else -> IconPackStatus.Unavailable
            }
            runCatching { appRepository.loadApps(selectedPack) }
                .onSuccess { apps ->
                    val renames = preferences.renames()
                    val collator = Collator.getInstance()
                    val displayedApps = apps.map { it.copy(label = renames[it.key] ?: it.originalLabel) }
                        .sortedWith { a, b -> collator.compare(a.label, b.label) }
                    val favorites = favoritesStore.favoritesFor(displayedApps)
                    _uiState.update {
                        it.copy(
                            apps = displayedApps,
                            favoriteKeys = favorites.toSet(),
                            favoriteOrder = favorites,
                            // Settings also has an independent Activity/ViewModel.
                            // Refresh legacy preferences when returning to HOME.
                            categories = preferences.categories(),
                            textMode = preferences.textMode,
                            themedIcons = preferences.themedIcons,
                            iconPackStatus = status,
                            isLoadingApps = false,
                        )
                    }
                    shortcutRepository.prefetch(_uiState.value.favoriteApps)
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiState.update { it.copy(isLoadingApps = false, appLoadFailed = true) }
                }
        }
    }

    /** Only reads Breezy's local provider; never initiates a network weather request. */
    fun refreshWeather(force: Boolean = true) {
        val state = _uiState.value
        if (state.isLoadingSettings || state.settingsLoadFailed || !state.settings.weatherEnabled) {
            weatherLoadJob?.cancel()
            lastWeatherRefresh = 0L
            _uiState.update { it.copy(weather = WeatherState()) }
            return
        }
        if (!force && (weatherLoadJob?.isActive == true ||
                SystemClock.elapsedRealtime() - lastWeatherRefresh < 15 * 60_000L)) return
        weatherLoadJob?.cancel()
        val locationId = state.settings.weatherLocationId
        weatherLoadJob = viewModelScope.launch {
            val previous = _uiState.value.weather
            _uiState.update { it.copy(weather = previous.copy(status = WeatherStatus.Loading,
                snapshot = previous.snapshot.takeIf { previous.selectedLocationId == locationId || locationId == null })) }
            val weather = weatherRepository.load(locationId)
            lastWeatherRefresh = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(weather = weather) }
        }
    }

    fun refreshSchedule() {
        if (_uiState.value.isLoadingSettings || _uiState.value.settingsLoadFailed ||
            !_uiState.value.settings.calendarAgenda
        ) {
            scheduleLoadJob?.cancel()
            _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Ready) }
            return
        }
        val application = getApplication<Application>()
        val hasPermission = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            scheduleLoadJob?.cancel()
            _uiState.update {
                it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.PermissionRequired)
            }
            return
        }

        scheduleLoadJob?.cancel()
        scheduleLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(scheduleStatus = ScheduleStatus.Loading) }
            runCatching { calendarRepository.upcomingEvents() }
                .onSuccess { events ->
                    _uiState.update { it.copy(events = events, scheduleStatus = ScheduleStatus.Ready) }
                }
                .onFailure {
                    if (it is CancellationException) throw it
                    _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Error) }
                }
        }
    }

    fun launch(app: LauncherApp, bounds: Rect? = null, options: Bundle? = null): Boolean =
        appRepository.launch(app, bounds, options)

    suspend fun loadShortcuts(app: LauncherApp) = shortcutRepository.shortcutsFor(app)

    fun cachedShortcuts(app: LauncherApp) = shortcutRepository.peek(app)

    fun prepareShortcuts(app: LauncherApp) {
        viewModelScope.launch { shortcutRepository.prefetch(listOf(app)) }
    }

    fun launchShortcut(shortcut: LauncherShortcut, bounds: Rect? = null, options: Bundle? = null) =
        shortcutRepository.launch(shortcut, bounds, options)

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
        getApplication<Application>().unregisterReceiver(dateReceiver)
        shortcutRepository.close()
        mediaRepository.close()
    }

    fun controlMedia(sessionId: String, command: MediaCommand): Boolean = mediaRepository.command(sessionId, command)
    fun dismissMedia(sessionId: String, revision: Long): Boolean = mediaRepository.dismiss(sessionId, revision)
    fun dismissNotification(key: String, revision: Long): Boolean = appNotifications.dismiss(key, revision)
    fun openNotification(key: String, revision: Long): Boolean = appNotifications.open(getApplication(), key, revision)

    fun renameApp(app: LauncherApp, label: String) {
        preferences.rename(app.key, label)
        val collator = Collator.getInstance()
        _uiState.update { state ->
            state.copy(apps = state.apps.map {
                if (it.key == app.key) it.copy(label = label.trim().ifBlank { it.originalLabel }) else it
            }.sortedWith { a, b -> collator.compare(a.label, b.label) })
        }
    }

    fun categorize(app: LauncherApp, category: String?) {
        preferences.categorize(app.key, category)
        _uiState.update { it.copy(categories = preferences.categories()) }
    }

    fun setTextMode(mode: WallpaperTextMode) {
        preferences.textMode(mode)
        _uiState.update { it.copy(textMode = mode) }
    }

    fun setThemedIcons(enabled: Boolean) {
        preferences.themedIcons(enabled)
        _uiState.update { it.copy(themedIcons = enabled) }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = persistSettings {
        settingsRepository.mutateSettings(transform)
    }

    suspend fun applyClockStyle(style: ClockStyle): Boolean = settingsWriteMutex.withLock {
        try {
            settingsRepository.mutateSettings { it.copy(clockStyle = style) }
            _uiState.update { it.copy(settingsSaveFailed = false) }
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("LauncherViewModel", "Unable to save clock style", error)
            _uiState.update { it.copy(settingsSaveFailed = true) }
            false
        }
    }

    fun setHiddenApps(keys: Set<String>) {
        val snapshot = keys.toSet()
        persistSettings { settingsRepository.setHiddenApps(snapshot) }
    }

    fun saveFolder(folder: LauncherFolder) {
        val snapshot = folder.copy(appKeys = folder.appKeys.toList())
        persistSettings { settingsRepository.saveFolder(snapshot) }
    }

    fun deleteFolder(id: String) = persistSettings {
        settingsRepository.deleteFolder(id)
    }

    /** Room emissions update the UI only after the operation has committed. */
    private fun persistSettings(write: suspend () -> Unit) {
        viewModelScope.launch {
            settingsWriteMutex.withLock {
                try {
                    write()
                    _uiState.update { it.copy(settingsSaveFailed = false) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e("LauncherViewModel", "Unable to save launcher settings", error)
                    _uiState.update { it.copy(settingsSaveFailed = true) }
                }
            }
        }
    }

    fun toggleFavorite(app: LauncherApp): Boolean {
        val updated = favoritesStore.toggle(app.key, _uiState.value.favoriteOrder)
        _uiState.update { it.copy(favoriteKeys = updated.toSet(), favoriteOrder = updated) }
        if (app.key in updated) prepareShortcuts(app)
        return app.key in updated
    }

    fun reorderFavorites(keys: List<String>) {
        val updated = favoritesStore.reorder(keys, _uiState.value.favoriteOrder)
        _uiState.update { it.copy(favoriteOrder = updated) }
    }

    fun requestReturnHome() {
        _returnHomeRequests.tryEmit(Unit)
    }
}
