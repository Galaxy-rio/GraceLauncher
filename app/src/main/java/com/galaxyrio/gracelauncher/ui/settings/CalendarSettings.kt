package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.ScheduleStatus

@Composable
internal fun CalendarSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.widget_calendar), "settings_calendar", onBack) { padding ->
        SettingsList(padding) {
            item {
                SettingsToggleItem(stringResource(R.string.settings_calendar_agenda), stringResource(R.string.settings_calendar_agenda_summary),
                    uiState.settings.calendarAgenda, 0, 1, "calendar_agenda") { enabled ->
                    actions.updateSettings { it.copy(calendarAgenda = enabled) }
                }
            }
            if (uiState.scheduleStatus == ScheduleStatus.PermissionRequired) item {
                SettingsHeading(stringResource(R.string.widget_calendar_access))
                SettingsActionItem(stringResource(R.string.connect_calendar), stringResource(R.string.calendar_permission_description),
                    0, 1, "calendar_access", onClick = actions.requestCalendarAccess)
            }
        }
    }
}
