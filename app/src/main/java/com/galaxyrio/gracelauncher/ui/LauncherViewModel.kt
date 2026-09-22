package com.galaxyrio.gracelauncher.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxyrio.gracelauncher.data.AppRepository
import com.galaxyrio.gracelauncher.data.CalendarRepository
import com.galaxyrio.gracelauncher.data.FavoritesStore
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.ScheduleEvent
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
) {
    val favoriteApps: List<LauncherApp>
        get() = apps.filter { it.key in favoriteKeys }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(application)
    private val calendarRepository = CalendarRepository(application)
    private val favoritesStore = FavoritesStore(application)

    private val _uiState = MutableStateFlow(LauncherUiState())
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
            _uiState.update { it.copy(isLoadingApps = true, appLoadFailed = false) }
            runCatching { appRepository.loadApps() }
                .onSuccess { apps ->
                    _uiState.update {
                        it.copy(
                            apps = apps,
                            favoriteKeys = favoritesStore.favoritesFor(apps),
                            isLoadingApps = false,
                        )
                    }
                }
                .onFailure {
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
                    _uiState.update { it.copy(events = emptyList(), scheduleStatus = ScheduleStatus.Error) }
                }
        }
    }

    fun launch(app: LauncherApp): Boolean = appRepository.launch(app)

    fun toggleFavorite(app: LauncherApp): Boolean {
        val updated = favoritesStore.toggle(app.key, _uiState.value.favoriteKeys)
        _uiState.update { it.copy(favoriteKeys = updated) }
        return app.key in updated
    }

    fun requestReturnHome() {
        _returnHomeRequests.tryEmit(Unit)
    }
}
