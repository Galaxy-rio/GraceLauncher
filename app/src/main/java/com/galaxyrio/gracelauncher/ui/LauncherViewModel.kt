package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.CalendarRepository
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherPreferences
import com.galaxyrio.gracelauncher.data.LauncherShortcut
import com.galaxyrio.gracelauncher.data.ScheduleEvent
import com.galaxyrio.gracelauncher.data.ShortcutRepository
import com.galaxyrio.gracelauncher.data.WallpaperTextMode
import java.text.Collator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    val favoriteApps: List<LauncherApp>
        get() = apps.filter { it.key in favoriteKeys }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(application)
    private val calendarRepository = CalendarRepository(application)
    private val favoritesStore = FavoritesStore(application)
    private val preferences = LauncherPreferences(application)
    private val shortcutRepository = ShortcutRepository(application)

    private val _uiState = MutableStateFlow(LauncherUiState(
        categories = preferences.categories(),
        textMode = preferences.textMode,
        themedIcons = preferences.themedIcons,
    ))
    val uiState = _uiState.asStateFlow()

    private val _returnHomeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnHomeRequests = _returnHomeRequests.asSharedFlow()

    private var appLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null

    init {
        refreshApps()
        refreshSchedule()
    }

    fun refreshApps() {
        appLoadJob?.cancel()
        appLoadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingApps = true, appLoadFailed = false, hasShortcutAccess = shortcutRepository.hasAccess())
            }
            runCatching { appRepository.loadApps() }
                .onSuccess { apps ->
                    val renames = preferences.renames()
                    val collator = Collator.getInstance()
                    val displayedApps = apps.map { it.copy(label = renames[it.key] ?: it.originalLabel) }
                        .sortedWith { a, b -> collator.compare(a.label, b.label) }
                    _uiState.update {
                        it.copy(
                            apps = displayedApps,
                            favoriteKeys = favoritesStore.favoritesFor(apps),
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

    fun refreshSchedule() {
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
        shortcutRepository.close()
    }

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

    fun toggleFavorite(app: LauncherApp): Boolean {
        val updated = favoritesStore.toggle(app.key, _uiState.value.favoriteKeys)
        _uiState.update { it.copy(favoriteKeys = updated) }
        if (app.key in updated) prepareShortcuts(app)
        return app.key in updated
    }

    fun requestReturnHome() {
        _returnHomeRequests.tryEmit(Unit)
    }
}
