package com.galaxyrio.gracelauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.settings.AdvancedSettings
import com.galaxyrio.gracelauncher.ui.settings.BackupSettings
import com.galaxyrio.gracelauncher.ui.settings.SettingsPage
import com.galaxyrio.gracelauncher.ui.theme.GraceLauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun advancedPageOpensBackupWithExportAndRestoreActions() {
        var page by mutableStateOf(SettingsPage.Advanced)
        compose.setContent {
            GraceLauncherTheme(dynamicColor = false) {
                if (page == SettingsPage.Backup) {
                    BackupSettings(LauncherActions(), onBack = { page = SettingsPage.Advanced })
                } else {
                    AdvancedSettings(onBack = {}, navigate = { page = it }, onChooseDefaultHome = {})
                }
            }
        }
        compose.onNodeWithTag("settings_open_backup").performClick()
        compose.onNodeWithTag("backup_export").assertIsDisplayed()
        compose.onNodeWithTag("backup_restore").assertIsDisplayed()
    }
}
