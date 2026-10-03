@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSearchBar

@Composable
internal fun clockAppSummary(uiState: LauncherUiState): String =
    if (uiState.settings.clockAppKey == null) stringResource(R.string.clock_default_app)
    else uiState.apps.firstOrNull { it.key == uiState.settings.clockAppKey }?.label
        ?: stringResource(R.string.clock_selected_unavailable)

/** Choosing a target persists a preference; it never launches the selected app. */
@Composable
internal fun ClockSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    AppSelectionSettings(
        uiState, actions, onBack, title = stringResource(R.string.settings_clock), tag = "clock",
        selectedKey = uiState.settings.clockAppKey, showDefault = true,
        description = stringResource(R.string.clock_choose_app_description),
        onSelect = { key -> actions.updateSettings { it.copy(clockAppKey = key) } },
    )
}

/** Shared searchable app page for clock targets and the Icon designer preview. */
@Composable
internal fun AppSelectionSettings(
    uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    title: String, tag: String, selectedKey: String?, onSelect: (String?) -> Unit,
    showDefault: Boolean = false, description: String? = null,
) {
    val queryState = rememberTextFieldState()
    val query = queryState.text.toString()
    val ownPackage = LocalContext.current.packageName
    // This preference stores an activity component, not a deep-shortcut identity.
    val available = remember(uiState.apps, ownPackage) { uiState.apps.filter { it.packageName != ownPackage && it.shortcut == null } }
    val filtered = remember(available, query) {
        val search = query.trim()
        available.filter { it.label.contains(search, true) || it.originalLabel.contains(search, true) || it.packageName.contains(search, true) }
    }
    val enabled = LocalSettingsStorageState.current.canEdit
    SettingsScaffold(title, "settings_${tag}_page", onBack) { padding ->
        SettingsList(padding) {
            if (description != null) item {
                Text(description,
                    Modifier.padding(horizontal = 4.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showDefault) item {
                SegmentedListItem(
                    selected = selectedKey == null,
                    onClick = { onSelect(null) },
                    enabled = enabled,
                    modifier = Modifier.testTag("${tag}_default_app"),
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    content = { Text(stringResource(R.string.clock_default_app)) },
                    trailingContent = { RadioButton(selected = selectedKey == null, onClick = null) },
                )
            }
            if (selectedKey != null && !uiState.isLoadingApps && !uiState.appLoadFailed && available.none { it.key == selectedKey }) item {
                Text(stringResource(R.string.clock_selected_unavailable), Modifier.padding(16.dp).testTag("${tag}_missing_app"),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Spacer(Modifier.height(20.dp))
                LauncherSearchBar(queryState, stringResource(R.string.settings_search_apps), "${tag}_app_query",
                    enabled = enabled && !uiState.isLoadingApps && !uiState.appLoadFailed)
                Spacer(Modifier.height(12.dp))
            }
            when {
                uiState.appLoadFailed -> item {
                    Text(stringResource(R.string.clock_apps_load_failed), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = actions.refreshApps) { Text(stringResource(R.string.retry)) }
                }
                uiState.isLoadingApps -> item {
                    Text(stringResource(R.string.clock_apps_loading), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filtered.isEmpty() -> item {
                    Text(stringResource(R.string.search_no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> itemsIndexed(filtered, key = { _, app -> app.key }) { index, app ->
                    SegmentedListItem(
                        selected = app.key == selectedKey,
                        onClick = { onSelect(app.key) },
                        enabled = enabled,
                        modifier = Modifier.testTag("${tag}_app:${app.key}"),
                        shapes = ListItemDefaults.segmentedShapes(index, filtered.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                        leadingContent = { AppIcon(app, size = 36.dp) },
                        content = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { RadioButton(selected = app.key == selectedKey, onClick = null) },
                    )
                }
            }
        }
    }
}
