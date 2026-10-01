@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.FolderPlacement
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherFolder
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import java.util.UUID

@Composable
internal fun ProductivitySettings(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onBack: () -> Unit,
    navigate: (SettingsPage) -> Unit,
) {
    val settings = uiState.settings
    var showMediaAccessDialog by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.settings_productivity), "settings_productivity", onBack) { padding ->
        SettingsList(padding) {
            item { SettingsHeading(stringResource(R.string.settings_instant_access)) }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_clock), clockAppSummary(uiState),
                    0, 4, "settings_clock",
                ) { navigate(SettingsPage.Clock) }
            }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_calendar_agenda), stringResource(R.string.settings_calendar_agenda_summary),
                    settings.calendarAgenda, 1, 4, "calendar_agenda",
                ) { value -> actions.updateSettings { current -> current.copy(calendarAgenda = value) } }
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_weather),
                    stringResource(if (settings.weatherEnabled) R.string.weather_settings_enabled_summary else R.string.weather_settings_disabled_summary),
                    2, 4, "settings_weather",
                ) { navigate(SettingsPage.Weather) }
            }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_media_player), stringResource(R.string.media_player_summary),
                    settings.mediaPlayer, 3, 4, "settings_media_player",
                ) { value ->
                    actions.updateSettings { it.copy(mediaPlayer = value) }
                    if (value && !uiState.media.hasAccess) showMediaAccessDialog = true
                }
            }
            if (settings.mediaPlayer && !uiState.media.hasAccess) item {
                Spacer(Modifier.height(12.dp))
                SettingsActionItem(
                    stringResource(R.string.media_allow_controls), stringResource(R.string.media_access_required),
                    0, 1, "media_access",
                ) { showMediaAccessDialog = true }
            }
            item { SettingsHeading(stringResource(R.string.settings_app_organization)) }
            if (!settings.mediaPlayer && !uiState.media.hasAccess) item {
                SettingsActionItem(stringResource(R.string.notification_allow), stringResource(R.string.media_access_required),
                    0, 1, "notifications_access") { showMediaAccessDialog = true }
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_hide_apps), pluralStringResource(R.plurals.settings_hidden_count, uiState.hiddenAppKeys.size, uiState.hiddenAppKeys.size),
                    0, 2, "settings_open_hidden_apps",
                ) { navigate(SettingsPage.HiddenApps) }
            }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_folders), pluralStringResource(R.plurals.settings_folder_count, uiState.folders.size, uiState.folders.size),
                    1, 2, "settings_open_folders",
                ) { navigate(SettingsPage.Folders) }
            }
            item { SettingsHeading(stringResource(R.string.settings_advanced)) }
            item {
                SettingsActionItem(stringResource(R.string.settings_add_widget), stringResource(R.string.settings_coming_soon), 0, 4, "settings_add_widget", enabled = false) { }
            }
            item {
                SettingsActionItem(stringResource(R.string.settings_move_widget), stringResource(R.string.settings_coming_soon), 1, 4, "settings_move_widget", enabled = false) { }
            }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_show_battery), stringResource(R.string.settings_show_battery_summary),
                    settings.showBatteryPercentage, 2, 4, "show_battery",
                ) { value -> actions.updateSettings { current -> current.copy(showBatteryPercentage = value) } }
            }
            item {
                SettingsToggleItem(
                    stringResource(R.string.settings_allow_haptics), stringResource(R.string.settings_allow_haptics_summary),
                    settings.allowHapticFeedback, 3, 4, "allow_haptics",
                ) { value -> actions.updateSettings { current -> current.copy(allowHapticFeedback = value) } }
            }
        }
    }
    if (showMediaAccessDialog) AlertDialog(
        onDismissRequest = { showMediaAccessDialog = false },
        title = { Text(stringResource(R.string.media_allow_controls)) },
        text = { Text(stringResource(R.string.media_access_explanation)) },
        confirmButton = {
            TextButton(onClick = { showMediaAccessDialog = false; actions.requestMediaAccess() }, modifier = Modifier.testTag("media_access_continue")) {
                Text(stringResource(R.string.media_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = { showMediaAccessDialog = false }, modifier = Modifier.testTag("media_access_cancel")) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
internal fun HiddenAppsSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    var selectedKeys by rememberSaveable { mutableStateOf<List<String>>(uiState.hiddenAppKeys.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = filterApps(uiState.apps, query)
    SettingsScaffold(
        stringResource(R.string.settings_hide_apps), "settings_hidden_apps", onBack,
        actions = {
            TextButton(onClick = { actions.setHiddenApps(selectedKeys.toSet()); onBack() }, enabled = LocalSettingsStorageState.current.canEdit, modifier = Modifier.testTag("hidden_apps_save")) {
                Text(stringResource(R.string.settings_save))
            }
        },
    ) { padding ->
        SettingsList(padding) {
            item {
                Text(stringResource(R.string.settings_hide_apps_description), Modifier.padding(horizontal = 4.dp, vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { AppSearchField(query) { query = it } }
            item { SettingsHeading(pluralStringResource(R.plurals.settings_select_count, selectedKeys.size, selectedKeys.size)) }
            appSelectionItems(filtered, selectedKeys.toSet(), "hidden_app") { key ->
                selectedKeys = toggledKeys(selectedKeys, key)
            }
        }
    }
}

@Composable
internal fun FolderSettings(uiState: LauncherUiState, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_folders), "settings_folders", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionItem(
                    stringResource(R.string.settings_folder_create), null, 0, 1, "folder_create",
                    leading = { LauncherIcon(LauncherSymbol.Plus) },
                ) { onEdit(null) }
            }
            if (uiState.folders.isEmpty()) {
                item { Text(stringResource(R.string.settings_folder_empty), Modifier.padding(horizontal = 4.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { SettingsHeading(stringResource(R.string.settings_folders)) }
                itemsIndexed(uiState.folders, key = { _, folder -> folder.id }) { index, folder ->
                    SettingsActionItem(
                        folder.name,
                        pluralStringResource(R.plurals.settings_folder_summary, folder.appKeys.size, folder.appKeys.size, folder.placement.label()),
                        index, uiState.folders.size, "folder:${folder.id}",
                        leading = { LauncherIcon(LauncherSymbol.Folder) },
                    ) { onEdit(folder.id) }
                }
            }
        }
    }
}

@Composable
internal fun FolderEditorSettings(folderId: String?, uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val existing = uiState.folders.firstOrNull { it.id == folderId }
    val id = rememberSaveable { folderId ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var selectedKeys by rememberSaveable { mutableStateOf<List<String>>(existing?.appKeys.orEmpty().toList()) }
    var placementName by rememberSaveable { mutableStateOf((existing?.placement ?: FolderPlacement.Favorites).name) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val filtered = filterApps(uiState.apps, query)
    val placement = FolderPlacement.valueOf(placementName)

    SettingsScaffold(
        stringResource(if (existing == null) R.string.settings_folder_create else R.string.settings_folder_edit),
        "settings_folder_editor", onBack,
        actions = {
            TextButton(
                onClick = {
                    actions.saveFolder(LauncherFolder(id, name.trim(), selectedKeys.toList(), placement))
                    onBack()
                },
                enabled = name.isNotBlank() && LocalSettingsStorageState.current.canEdit,
                modifier = Modifier.testTag("folder_save"),
            ) { Text(stringResource(R.string.settings_save)) }
        },
    ) { padding ->
        SettingsList(padding) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) },
                    enabled = LocalSettingsStorageState.current.canEdit,
                    label = { Text(stringResource(R.string.settings_folder_name)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("folder_name"),
                )
            }
            item { SettingsHeading(stringResource(R.string.settings_folder_placement)) }
            FolderPlacement.entries.forEachIndexed { index, option ->
                item(key = "placement:${option.name}") {
                    SegmentedListItem(
                        selected = placement == option,
                        enabled = LocalSettingsStorageState.current.canEdit,
                        onClick = { placementName = option.name },
                        shapes = ListItemDefaults.segmentedShapes(index, FolderPlacement.entries.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                        modifier = Modifier.testTag("folder_placement:${option.name}"),
                        content = { Text(option.label()) },
                        trailingContent = { RadioButton(selected = placement == option, onClick = null) },
                    )
                }
            }
            if (existing != null) item {
                TextButton(onClick = { confirmDelete = true }, enabled = LocalSettingsStorageState.current.canEdit, modifier = Modifier.testTag("folder_delete")) {
                    Text(stringResource(R.string.settings_folder_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            item { SettingsHeading(stringResource(R.string.settings_folder_members)) }
            item { AppSearchField(query) { query = it } }
            item {
                Text(pluralStringResource(R.plurals.settings_select_count, selectedKeys.size, selectedKeys.size), Modifier.padding(horizontal = 4.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            appSelectionItems(filtered, selectedKeys.toSet(), "folder_app") { key ->
                selectedKeys = toggledKeys(selectedKeys, key)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.settings_folder_delete)) },
        text = { Text(stringResource(R.string.settings_folder_delete_confirmation, existing?.name ?: name)) },
        confirmButton = {
            TextButton(onClick = { actions.deleteFolder(id); onBack() }, enabled = LocalSettingsStorageState.current.canEdit, modifier = Modifier.testTag("folder_confirm_delete")) {
                Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun AppSearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onChange,
        enabled = LocalSettingsStorageState.current.canEdit,
        modifier = Modifier.fillMaxWidth().testTag("settings_app_search"), singleLine = true,
        label = { Text(stringResource(R.string.settings_search_apps)) },
        leadingIcon = { LauncherIcon(LauncherSymbol.Search) },
    )
}

private fun LazyListScope.appSelectionItems(apps: List<LauncherApp>, selectedKeys: Set<String>, tagPrefix: String, onToggle: (String) -> Unit) {
    if (apps.isEmpty()) item {
        Text(stringResource(R.string.settings_no_apps), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
        val checked = app.key in selectedKeys
        SegmentedListItem(
            checked = checked, onCheckedChange = { onToggle(app.key) },
            enabled = LocalSettingsStorageState.current.canEdit,
            shapes = ListItemDefaults.segmentedShapes(index, apps.size),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
            modifier = Modifier.testTag("$tagPrefix:${app.key}"),
            leadingContent = { AppIcon(app, size = 36.dp) },
            content = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        )
    }
}

@Composable
private fun FolderPlacement.label(): String = stringResource(
    when (this) {
        FolderPlacement.Favorites -> R.string.settings_folder_favorites
        FolderPlacement.AppList -> R.string.settings_folder_app_list
    },
)

private fun filterApps(apps: List<LauncherApp>, query: String): List<LauncherApp> {
    val search = query.trim()
    return if (search.isEmpty()) apps else apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
}

private fun toggledKeys(keys: List<String>, key: String): List<String> =
    if (key in keys) keys.filterNot { it == key } else keys + key
