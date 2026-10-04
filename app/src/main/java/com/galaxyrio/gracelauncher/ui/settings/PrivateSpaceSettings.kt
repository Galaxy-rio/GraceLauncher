@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.PrivateSpaceDisplay
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.overlays.PrivateSpaceEditorScreen
import com.galaxyrio.gracelauncher.ui.overlays.TextEntryDialog
import kotlinx.coroutines.launch

@Composable
internal fun PrivateSpaceSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit, onEdit: () -> Unit) {
    val settings = uiState.settings.privateSpace
    var displayDialog by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(stringResource(R.string.private_space_title), "settings_private_space", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            if (!uiState.privateSpace.supported) item {
                Text(stringResource(R.string.private_space_unsupported), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uiState.privateSpace.supported && uiState.isDefaultHome == false) item {
                SettingsActionItem(stringResource(R.string.set_default_launcher), stringResource(R.string.private_space_default_home),
                    0, 1, "private_space_default_home", leading = { LauncherIcon(LauncherSymbol.Home) }, onClick = actions.requestDefaultHome)
                Spacer(Modifier.height(12.dp))
            }
            item {
                SettingsToggleItem(stringResource(R.string.private_space_enable), stringResource(R.string.private_space_enable_summary),
                    settings.enabled, 0, 2, "private_space_enabled", enabled = uiState.privateSpace.supported) { enabled ->
                    actions.updateSettings { it.copy(privateSpace = it.privateSpace.copy(enabled = enabled)) }
                }
            }
            item {
                SettingsToggleItem(stringResource(R.string.private_space_password), stringResource(R.string.private_space_password_summary),
                    settings.passwordProtected, 1, 2, "private_space_password", enabled = settings.enabled && uiState.privateSpace.supported) { enabled ->
                    actions.updateSettings { it.copy(privateSpace = it.privateSpace.copy(passwordProtected = enabled)) }
                }
            }
            item { SettingsHeading(stringResource(R.string.private_space_display)) }
            item {
                SettingsActionItem(stringResource(R.string.private_space_display), settings.display.label(), 0, 2, "private_space_display",
                    enabled = settings.enabled && uiState.privateSpace.supported) { displayDialog = true }
            }
            item {
                SettingsActionItem(stringResource(R.string.private_space_edit), stringResource(R.string.private_space_edit_summary),
                    1, 2, "private_space_edit", enabled = settings.enabled && uiState.privateSpace.user != null && !uiState.privateSpace.authenticating) {
                    actions.requestPrivateSpace(true, onEdit)
                }
            }
            if (uiState.privateSpace.supported && (uiState.privateSpace.user == null || uiState.privateApps.isEmpty())) item {
                Spacer(Modifier.height(16.dp))
                SettingsActionItem(stringResource(R.string.private_space_setup), stringResource(R.string.private_space_setup_summary),
                    0, 1, "private_space_system_settings", onClick = actions.openPrivateSpaceSettings)
            }
        }
    }
    if (displayDialog) AlertDialog(
        onDismissRequest = { displayDialog = false }, shape = RoundedCornerShape(40.dp),
        title = { Text(stringResource(R.string.private_space_display)) },
        text = {
            Column {
                PrivateSpaceDisplay.entries.forEach { display ->
                    Row(Modifier.fillMaxWidth().selectable(display == settings.display, role = Role.RadioButton, onClick = {
                        actions.updateSettings { it.copy(privateSpace = it.privateSpace.copy(display = display)) }
                        displayDialog = false
                    }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(display == settings.display, null)
                        Spacer(Modifier.width(12.dp))
                        Text(display.label())
                    }
                }
            }
        }, confirmButton = {},
    )
}

@Composable
internal fun PrivateSpaceEditorSettings(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    onEditIcon: (LauncherApp) -> Unit) {
    if (!uiState.privateSpace.accessible || uiState.privateSpace.locked || !uiState.settings.privateSpace.enabled) {
        SettingsScaffold(stringResource(R.string.private_space_edit), "private_space_editor_locked", onBack) { padding ->
            SettingsList(padding) {
                item { Spacer(Modifier.height(24.dp)) }
                item {
                    SettingsActionItem(stringResource(R.string.private_space_unlock_edit), null, 0, 1, "private_space_editor_unlock",
                        enabled = uiState.settings.privateSpace.enabled && uiState.privateSpace.user != null && !uiState.privateSpace.authenticating,
                        leading = { LauncherIcon(LauncherSymbol.Lock) }) { actions.requestPrivateSpace(true) {} }
                }
            }
        }
        return
    }
    val folder = uiState.privateFolder
    val app = uiState.folderItem(folder)
    var rename by rememberSaveable { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    PrivateSpaceEditorScreen(app, uiState, actions, onBack) {
        Column(Modifier.padding(top = 16.dp)) {
            SettingsActionItem(folder.name, stringResource(R.string.settings_folder_name), 0, 1, "private_space_name",
                leading = { AppIcon(app, Modifier.clickable(onClickLabel = stringResource(R.string.icon_designer_title)) { onEditIcon(app) }, size = 36.dp) },
                trailing = {
                    TextButton(enabled = !resetting, onClick = {
                        resetting = true
                        scope.launch {
                            if (!actions.resetPrivateSpaceAppearance()) Toast.makeText(context, R.string.settings_storage_save_error, Toast.LENGTH_SHORT).show()
                            resetting = false
                        }
                    }, modifier = Modifier.testTag("private_space_reset")) { Text(stringResource(R.string.reset)) }
                }) { rename = true }
            if (uiState.privateAppsFailed) TextButton(onClick = actions.refreshApps) { Text(stringResource(R.string.retry)) }
        }
    }
    if (rename) TextEntryDialog(stringResource(R.string.rename_folder), folder.name, { rename = false }, {
        actions.rename(app, it)
        rename = false
    })
}

@Composable
private fun PrivateSpaceDisplay.label(): String = stringResource(
    if (this == PrivateSpaceDisplay.List) R.string.private_space_display_list else R.string.private_space_display_folder,
)
