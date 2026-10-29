@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.*
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import com.galaxyrio.gracelauncher.ui.overlays.CategoryItemsEditor
import com.galaxyrio.gracelauncher.ui.overlays.TextEntryDialog
import java.util.Calendar
import java.util.UUID

@Composable
internal fun categoryLabel(category: AppCategory): String = category.name ?: stringResource(when (category.id) {
    FavoritesCategoryId -> R.string.settings_folder_favorites
    MusicCategoryId -> R.string.app_category_music
    else -> R.string.app_category_name
})

@Composable
private fun CategoryCondition.label() = stringResource(when (this) {
    CategoryCondition.Always -> R.string.app_category_always
    CategoryCondition.Headphones -> R.string.app_category_headphones
    CategoryCondition.Time -> R.string.app_category_time
})

@Composable
internal fun AppCategoriesSettings(
    uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit,
    selectedIds: Set<String>? = null, onToggle: (String) -> Unit = {}, onEdit: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var openingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openingId, uiState.appCategories, uiState.settingsSaveFailed) {
        val id = openingId ?: return@LaunchedEffect
        if (uiState.appCategories.any { it.id == id }) { openingId = null; onEdit(id) }
        else if (uiState.settingsSaveFailed) openingId = null
    }
    SettingsScaffold(stringResource(R.string.app_categories_title), "app_categories", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                SettingsActionItem(stringResource(R.string.app_category_create), null, 0, 1, "category_create",
                    enabled = openingId == null, leading = { LauncherIcon(LauncherSymbol.Plus) }) { creating = true }
            }
            item { SettingsHeading(stringResource(R.string.app_categories_title)) }
            itemsIndexed(uiState.appCategories, key = { _, category -> category.id }) { index, category ->
                val checked = selectedIds?.contains(category.id)
                SettingsActionItem(categoryLabel(category),
                    pluralStringResource(R.plurals.app_category_item_count, category.itemKeys.size, category.itemKeys.size) + " · " + category.condition.label(),
                    index, uiState.appCategories.size, "category:${category.id}",
                    modifier = Modifier.semantics {
                        if (checked != null) {
                            role = Role.Checkbox
                            toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                        }
                    }, leading = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (checked != null) Checkbox(checked, null, enabled = LocalSettingsStorageState.current.canEdit)
                            LauncherIcon(LauncherSymbol.Category)
                        }
                    }) { if (selectedIds == null) onEdit(category.id) else onToggle(category.id) }
            }
        }
    }
    if (creating) TextEntryDialog(stringResource(R.string.app_category_create), "", { creating = false }, { name ->
        val id = UUID.randomUUID().toString()
        actions.saveAppCategory(AppCategory(id, name, showOnHome = selectedIds != null))
        creating = false
        openingId = id
    })
}

@Composable
internal fun AppCategoryEditorSettings(categoryId: String?, uiState: LauncherUiState, actions: LauncherActions, onBack: () -> Unit) {
    val category = uiState.appCategories.firstOrNull { it.id == categoryId }
    if (category == null) {
        SettingsScaffold(stringResource(R.string.app_categories_title), "category_unavailable", onBack) { padding ->
            Text(stringResource(R.string.app_category_unavailable), Modifier.padding(padding).padding(24.dp))
        }
        return
    }
    val name = categoryLabel(category)
    var dialog by rememberSaveable(category.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    fun formatTime(minute: Int): String = DateFormat.getTimeFormat(context).format(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60)
    }.time)
    CategoryItemsEditor(category, uiState, actions, onBack) {
        val count = if (category.condition == CategoryCondition.Time) 4 else 2
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SettingsActionItem(name, stringResource(R.string.app_category_name), 0, count, "category_name",
                leading = { LauncherIcon(LauncherSymbol.Category) }) { dialog = "name" }
            SettingsActionItem(stringResource(R.string.app_category_condition), category.condition.label(), 1, count,
                "category_condition") { dialog = "condition" }
            if (category.condition == CategoryCondition.Time) {
                SettingsActionItem(stringResource(R.string.app_category_start), formatTime(category.startMinute), 2, count,
                    "category_start") { dialog = "start" }
                SettingsActionItem(stringResource(R.string.app_category_end), formatTime(category.endMinute), 3, count,
                    "category_end") { dialog = "end" }
            }
            if (category.id != FavoritesCategoryId) TextButton(onClick = { dialog = "delete" },
                enabled = LocalSettingsStorageState.current.canEdit, modifier = Modifier.testTag("category_delete")) {
                Text(stringResource(R.string.app_category_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    when (dialog) {
        "name" -> TextEntryDialog(stringResource(R.string.app_category_name), name, { dialog = null }, { value ->
            actions.updateAppCategory(category.id) { it.copy(name = value) }; dialog = null
        })
        "condition" -> SettingsSelectionDialog(stringResource(R.string.app_category_condition), CategoryCondition.entries,
            category.condition, "category_condition", { it.name }, { it.label() }, { value ->
                actions.updateAppCategory(category.id) { it.copy(condition = value) }; dialog = null
            }, { dialog = null })
        "start", "end" -> {
            val start = dialog == "start"
            val value = if (start) category.startMinute else category.endMinute
            val picker = rememberTimePickerState(value / 60, value % 60, DateFormat.is24HourFormat(context))
            AlertDialog(onDismissRequest = { dialog = null }, title = {
                Text(stringResource(if (start) R.string.app_category_start else R.string.app_category_end))
            }, text = { TimePicker(picker) }, confirmButton = {
                TextButton(onClick = {
                    val minute = picker.hour * 60 + picker.minute
                    actions.updateAppCategory(category.id) { if (start) it.copy(startMinute = minute) else it.copy(endMinute = minute) }
                    dialog = null
                }) { Text(stringResource(R.string.done)) }
            }, dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } })
        }
        "delete" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.app_category_delete)) },
            text = { Text(stringResource(R.string.app_category_delete_confirmation, name)) }, confirmButton = {
                TextButton(onClick = { actions.deleteAppCategory(category.id); dialog = null; onBack() }) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            }, dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.settings_cancel)) } })
    }
}
