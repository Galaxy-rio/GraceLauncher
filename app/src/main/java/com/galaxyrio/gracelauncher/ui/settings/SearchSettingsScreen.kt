package com.galaxyrio.gracelauncher.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.SearchContacts
import com.galaxyrio.gracelauncher.data.SearchSettings
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState

@Composable
internal fun SearchSettingsScreen(uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val settings = uiState.settings.search
    val context = LocalContext.current
    val contacts = remember(context) { SearchContacts(context) }
    var hasAccess by remember { mutableStateOf(contacts.hasAccess()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, contacts) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hasAccess = contacts.hasAccess() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun update(change: (SearchSettings) -> SearchSettings) = actions.updateSettings { it.copy(search = change(it.search)) }
    fun permissionResult(granted: Boolean) {
        hasAccess = granted
        update { it.copy(contacts = granted) }
        if (!granted) Toast.makeText(context, R.string.search_contacts_permission, Toast.LENGTH_LONG).show()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), ::permissionResult)
    val systemSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionResult(contacts.hasAccess())
    }
    SettingsScaffold(stringResource(R.string.settings_search), "settings_search", onBack) { padding ->
        SettingsList(padding) {
            item {
                Spacer(Modifier.height(24.dp))
                Surface(onClick = { update { it.copy(enabled = !it.enabled) } },
                    modifier = Modifier.fillMaxWidth().testTag("search_enabled"), shape = CircleShape,
                    color = if (settings.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
                    contentColor = if (settings.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
                    Row(Modifier.heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.search_enable), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(16.dp))
                        Switch(settings.enabled, null)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                SettingsToggleItem(stringResource(R.string.search_suggestions), stringResource(R.string.search_suggestions_summary),
                    settings.suggestions, 0, 6, "search_suggestions", enabled = settings.enabled) { value -> update { it.copy(suggestions = value) } }
            }
            item {
                SettingsToggleItem(stringResource(R.string.search_contacts), stringResource(R.string.search_contacts_summary),
                    settings.contacts && hasAccess, 1, 6, "search_contacts", enabled = settings.enabled) { value ->
                    if (!value || contacts.hasAccess()) update { it.copy(contacts = value) }
                    else if (permissionRequested && (context as? Activity)?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS)
                    } == false) {
                        systemSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    } else {
                        permissionRequested = true
                        permission.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }
            item {
                SettingsActionItem(stringResource(R.string.search_files), stringResource(R.string.settings_coming_soon),
                    2, 6, "search_files", enabled = false, onClick = {})
            }
            item {
                SettingsToggleItem(stringResource(R.string.search_fuzzy), stringResource(R.string.search_fuzzy_summary),
                    settings.fuzzy, 3, 6, "search_fuzzy", enabled = settings.enabled) { value -> update { it.copy(fuzzy = value) } }
            }
            item {
                SettingsToggleItem(stringResource(R.string.search_internet_setting), stringResource(R.string.search_internet_summary),
                    settings.internet, 4, 6, "search_internet", enabled = settings.enabled) { value -> update { it.copy(internet = value) } }
            }
            item {
                SettingsToggleItem(stringResource(R.string.search_hidden_apps), stringResource(R.string.search_hidden_apps_summary),
                    settings.hiddenApps, 5, 6, "search_hidden_apps", enabled = settings.enabled) { value -> update { it.copy(hiddenApps = value) } }
            }
        }
    }
}
