@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import java.util.UUID

internal enum class SettingsPage {
    Root, Productivity, Themes, Advanced, About, HiddenApps, Folders, FolderEditor,
    Changelog, Licenses, FontLicenses, IconLicenses, FrameworkLicenses,
}

/** A full-screen settings flow with a small saveable, local navigation stack. */
@Composable
fun LauncherSettingsScreen(
    uiState: LauncherUiState,
    actions: LauncherActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: String? = null,
    initialFolderId: String? = null,
) {
    var pageName by rememberSaveable {
        mutableStateOf(when {
            initialFolderId != null -> SettingsPage.FolderEditor.name
            initialPage == "folders" -> SettingsPage.Folders.name
            else -> SettingsPage.Root.name
        })
    }
    var history by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var editingFolderId by rememberSaveable { mutableStateOf(initialFolderId) }
    val navigate: (SettingsPage) -> Unit = { destination ->
        history = history + pageName
        pageName = destination.name
    }
    val back: () -> Unit = {
        if (history.isEmpty()) onBack() else {
            pageName = history.last()
            history = history.dropLast(1)
        }
    }
    BackHandler(onBack = back)

    val storage = SettingsStorageState(
        uiState.isLoadingSettings, uiState.settingsLoadFailed, uiState.settingsSaveFailed,
    )
    CompositionLocalProvider(LocalSettingsStorageState provides storage) {
      Box(modifier) {
        // Do not initialize editable drafts from default values before Room's
        // first complete snapshot, especially when restoring a direct folder link.
        if (!storage.canEdit) {
            SettingsScaffold(stringResource(R.string.settings_title), "settings_storage_blocked", back) { }
        } else when (SettingsPage.valueOf(pageName)) {
            SettingsPage.Root -> SettingsHome(back, navigate)
            SettingsPage.Productivity -> ProductivitySettings(uiState, actions, back, navigate)
            SettingsPage.Themes -> ThemeSettings(uiState, actions, back)
            // Intentionally left blank until advanced options have real implementations.
            SettingsPage.Advanced -> SettingsScaffold(stringResource(R.string.settings_advanced), "settings_advanced", back) { }
            SettingsPage.About -> AboutSettings(back, navigate)
            SettingsPage.HiddenApps -> HiddenAppsSettings(uiState, actions, back)
            SettingsPage.Folders -> FolderSettings(uiState, back) { folderId ->
                editingFolderId = folderId ?: UUID.randomUUID().toString()
                navigate(SettingsPage.FolderEditor)
            }
            SettingsPage.FolderEditor -> key(editingFolderId) {
                FolderEditorSettings(editingFolderId, uiState, actions, back)
            }
            SettingsPage.Changelog -> ChangelogSettings(back)
            SettingsPage.Licenses -> LicenseSettings(back, navigate)
            SettingsPage.FontLicenses -> LicenseTextSettings(fonts = true, onBack = back)
            SettingsPage.IconLicenses -> LicenseTextSettings(fonts = false, onBack = back)
            SettingsPage.FrameworkLicenses -> LicenseTextSettings(fonts = false, onBack = back, titleResource = R.string.settings_framework_licenses, frameworks = true)
        }
      }
    }
}

@Composable
private fun SettingsHome(onBack: () -> Unit, navigate: (SettingsPage) -> Unit) {
    val categories = listOf(
        SettingsPage.Productivity to LauncherSymbol.Star,
        SettingsPage.Themes to LauncherSymbol.Palette,
        SettingsPage.Advanced to LauncherSymbol.Settings,
        SettingsPage.About to LauncherSymbol.Info,
    )
    SettingsScaffold(stringResource(R.string.settings_title), "settings_root", onBack) { padding ->
        SettingsList(padding) {
            item { Spacer(Modifier.height(24.dp)) }
            categories.forEachIndexed { index, (page, symbol) ->
                item(key = page.name) {
                    val title = when (page) {
                        SettingsPage.Productivity -> R.string.settings_productivity
                        SettingsPage.Themes -> R.string.settings_themes
                        SettingsPage.Advanced -> R.string.settings_advanced
                        else -> R.string.settings_about
                    }
                    val summary = when (page) {
                        SettingsPage.Productivity -> R.string.settings_productivity_summary
                        SettingsPage.Themes -> R.string.settings_themes_summary
                        SettingsPage.Advanced -> R.string.settings_advanced_summary
                        else -> R.string.settings_about_summary
                    }
                    SettingsActionItem(
                        stringResource(title), stringResource(summary), index, categories.size,
                        tag = "settings_category_${page.name.lowercase()}",
                        leading = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                LauncherIcon(symbol, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                    ) { navigate(page) }
                }
            }
        }
    }
}
