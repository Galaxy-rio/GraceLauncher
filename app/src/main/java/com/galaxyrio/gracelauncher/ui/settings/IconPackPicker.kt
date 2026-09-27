package com.galaxyrio.gracelauncher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.icons.IconPackInfo
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol

/** Standard radio dialog, not a segmented settings list inside a dialog. */
@Composable
internal fun IconPackPicker(
    uiState: LauncherUiState,
    onSelect: (String?) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = uiState.settings.iconPackPackage
    AlertDialog(
        modifier = Modifier.testTag("icon_pack_picker"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_icon_pack)) },
        text = {
            Column {
                Text(stringResource(R.string.icon_pack_description), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup().testTag("icon_pack_options")) {
                    item(key = "system") {
                        IconPackOption(null, stringResource(R.string.icon_pack_system), selected == null) { onSelect(null) }
                    }
                    items(uiState.iconPacks, key = IconPackInfo::packageName) { pack ->
                        IconPackOption(pack, pack.label, selected == pack.packageName) { onSelect(pack.packageName) }
                    }
                    if (selected != null && uiState.iconPacks.none { it.packageName == selected } && !uiState.isLoadingIconPacks) {
                        item(key = "unavailable") {
                            Text(stringResource(R.string.icon_pack_missing, selected),
                                Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!uiState.isLoadingIconPacks && uiState.iconPacks.isEmpty() && !uiState.iconPacksLoadFailed) {
                        item(key = "empty") {
                            Text(stringResource(R.string.icon_pack_empty), Modifier.padding(vertical = 12.dp).testTag("icon_pack_empty"))
                        }
                    }
                }
                if (uiState.isLoadingIconPacks) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                if (uiState.iconPacksLoadFailed) {
                    Text(stringResource(R.string.icon_pack_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.licenses_retry)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun IconPackOption(pack: IconPackInfo?, label: String, selected: Boolean, onClick: () -> Unit) {
    val enabled = LocalSettingsStorageState.current.canEdit
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("icon_pack:${pack?.packageName ?: "system"}")
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (pack?.icon != null) Image(pack.icon, null, Modifier.fillMaxSize())
            else LauncherIcon(LauncherSymbol.Apps)
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
